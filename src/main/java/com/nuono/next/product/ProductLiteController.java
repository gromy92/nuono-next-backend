package com.nuono.next.product;

import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.permission.access.BusinessStoreAccess;
import com.nuono.next.permission.access.RequiredBusinessAccess;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/lite")
public class ProductLiteController {

    private final ProductLiteQueryService service;

    public ProductLiteController(ProductLiteQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductLiteView> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String titleKeyword,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit,
            @RequiredBusinessAccess(
                    capability = BusinessCapability.PRODUCT_MASTER,
                    storeQueryParameter = "storeCode"
            )
            BusinessStoreAccess storeAccess
    ) {
        ProductLiteQuery query = new ProductLiteQuery();
        query.setSiteCode(siteCode);
        query.setTitleKeyword(StringUtils.hasText(titleKeyword) ? titleKeyword : keyword);
        query.setLimit(limit);
        return service.search(storeAccess, query);
    }
}
