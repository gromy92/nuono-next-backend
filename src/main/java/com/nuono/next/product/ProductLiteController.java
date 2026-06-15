package com.nuono.next.product;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/lite")
public class ProductLiteController {

    private final ProductLiteQueryService service;
    private final BusinessAccessResolver businessAccessResolver;

    public ProductLiteController(
            ProductLiteQueryService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping
    public List<ProductLiteView> search(
            @RequestParam String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String titleKeyword,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                storeCode
        );
        ProductLiteQuery query = new ProductLiteQuery();
        query.setStoreCode(storeCode);
        query.setSiteCode(siteCode);
        query.setTitleKeyword(StringUtils.hasText(titleKeyword) ? titleKeyword : keyword);
        query.setLimit(limit);
        return service.search(context, query);
    }
}
