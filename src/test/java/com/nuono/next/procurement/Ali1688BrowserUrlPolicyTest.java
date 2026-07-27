package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.procurement.Ali1688BrowserUrlPolicy.PageKind;
import com.nuono.next.procurement.ProcurementAutoInquiryTargetResolver.TargetResolution;
import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688BrowserUrlPolicyTest {

    private final Ali1688BrowserUrlPolicy policy = new Ali1688BrowserUrlPolicy();

    @Test
    void shouldAcceptOnlyTheKnownHttpsPagesOnExactHosts() {
        String resultUrl = "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult?id=12#reply";
        String createUrl = "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryCreate?from=nuono";
        String chatUrl = "https://air.1688.com/app/ocms-fusion-components-1688/def_cbu_web_im/index.html"
                + "?offerId=798448779771#/";
        String offerUrl = "https://detail.1688.com/offer/798448779771.html?spm=test";

        assertEquals(resultUrl, policy.validateRequestedUrl(resultUrl, PageKind.INQUIRY_RESULT));
        assertEquals(createUrl, policy.validateRequestedUrl(createUrl, PageKind.INQUIRY_CREATE));
        assertEquals(chatUrl, policy.validateRequestedUrl(chatUrl, PageKind.CHAT));
        assertEquals(offerUrl, policy.validateRequestedUrl(offerUrl, PageKind.OFFER_DETAIL));
        assertTrue(policy.acceptsObservedUrl(resultUrl, PageKind.INQUIRY_RESULT));
        assertTrue(policy.matchesOfferId(chatUrl, PageKind.CHAT, "798448779771"));
        assertTrue(policy.matchesOfferId(offerUrl, PageKind.OFFER_DETAIL, "798448779771"));
        assertTrue(policy.matchesRequestedPage(
                resultUrl.replace("#reply", "#changed"),
                resultUrl,
                PageKind.INQUIRY_RESULT
        ));
    }

    @Test
    void shouldRejectDeceptiveAuthoritiesPortsSchemesAndPaths() {
        List<String> rejectedResultUrls = List.of(
                "http://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult",
                "https://user@air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult",
                "https://air.1688.com:443/kapp/1688-pc-front/ai-avatar/inquiryResult",
                "https://air.1688.com:8443/kapp/1688-pc-front/ai-avatar/inquiryResult",
                "https://evil.air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult",
                "https://air.1688.com.evil.test/kapp/1688-pc-front/ai-avatar/inquiryResult",
                "https://air.1688.com./kapp/1688-pc-front/ai-avatar/inquiryResult",
                "https://127.0.0.1/kapp/1688-pc-front/ai-avatar/inquiryResult",
                "https://[::1]/kapp/1688-pc-front/ai-avatar/inquiryResult",
                "//air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult",
                "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryCreate"
        );

        rejectedResultUrls.forEach(url -> assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateRequestedUrl(url, PageKind.INQUIRY_RESULT),
                url
        ));
        assertFalse(policy.acceptsObservedUrl(
                "https://evil.test/?next=air.1688.com&inquiryResult=1",
                PageKind.INQUIRY_RESULT
        ));
    }

    @Test
    void shouldRejectSpoofedOfferHostsAtTheTargetResolutionSeam() {
        ProcurementAutoInquiryTargetResolver resolver = new ProcurementAutoInquiryTargetResolver(policy);
        CandidateView candidate = new CandidateView();
        candidate.setSupplierName("可信供应商");
        candidate.setCandidateUrl("https://evil1688.com/offer/798448779771.html");

        TargetResolution rejected = resolver.resolve(candidate);

        assertFalse(rejected.isResolved());
        assertEquals("INVALID_1688_URL", rejected.getFailureCode());

        candidate.setCandidateUrl("https://detail.1688.com/offer/798448779771.html");
        TargetResolution accepted = resolver.resolve(candidate);
        assertTrue(accepted.isResolved());
        assertEquals("798448779771", accepted.getOfferId());
    }

    @Test
    void shouldEmbedTheSameOriginAndPathContractInBrowserJavascript() {
        String chatUrl = "https://air.1688.com/app/ocms-fusion-components-1688/"
                + "def_cbu_web_im/index.html?offerId=798448779771&status=1#/";
        String guarded = policy.guardJavascript(PageKind.CHAT, chatUrl, "(() => 'ok')();");

        assertTrue(guarded.contains("location.protocol"));
        assertTrue(guarded.contains("air.1688.com"));
        assertTrue(guarded.contains("def_cbu_web_im/index.html"));
        assertTrue(guarded.contains("UNTRUSTED_BROWSER_URL"));
        assertTrue(guarded.contains("location.search==='?offerId=798448779771&status=1'"));
        assertFalse(guarded.contains("String(location"));
        assertFalse(guarded.contains("JSON.stringify({ok:false"));
    }

    @Test
    void shouldRejectAmbiguousOrDifferentOfferAndPageTargets() {
        String duplicateOfferUrl = "https://air.1688.com/app/ocms-fusion-components-1688/"
                + "def_cbu_web_im/index.html?offerId=798448779771&offerId=123";
        String expectedResultUrl =
                "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult?id=12";
        String otherResultUrl =
                "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult?id=99";

        assertFalse(policy.matchesOfferId(
                duplicateOfferUrl,
                PageKind.CHAT,
                "798448779771"
        ));
        assertFalse(policy.matchesRequestedPage(
                otherResultUrl,
                expectedResultUrl,
                PageKind.INQUIRY_RESULT
        ));
    }
}
