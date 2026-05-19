package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParsePartnerIdentityLookupPasswordChannel() throws Exception {
        JsonNode root = objectMapper.readTree(
                "[{\"user_code\":\"prd-user@idp.noon.partners\",\"channels\":[{\"channel_code\":\"password\"}]}]"
        );

        NoonSessionGateway.PartnerIdentityUser user = NoonSessionGateway.extractPartnerIdentityUser(root);

        assertEquals("prd-user@idp.noon.partners", user.getUserCode());
    }

    @Test
    void shouldRejectPartnerIdentityLookupWithoutPasswordChannel() throws Exception {
        JsonNode root = objectMapper.readTree(
                "[{\"userCode\":\"prd-user@idp.noon.partners\",\"channels\":[{\"channelCode\":\"emailotp\"}]}]"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> NoonSessionGateway.extractPartnerIdentityUser(root)
        );
        assertTrue(exception.getMessage().contains("密码登录"));
    }

    @Test
    void shouldSelectRequestedProjectFromPartnerIdentityProjectList() throws Exception {
        JsonNode root = objectMapper.readTree(
                "{\"projects\":[{\"project_code\":\"PRJ108065\"},{\"project_code\":\"PRJ245027\"}]}"
        );

        assertEquals("PRJ245027", NoonSessionGateway.selectPartnerIdentityProjectCode(root, "prj245027"));
    }

    @Test
    void shouldRejectMissingRequestedProjectFromPartnerIdentityProjectList() throws Exception {
        JsonNode root = objectMapper.readTree(
                "{\"projects\":[{\"projectCode\":\"PRJ108065\"}]}"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> NoonSessionGateway.selectPartnerIdentityProjectCode(root, "PRJ245027")
        );
        assertTrue(exception.getMessage().contains("PRJ245027"));
    }

    @Test
    void shouldGenerateSpecLengthPkceVerifierAndKnownChallenge() {
        String verifier = NoonSessionGateway.generateCodeVerifier();

        assertEquals(128, verifier.length());
        assertEquals(
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                NoonSessionGateway.generateCodeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        );
    }
}
