package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductDetailFrontendCandidateMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.ProductPublicDetailFetchResult;
import com.nuono.next.productpublicdetail.ProductPublicDetailSnapshot;
import com.nuono.next.productpublicdetail.ProductPublicDetailSingleFetchService;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class ProductDetailFrontendFetchService {
    private final ProductPublicDetailMapper publicDetailMapper;
    private final ProductDetailFrontendCandidateMapper candidateMapper;
    private final ProductPublicDetailSingleFetchService publicDetailSingleFetchService;

    public ProductDetailFrontendFetchService(
            ProductPublicDetailMapper publicDetailMapper,
            ProductDetailFrontendCandidateMapper candidateMapper,
            ProductPublicDetailSingleFetchService publicDetailSingleFetchService
    ) {
        this.publicDetailMapper = publicDetailMapper;
        this.candidateMapper = candidateMapper;
        this.publicDetailSingleFetchService = publicDetailSingleFetchService;
    }

    public ProductPublicDetailFetchResult fetch(ProductMasterFetchCommand command, Long taskId) {
        if (command == null) {
            return ProductPublicDetailFetchResult.of(
                    ProductPublicDetailSyncStatus.FAILED,
                    "商品详情拉取命令为空，无法请求 Noon 前台。"
            );
        }
        ProductPublicDetailSnapshot existing = publicDetailMapper.selectLatestUsableSnapshotBySkuParent(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSkuParent()
        );
        if (existing != null && existing.getSyncStatus() != null) {
            return ProductPublicDetailFetchResult.of(
                    existing.getSyncStatus(),
                    "已存在可用的 Noon 前台详情。"
            );
        }
        ProductPublicDetailCandidate candidate = candidateMapper.selectCandidateByProductIdentity(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSkuParent(),
                command.getPartnerSku(),
                command.getPskuCode()
        );
        if (candidate == null) {
            return ProductPublicDetailFetchResult.of(
                    ProductPublicDetailSyncStatus.FAILED,
                    "未找到可用于 Noon 前台详情拉取的商品身份。"
            );
        }
        return publicDetailSingleFetchService.fetch(candidate, command.getOwnerUserId(), taskId);
    }
}
