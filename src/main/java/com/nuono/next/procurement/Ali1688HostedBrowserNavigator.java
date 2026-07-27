package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class Ali1688HostedBrowserNavigator {

    private static final String CHAT_URL_TEMPLATE = "https://air.1688.com/app/ocms-fusion-components-1688/"
            + "def_cbu_web_im/index.html?offerId=%s#/";
    private static final String UNTRUSTED_REDIRECT_PAYLOAD = "{\"ok\":false,"
            + "\"failureCode\":\"UNTRUSTED_BROWSER_REDIRECT\",\"failureMessage\":\"1688 浏览器标签已跳转到其他地址。\"}";

    private final ChromeAppleScriptClient chromeClient;
    private final ChromeProbeResultParser probeResultParser;
    private final Ali1688BrowserUrlPolicy urlPolicy;

    Ali1688HostedBrowserNavigator(
            ChromeAppleScriptClient chromeClient,
            ChromeProbeResultParser probeResultParser,
            Ali1688BrowserUrlPolicy urlPolicy
    ) {
        this.chromeClient = chromeClient;
        this.probeResultParser = probeResultParser;
        this.urlPolicy = urlPolicy;
    }

    ChatTabSelection resolveReadyChatTab(AutoInquiryTaskView task) {
        String offerId = normalize(task == null ? null : task.getTargetOfferId());
        String supplierIdentity = normalize(task == null ? null : task.getTargetSupplierIdentity());
        if (!StringUtils.hasText(offerId)) {
            return ChatTabSelection.failure("MISSING_OFFER_ID", "当前任务还没有有效 offerId。");
        }
        ChromeTab chatTab = resolveChatTab(offerId);
        if (chatTab == null) {
            return ChatTabSelection.failure("CHAT_TAB_NOT_FOUND", "还没有找到可用的 1688 聊天页。");
        }
        if (urlPolicy.acceptsLoginUrl(chatTab.url)) {
            return ChatTabSelection.failure("LOGIN_REQUIRED", "本机 Chrome 的 1688 托管会话当前未登录。");
        }

        chromeClient.focusTab(chatTab);
        ContactSelectionResult selection = ensureContactSelected(chatTab, supplierIdentity, offerId);
        if (selection.ok) {
            return ChatTabSelection.success(chatTab, selection);
        }

        ChromeTab bootstrappedTab = bootstrapChatTabFromEntry(task, offerId);
        if (bootstrappedTab != null) {
            chromeClient.focusTab(bootstrappedTab);
            ContactSelectionResult retriedSelection =
                    ensureContactSelected(bootstrappedTab, supplierIdentity, offerId);
            if (retriedSelection.ok) {
                return ChatTabSelection.success(bootstrappedTab, retriedSelection);
            }
            selection = retriedSelection;
        }

        return ChatTabSelection.failure(
                firstNonBlank(selection.failureCode, "SUPPLIER_THREAD_NOT_FOUND"),
                firstNonBlank(selection.failureMessage, "当前聊天页还没有命中正确联系人。")
        );
    }

    private ChromeTab resolveChatTab(String offerId) {
        ChromeTab existingChatTab = findMatchingChatTab(offerId);
        if (existingChatTab != null) {
            return existingChatTab;
        }

        chromeClient.openTab(urlPolicy.validateRequestedUrl(
                String.format(Locale.ROOT, CHAT_URL_TEMPLATE, offerId),
                Ali1688BrowserUrlPolicy.PageKind.CHAT
        ));
        chromeClient.sleep(2500L);

        ChromeTab chatTab = findMatchingChatTab(offerId);
        if (chatTab != null) {
            return chatTab;
        }
        ChromeTab offerRelatedTab = findLatestOfferRelatedTab(offerId);
        return offerRelatedTab == null ? findLatestLoginTab() : offerRelatedTab;
    }

    private ChromeTab bootstrapChatTabFromEntry(AutoInquiryTaskView task, String offerId) {
        String entryUrl = normalize(task == null ? null : task.getTargetEntryUrl());
        if (!StringUtils.hasText(entryUrl)) {
            return null;
        }
        try {
            entryUrl = urlPolicy.validateRequestedUrl(
                    entryUrl,
                    Ali1688BrowserUrlPolicy.PageKind.OFFER_DETAIL
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (!urlPolicy.matchesOfferId(
                entryUrl,
                Ali1688BrowserUrlPolicy.PageKind.OFFER_DETAIL,
                offerId
        )) {
            return null;
        }

        ChromeTab entryTab = findDetailTab(offerId);
        if (entryTab == null) {
            chromeClient.openTab(entryUrl);
            chromeClient.sleep(3200L);
            entryTab = findDetailTab(offerId);
        }
        if (entryTab == null) {
            return null;
        }

        chromeClient.focusTab(entryTab);
        ServiceEntryResult openChatResult = openCustomerService(entryTab, offerId);
        if (!openChatResult.ok) {
            return null;
        }
        chromeClient.sleep(2600L);
        return findMatchingChatTab(offerId);
    }

    private ChromeTab findMatchingChatTab(String offerId) {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> tab != null && urlPolicy.matchesOfferId(
                        tab.url,
                        Ali1688BrowserUrlPolicy.PageKind.CHAT,
                        offerId
                ))
                .max(Comparator
                        .comparingInt((ChromeTab tab) -> chatTabSpecificity(tab, offerId))
                        .thenComparingInt(tab -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private ChromeTab findDetailTab(String offerId) {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> tab != null && urlPolicy.matchesOfferId(
                        tab.url,
                        Ali1688BrowserUrlPolicy.PageKind.OFFER_DETAIL,
                        offerId
                ))
                .max(Comparator.comparingInt((ChromeTab tab) -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private ChromeTab findLatestOfferRelatedTab(String offerId) {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> tab != null
                        && (urlPolicy.matchesOfferId(
                                tab.url,
                                Ali1688BrowserUrlPolicy.PageKind.CHAT,
                                offerId
                        ) || urlPolicy.matchesOfferId(
                                tab.url,
                                Ali1688BrowserUrlPolicy.PageKind.OFFER_DETAIL,
                                offerId
                        )))
                .max(Comparator.comparingInt((ChromeTab tab) -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private ChromeTab findLatestLoginTab() {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> tab != null && urlPolicy.acceptsLoginUrl(tab.url))
                .max(Comparator.comparingInt((ChromeTab tab) -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private ContactSelectionResult ensureContactSelected(
            ChromeTab tab,
            String supplierIdentity,
            String offerId
    ) {
        if (!StringUtils.hasText(supplierIdentity)) {
            return ContactSelectionResult.success(true, null, null, null);
        }
        String supplierBase64 = Base64.getEncoder().encodeToString(
                supplierIdentity.getBytes(StandardCharsets.UTF_8)
        );
        String javascript = loadJavascriptResource("ensure-contact.js")
                .replace("__SUPPLIER_BASE64__", supplierBase64);
        return probeResultParser.parseContactSelectionResult(executeTrusted(
                tab,
                Ali1688BrowserUrlPolicy.PageKind.CHAT,
                offerId,
                javascript
        ));
    }

    private ServiceEntryResult openCustomerService(ChromeTab tab, String offerId) {
        return probeResultParser.parseServiceEntryResult(executeTrusted(
                tab,
                Ali1688BrowserUrlPolicy.PageKind.OFFER_DETAIL,
                offerId,
                loadJavascriptResource("open-customer-service.js")
        ));
    }

    private String executeTrusted(
            ChromeTab tab,
            Ali1688BrowserUrlPolicy.PageKind pageKind,
            String offerId,
            String javascript
    ) {
        ChromeTab readyTab = ChromeTab.findCurrent(chromeClient.listChromeTabs(), tab);
        if (readyTab == null || !urlPolicy.matchesOfferId(readyTab.url, pageKind, offerId)) {
            return UNTRUSTED_REDIRECT_PAYLOAD;
        }
        tab.url = readyTab.url;
        tab.title = readyTab.title;
        String payload = chromeClient.executeTabJavascript(
                tab,
                urlPolicy.guardJavascript(pageKind, tab.url, javascript)
        );
        ChromeTab observedTab = ChromeTab.findCurrent(chromeClient.listChromeTabs(), tab);
        if (!stayedOnExpectedOffer(observedTab, pageKind, offerId)) {
            return UNTRUSTED_REDIRECT_PAYLOAD;
        }
        tab.url = observedTab.url;
        tab.title = observedTab.title;
        return payload;
    }

    private boolean stayedOnExpectedOffer(
            ChromeTab observedTab,
            Ali1688BrowserUrlPolicy.PageKind pageKind,
            String offerId
    ) {
        if (observedTab == null) {
            return false;
        }
        if (urlPolicy.matchesOfferId(observedTab.url, pageKind, offerId)) {
            return true;
        }
        return pageKind == Ali1688BrowserUrlPolicy.PageKind.OFFER_DETAIL
                && urlPolicy.matchesOfferId(
                observedTab.url,
                Ali1688BrowserUrlPolicy.PageKind.CHAT,
                offerId
        );
    }

    private int chatTabSpecificity(ChromeTab tab, String offerId) {
        String url = normalize(tab == null ? null : tab.url);
        if (url == null) {
            return 0;
        }
        int score = url.contains("offerId=" + offerId) ? 40 : 0;
        score += url.contains("touid=") ? 200 : 0;
        score += url.contains("sourceValue=") ? 200 : 0;
        score += url.contains("status=1") ? 20 : 0;
        return score;
    }

    private String loadJavascriptResource(String resourceName) {
        try (InputStream inputStream = Ali1688HostedBrowserNavigator.class.getResourceAsStream(
                "/procurement-browser/" + resourceName
        )) {
            if (inputStream == null) {
                throw new IllegalStateException("缺少浏览器执行脚本资源：" + resourceName);
            }
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取浏览器执行脚本资源失败：" + resourceName, exception);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                String normalized = normalize(value);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }
}
