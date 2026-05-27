package com.nuono.next.system;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendFallbackController {

    @GetMapping({
            "/",
            "/login",
            "/login/register",
            "/login/reset-pwd",
            "/user/manage",
            "/user/role",
            "/user/store-binding",
            "/system/role",
            "/system/menu",
            "/system/file-management",
            "/system/file-management/**",
            "/system/ai-file-parse",
            "/system/ai-file-parse/**",
            "/system-reports",
            "/system-reports/**",
            "/noon-call/store-data",
            "/product-manage",
            "/product/manual-selection",
            "/product/manual-selection/**",
            "/purchase/order",
            "/purchase/profit",
            "/purchase/logistics-quote",
            "/purchase/logistics-quote/**",
            "/purchase/order/requirement-confirmation",
            "/purchase/order/requirement-confirmation/**"
    })
    public String forwardToFrontend() {
        return "forward:/index.html";
    }
}
