package com.nuono.next.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class AliAiBulkInquiryCreatePageReadAdapter {

    private final ChromeAppleScriptClient chromeClient;
    private final ObjectMapper objectMapper;
    private final Ali1688BrowserUrlPolicy urlPolicy;

    AliAiBulkInquiryCreatePageReadAdapter(
            ChromeAppleScriptClient chromeClient,
            ObjectMapper objectMapper,
            Ali1688BrowserUrlPolicy urlPolicy
    ) {
        this.chromeClient = chromeClient;
        this.objectMapper = objectMapper;
        this.urlPolicy = urlPolicy;
    }

    AliAiBulkInquiryCreatePageSnapshot readCreatePage(String pageUrl, boolean openIfMissing) {
        String normalizedUrl = urlPolicy.validateRequestedUrl(
                pageUrl,
                Ali1688BrowserUrlPolicy.PageKind.INQUIRY_CREATE
        );
        if (!StringUtils.hasText(normalizedUrl)) {
            AliAiBulkInquiryCreatePageSnapshot snapshot = new AliAiBulkInquiryCreatePageSnapshot();
            snapshot.setOk(false);
            snapshot.setFailureCode("ALI_AI_CREATE_PAGE_URL_REQUIRED");
            snapshot.setFailureMessage("读取 1688 智能询盘创建页时必须提供明确的受信任地址。");
            return snapshot;
        }
        ChromeTab createTab = findCreatePageTab(normalizedUrl);
        if (createTab == null && openIfMissing && StringUtils.hasText(normalizedUrl)) {
            chromeClient.openTab(normalizedUrl);
            chromeClient.sleep(2500L);
            createTab = findCreatePageTab(normalizedUrl);
        }
        if (createTab == null) {
            AliAiBulkInquiryCreatePageSnapshot snapshot = new AliAiBulkInquiryCreatePageSnapshot();
            snapshot.setOk(false);
            snapshot.setFailureCode("ALI_AI_CREATE_PAGE_TAB_NOT_FOUND");
            snapshot.setFailureMessage("没有找到已打开的 1688 智能询盘创建页。");
            snapshot.setUrl(normalizedUrl);
            return snapshot;
        }

        chromeClient.focusTab(createTab);
        String payload = chromeClient.executeTabJavascript(
                createTab,
                urlPolicy.guardJavascript(
                        Ali1688BrowserUrlPolicy.PageKind.INQUIRY_CREATE,
                        normalizedUrl,
                        buildReadCreatePageJavascript()
                )
        );
        ChromeTab observedTab = ChromeTab.findCurrent(chromeClient.listChromeTabs(), createTab);
        if (observedTab == null || !urlPolicy.matchesRequestedPage(
                observedTab.url,
                normalizedUrl,
                Ali1688BrowserUrlPolicy.PageKind.INQUIRY_CREATE
        )) {
            return failure(
                    "ALI_AI_CREATE_PAGE_UNTRUSTED_REDIRECT",
                    "1688 智能询盘创建页已跳转到其他地址，未读取页面内容。",
                    observedTab == null ? createTab : observedTab
            );
        }
        try {
            AliAiBulkInquiryCreatePageSnapshot snapshot =
                    objectMapper.readValue(payload, AliAiBulkInquiryCreatePageSnapshot.class);
            if (snapshot == null) {
                return failure("ALI_AI_CREATE_PAGE_EMPTY", "1688 智能询盘创建页没有返回可读快照。", observedTab);
            }
            snapshot.setUrl(observedTab.url);
            snapshot.setTitle(firstNonBlank(snapshot.getTitle(), observedTab.title));
            return snapshot;
        } catch (JsonProcessingException exception) {
            return failure("ALI_AI_CREATE_PAGE_PARSE_FAILED", "1688 智能询盘创建页快照无法解析。", observedTab);
        }
    }

    private ChromeTab findCreatePageTab(String requestedUrl) {
        return chromeClient.listChromeTabs().stream()
                .filter(tab -> tab != null && urlPolicy.matchesRequestedPage(
                        tab.url,
                        requestedUrl,
                        Ali1688BrowserUrlPolicy.PageKind.INQUIRY_CREATE
                ))
                .max(Comparator.comparingInt((ChromeTab tab) -> tab.windowIndex)
                        .thenComparingInt(tab -> tab.tabIndex))
                .orElse(null);
    }

    private AliAiBulkInquiryCreatePageSnapshot failure(String code, String message, ChromeTab tab) {
        AliAiBulkInquiryCreatePageSnapshot snapshot = new AliAiBulkInquiryCreatePageSnapshot();
        snapshot.setOk(false);
        snapshot.setFailureCode(code);
        snapshot.setFailureMessage(message);
        if (tab != null) {
            snapshot.setUrl(tab.url);
            snapshot.setTitle(tab.title);
        }
        return snapshot;
    }

    private String buildReadCreatePageJavascript() {
        return "(function(){"
                + "function compact(value){return String(value || '').replace(/\\s+/g,' ').trim();}"
                + "function attr(el,name){return compact(el.getAttribute(name) || '');}"
                + "function visible(el){"
                + "var rect=el.getBoundingClientRect();"
                + "var style=window.getComputedStyle ? window.getComputedStyle(el) : null;"
                + "return !!((rect.width || rect.height) && (!style || (style.display!=='none' && style.visibility!=='hidden' && Number(style.opacity || 1)>0)));"
                + "}"
                + "function textOf(el){return compact(el.innerText || el.textContent || el.value || '');}"
                + "try {"
                + "var selector='button,a,input,textarea,select,[role=\"button\"],[contenteditable=\"true\"],[data-spm-click]';"
                + "var nodes=Array.prototype.slice.call(document.querySelectorAll(selector)).slice(0,160);"
                + "var elements=nodes.map(function(el){return {"
                + "tagName:compact(el.tagName).toLowerCase(),"
                + "type:attr(el,'type'),"
                + "text:textOf(el).slice(0,120),"
                + "placeholder:attr(el,'placeholder').slice(0,120),"
                + "name:attr(el,'name').slice(0,80),"
                + "elementId:attr(el,'id').slice(0,80),"
                + "className:compact(typeof el.className==='string' ? el.className : '').slice(0,120),"
                + "ariaLabel:attr(el,'aria-label').slice(0,120),"
                + "title:attr(el,'title').slice(0,120),"
                + "role:attr(el,'role').slice(0,80),"
                + "visible:visible(el)"
                + "};});"
                + "var text=compact(document.body ? document.body.innerText : '').slice(0,5000);"
                + "return JSON.stringify({ok:true,url:location.href,title:document.title || '',text:text,elements:elements});"
                + "} catch (error) {"
                + "return JSON.stringify({ok:false,failureCode:'ALI_AI_CREATE_PAGE_READ_FAILED',failureMessage:String(error && error.message || error)});"
                + "}"
                + "})();";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
