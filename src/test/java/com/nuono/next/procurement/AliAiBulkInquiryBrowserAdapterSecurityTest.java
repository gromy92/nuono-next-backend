package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.procurement.ProcurementAutoInquirySendGateway.SendAttemptResult;
import com.nuono.next.procurement.ProcurementAutoInquirySendGateway.SendPreparationResult;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AliAiBulkInquiryBrowserAdapterSecurityTest {

    @Mock
    private ChromeAppleScriptClient chromeClient;

    private ObjectMapper objectMapper;
    private Ali1688BrowserUrlPolicy urlPolicy;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        urlPolicy = new Ali1688BrowserUrlPolicy();
    }

    @Test
    void shouldRejectUntrustedRequestedUrlsBeforeTouchingChrome() {
        AliAiBulkInquiryReadAdapter resultAdapter =
                new AliAiBulkInquiryReadAdapter(chromeClient, objectMapper, urlPolicy);
        AliAiBulkInquiryCreatePageReadAdapter createAdapter =
                new AliAiBulkInquiryCreatePageReadAdapter(chromeClient, objectMapper, urlPolicy);

        assertThrows(
                IllegalArgumentException.class,
                () -> resultAdapter.readResultPage(
                        "https://air.1688.com.evil.test/kapp/1688-pc-front/ai-avatar/inquiryResult",
                        true
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> createAdapter.readCreatePage(
                        "https://evil.test/?next=air.1688.com&inquiry=1",
                        true
                )
        );

        verifyNoInteractions(chromeClient);
    }

    @Test
    void shouldIgnoreSpoofedExistingCreateTabs() {
        AliAiBulkInquiryCreatePageReadAdapter adapter =
                new AliAiBulkInquiryCreatePageReadAdapter(chromeClient, objectMapper, urlPolicy);
        when(chromeClient.listChromeTabs()).thenReturn(List.of(
                tab("https://evil.test/?next=air.1688.com&inquiry=1")
        ));

        AliAiBulkInquiryCreatePageSnapshot snapshot = adapter.readCreatePage(
                "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryCreate",
                false
        );

        assertFalse(snapshot.isOk());
        assertEquals("ALI_AI_CREATE_PAGE_TAB_NOT_FOUND", snapshot.getFailureCode());
        verify(chromeClient, never()).focusTab(any());
        verify(chromeClient, never()).executeTabJavascript(any(), anyString());
    }

    @Test
    void shouldRejectAnUntrustedObservedResultBeforeReturningPageData() {
        AliAiBulkInquiryReadAdapter adapter =
                new AliAiBulkInquiryReadAdapter(chromeClient, objectMapper, urlPolicy);
        ChromeTab trustedTab = tab(
                "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult?id=12"
        );
        when(chromeClient.listChromeTabs()).thenReturn(
                List.of(trustedTab),
                List.of(tab("https://evil.test/private"))
        );
        when(chromeClient.executeTabJavascript(any(), anyString())).thenReturn(
                "{\"ok\":true,\"url\":\"https://air.1688.com/kapp/1688-pc-front/"
                        + "ai-avatar/inquiryResult?id=12\",\"title\":\"result\",\"text\":\"reply\"}"
        );

        AliAiBulkInquiryPageSnapshot snapshot = adapter.readResultPage(trustedTab.url, false);

        assertFalse(snapshot.isOk());
        assertEquals("ALI_AI_RESULT_UNTRUSTED_REDIRECT", snapshot.getFailureCode());
        ArgumentCaptor<String> javascript = ArgumentCaptor.forClass(String.class);
        verify(chromeClient).executeTabJavascript(any(), javascript.capture());
        assertFalse(javascript.getValue().contains("evil.test"));
    }

    @Test
    void shouldNotReadAnotherTrustedResultTabWhenTheQueryIdentityDiffers() {
        AliAiBulkInquiryReadAdapter adapter =
                new AliAiBulkInquiryReadAdapter(chromeClient, objectMapper, urlPolicy);
        when(chromeClient.listChromeTabs()).thenReturn(List.of(tab(
                "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult?id=99"
        )));

        AliAiBulkInquiryPageSnapshot snapshot = adapter.readResultPage(
                "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult?id=12",
                false
        );

        assertFalse(snapshot.isOk());
        assertEquals("ALI_AI_RESULT_TAB_NOT_FOUND", snapshot.getFailureCode());
        verify(chromeClient, never()).executeTabJavascript(any(), anyString());
    }

    @Test
    void hostedSendShouldNeverSelectAnUntrustedOfferRelatedTab() {
        LocalChromeHostedBrowserSendAdapter adapter = hostedSendAdapter();
        when(chromeClient.listChromeTabs()).thenReturn(List.of(
                tab("https://evil.test/private?offerId=798448779771")
        ));
        AutoInquiryTaskView task = new AutoInquiryTaskView();
        task.setTargetOfferId("798448779771");
        task.setInputPayloadText("请报价");

        SendPreparationResult result = adapter.prepareInput(task, null);

        assertFalse(result.isReady());
        assertEquals("CHAT_TAB_NOT_FOUND", result.getFailureCode());
        verify(chromeClient, never()).focusTab(any());
        verify(chromeClient, never()).executeTabJavascript(any(), anyString());
    }

    @Test
    void hostedSendShouldFailIfTheSelectedTabChangesToAnotherOffer() {
        LocalChromeHostedBrowserSendAdapter adapter = hostedSendAdapter();
        ChromeTab expectedTab = tab(
                "https://air.1688.com/app/ocms-fusion-components-1688/"
                        + "def_cbu_web_im/index.html?offerId=798448779771"
        );
        ChromeTab otherOfferTab = tab(
                "https://air.1688.com/app/ocms-fusion-components-1688/"
                        + "def_cbu_web_im/index.html?offerId=123456789012"
        );
        when(chromeClient.listChromeTabs()).thenReturn(
                List.of(expectedTab),
                List.of(expectedTab),
                List.of(otherOfferTab)
        );
        when(chromeClient.executeTabJavascript(any(), anyString())).thenReturn(
                "{\"ok\":true,\"locator\":\"textarea\",\"editorText\":\"请报价\",\"bodyText\":\"\"}"
        );
        AutoInquiryTaskView task = new AutoInquiryTaskView();
        task.setTargetOfferId("798448779771");
        task.setInputPayloadText("请报价");

        SendPreparationResult result = adapter.prepareInput(task, null);

        assertFalse(result.isReady());
        assertEquals("UNTRUSTED_BROWSER_REDIRECT", result.getFailureCode());
    }

    @Test
    void hostedSendShouldRefreshAValidSameOfferQueryBeforeExecuting() {
        LocalChromeHostedBrowserSendAdapter adapter = hostedSendAdapter();
        ChromeTab selectedTab = tab(
                "https://air.1688.com/app/ocms-fusion-components-1688/"
                        + "def_cbu_web_im/index.html?offerId=798448779771"
        );
        ChromeTab refreshedTab = tab(
                "https://air.1688.com/app/ocms-fusion-components-1688/"
                        + "def_cbu_web_im/index.html?offerId=798448779771&touid=supplier&status=1"
        );
        when(chromeClient.listChromeTabs()).thenReturn(
                List.of(selectedTab),
                List.of(refreshedTab),
                List.of(refreshedTab)
        );
        when(chromeClient.executeTabJavascript(any(), anyString())).thenReturn(
                "{\"ok\":true,\"locator\":\"textarea\",\"editorText\":\"请报价\",\"bodyText\":\"\"}"
        );
        AutoInquiryTaskView task = new AutoInquiryTaskView();
        task.setTargetOfferId("798448779771");
        task.setInputPayloadText("请报价");

        SendPreparationResult result = adapter.prepareInput(task, null);

        assertTrue(result.isReady());
        ArgumentCaptor<String> javascript = ArgumentCaptor.forClass(String.class);
        verify(chromeClient).executeTabJavascript(any(), javascript.capture());
        assertTrue(javascript.getValue().contains(
                "location.search==='?offerId=798448779771&touid=supplier&status=1'"
        ));
    }

    @Test
    void hostedSendShouldNotReportDeliveredAfterAPostSendRedirect() {
        LocalChromeHostedBrowserSendAdapter adapter = hostedSendAdapter();
        ChromeTab expectedTab = tab(
                "https://air.1688.com/app/ocms-fusion-components-1688/"
                        + "def_cbu_web_im/index.html?offerId=798448779771"
        );
        ChromeTab otherOfferTab = tab(
                "https://air.1688.com/app/ocms-fusion-components-1688/"
                        + "def_cbu_web_im/index.html?offerId=123456789012"
        );
        when(chromeClient.listChromeTabs()).thenReturn(
                List.of(expectedTab),
                List.of(expectedTab),
                List.of(expectedTab),
                List.of(expectedTab),
                List.of(expectedTab),
                List.of(otherOfferTab)
        );
        when(chromeClient.executeTabJavascript(any(), anyString())).thenReturn(
                "{\"ok\":true,\"locator\":\"textarea\",\"editorText\":\"请报价\",\"bodyText\":\"\"}",
                "{\"ok\":true,\"triggerType\":\"click\",\"sendControlLocator\":\"button\"}"
        );
        AutoInquiryTaskView task = new AutoInquiryTaskView();
        task.setTargetOfferId("798448779771");
        task.setInputPayloadText("请报价");

        SendAttemptResult result = adapter.send(task, null);

        assertFalse(result.isDelivered());
        assertEquals("UNTRUSTED_BROWSER_REDIRECT", result.getFailureCode());
    }

    @Test
    void chromeTransportShouldNotDecodeJavascriptInsideThePageRealm() {
        ChromeAppleScriptClient client = new ChromeAppleScriptClient();
        String script = client.buildExecuteTabJavascriptAppleScript(
                tab("https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult"),
                "(() => {\nreturn 'ok';\n})()"
        );

        assertFalse(script.contains("eval(atob"));
        assertTrue(script.contains("return execute javascript"));
        assertTrue(script.contains("\" & linefeed & \""));
    }

    @Test
    void focusingAMultiWindowTabShouldSynchronizeItsPromotedCoordinate() {
        ChromeAppleScriptClient client = spy(new ChromeAppleScriptClient());
        doReturn("").when(client).runAppleScript(anyString());
        doNothing().when(client).sleep(anyLong());
        ChromeTab selectedTab = tab("https://air.1688.com/test");
        selectedTab.windowIndex = 3;
        selectedTab.tabIndex = 4;

        client.focusTab(selectedTab);

        assertEquals(1, selectedTab.windowIndex);
        ArgumentCaptor<String> appleScript = ArgumentCaptor.forClass(String.class);
        verify(client).runAppleScript(appleScript.capture());
        assertTrue(appleScript.getValue().contains("active tab index of window 3 to 4"));
    }

    private LocalChromeHostedBrowserSendAdapter hostedSendAdapter() {
        ChromeProbeResultParser parser = new ChromeProbeResultParser(objectMapper);
        return new LocalChromeHostedBrowserSendAdapter(
                chromeClient,
                parser,
                urlPolicy,
                new Ali1688HostedBrowserNavigator(chromeClient, parser, urlPolicy)
        );
    }

    private ChromeTab tab(String url) {
        ChromeTab tab = new ChromeTab();
        tab.windowIndex = 1;
        tab.tabIndex = 1;
        tab.title = "test";
        tab.url = url;
        return tab;
    }
}
