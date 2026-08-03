package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import org.junit.jupiter.api.Test;

class ProductMasterIdentityRecoveryTest {

    private static final Long LOGICAL_STORE_ID = 50003L;
    private static final String PARTNER_SKU = "PAPERSAYSB446";
    private static final String Z_CODE = "ZC9FC3C3B7475EFDAF4AAZ";

    private final ProductManagementMapper mapper = mock(ProductManagementMapper.class);
    private final ProductMasterIdentityRecovery recovery = new ProductMasterIdentityRecovery(mapper);

    @Test
    void claimsUnassignedZCodeMasterWhenPartnerIdentityHasNoMatch() {
        when(mapper.selectProductMasterIdByStorePartnerSku(LOGICAL_STORE_ID, PARTNER_SKU)).thenReturn(null);
        when(mapper.selectUnclaimedProductMasterIdBySkuParent(LOGICAL_STORE_ID, Z_CODE)).thenReturn(54013L);

        Long resolved = recovery.resolve(
                LOGICAL_STORE_ID,
                new ProductIdentity(LOGICAL_STORE_ID, PARTNER_SKU),
                Z_CODE
        );

        assertEquals(54013L, resolved);
        verify(mapper).selectUnclaimedProductMasterIdBySkuParent(LOGICAL_STORE_ID, Z_CODE);
    }

    @Test
    void doesNotClaimZCodeWhenPartnerIdentityAlreadyExists() {
        when(mapper.selectProductMasterIdByStorePartnerSku(LOGICAL_STORE_ID, PARTNER_SKU)).thenReturn(54014L);

        assertEquals(54014L, recovery.resolve(
                LOGICAL_STORE_ID,
                new ProductIdentity(LOGICAL_STORE_ID, PARTNER_SKU),
                Z_CODE
        ));
    }

    @Test
    void treatsMapperDefaultZeroAsMissingIdentity() {
        assertNull(recovery.resolve(
                LOGICAL_STORE_ID,
                new ProductIdentity(LOGICAL_STORE_ID, PARTNER_SKU),
                null
        ));
    }
}
