package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductDetailFrontendCandidateMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.ProductPublicDetailFetchResult;
import com.nuono.next.productpublicdetail.ProductPublicDetailSingleFetchService;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import org.junit.jupiter.api.Test;

class ProductDetailFrontendFetchServiceTest {

    @Test
    void shouldResolveProductIdentityAndFetchFromNoonFrontend() {
        ProductPublicDetailMapper mapper = mock(ProductPublicDetailMapper.class);
        ProductDetailFrontendCandidateMapper candidateMapper = mock(ProductDetailFrontendCandidateMapper.class);
        ProductPublicDetailSingleFetchService singleFetchService = mock(ProductPublicDetailSingleFetchService.class);
        ProductDetailFrontendFetchService service =
                new ProductDetailFrontendFetchService(mapper, candidateMapper, singleFetchService);
        ProductMasterFetchCommand command = command();
        ProductPublicDetailCandidate candidate = new ProductPublicDetailCandidate();
        candidate.setOwnerUserId(307L);
        candidate.setStoreCode("STR108065-NAE");
        candidate.setSiteCode("AE");
        candidate.setProductMasterId(1001L);
        candidate.setProductVariantId(2001L);
        candidate.setProductSiteOfferId(3001L);
        candidate.setSkuParent("Z123");
        candidate.setNoonProductCode("Z123");
        when(candidateMapper.selectCandidateByProductIdentity(
                307L,
                "STR108065-NAE",
                "Z123",
                "PAPERSAYSB123",
                "PSKU-123"
        )).thenReturn(candidate);
        when(singleFetchService.fetch(candidate, 307L, 9001L)).thenReturn(
                ProductPublicDetailFetchResult.of(ProductPublicDetailSyncStatus.PARTIAL, "available")
        );

        ProductPublicDetailFetchResult result = service.fetch(command, 9001L);

        assertEquals(ProductPublicDetailSyncStatus.PARTIAL, result.getStatus());
        verify(singleFetchService).fetch(candidate, 307L, 9001L);
    }

    private ProductMasterFetchCommand command() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR108065-NAE");
        command.setSkuParent("Z123");
        command.setPartnerSku("PAPERSAYSB123");
        command.setPskuCode("PSKU-123");
        return command;
    }
}
