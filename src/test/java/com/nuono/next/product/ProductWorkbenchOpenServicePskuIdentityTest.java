package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProductWorkbenchOpenServicePskuIdentityTest {

    @Test
    void resolveIdentityIgnoresCurrentZCodeWhenPartnerSkuIsPresent() {
        ProductWorkbenchOpenService service = new ProductWorkbenchOpenService(null, null);
        ProductMasterFetchCommand first = command("SGGRB113", "ZOLD");
        ProductMasterFetchCommand second = command("SGGRB113", "ZNEW");

        ProductWorkbenchOpenService.OpenIdentity firstIdentity = service.resolveIdentity(first);
        ProductWorkbenchOpenService.OpenIdentity secondIdentity = service.resolveIdentity(second);

        assertEquals(firstIdentity, secondIdentity);
        assertEquals("STR69486-NSA", firstIdentity.storeCode());
        assertEquals("SGGRB113", firstIdentity.partnerSku());
        assertEquals("ZOLD", first.getCurrentZCode());
        assertEquals("ZNEW", second.getCurrentZCode());
    }

    @Test
    void resolveIdentityFallsBackToSkuParentForLegacyRequestsWithoutPartnerSku() {
        ProductWorkbenchOpenService service = new ProductWorkbenchOpenService(null, null);
        ProductMasterFetchCommand command = command(null, "ZLEGACY");

        ProductWorkbenchOpenService.OpenIdentity identity = service.resolveIdentity(command);

        assertEquals("STR69486-NSA", identity.storeCode());
        assertEquals("ZLEGACY", identity.legacySkuParent());
        assertEquals("ZLEGACY", command.getCurrentZCode());
    }

    private ProductMasterFetchCommand command(String partnerSku, String skuParent) {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR69486-NSA");
        command.setPartnerSku(partnerSku);
        command.setSkuParent(skuParent);
        return command;
    }
}
