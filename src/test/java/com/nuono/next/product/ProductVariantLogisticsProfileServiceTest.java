package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductVariantLogisticsProfileServiceTest {

    @Mock
    private ProductManagementMapper mapper;

    private ProductVariantLogisticsProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProductVariantLogisticsProfileService(mapper);
    }

    @Test
    void saveByPskuResolvesVariantBeforeSavingCompatibilityProfile() {
        ProductVariantLogisticsProfileCommand command = new ProductVariantLogisticsProfileCommand();
        command.ownerUserId = 10002L;
        command.storeCode = "STR69486-NSA";
        command.partnerSku = "SGGRB113";
        command.profileStatus = "confirmed";
        command.operatorUserId = 10003L;
        when(mapper.selectLogicalStoreIdByOwnerStoreCode(10002L, "STR69486-NSA")).thenReturn(51001L);
        when(mapper.selectProductVariantIdByStorePartnerSku(51001L, "SGGRB113")).thenReturn(53001L);
        when(mapper.selectProductVariantForSpecByVariantId(10002L, "STR69486-NSA", 53001L))
                .thenReturn(scopedVariant());
        when(mapper.nextProductVariantLogisticsProfileId()).thenReturn(130001L);
        ProductVariantLogisticsProfileView saved = scopedProfile();
        when(mapper.selectProductVariantLogisticsProfile(10002L, "STR69486-NSA", 53001L)).thenReturn(saved);

        ProductVariantLogisticsProfileView view = service.saveByPsku(command);

        assertEquals(53001L, view.variantId);
        verify(mapper).upsertProductVariantLogisticsProfile(any(ProductVariantLogisticsProfileCommand.class));
    }

    private ProductVariantSpecRecord scopedVariant() {
        ProductVariantSpecRecord row = new ProductVariantSpecRecord();
        row.setStoreCode("STR69486-NSA");
        row.setSkuParent("ZCURRENT");
        row.setVariantId(53001L);
        row.setPartnerSku("SGGRB113");
        row.setChildSku("N701");
        return row;
    }

    private ProductVariantLogisticsProfileView scopedProfile() {
        ProductVariantLogisticsProfileView view = new ProductVariantLogisticsProfileView();
        view.storeCode = "STR69486-NSA";
        view.skuParent = "ZCURRENT";
        view.variantId = 53001L;
        view.partnerSku = "SGGRB113";
        view.profileStatus = "confirmed";
        return view;
    }
}
