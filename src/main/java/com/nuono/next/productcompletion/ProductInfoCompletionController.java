package com.nuono.next.productcompletion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-info-completion")
public class ProductInfoCompletionController {

    private final ProductInfoCompletionService productInfoCompletionService;

    public ProductInfoCompletionController(ProductInfoCompletionService productInfoCompletionService) {
        this.productInfoCompletionService = productInfoCompletionService;
    }

    @PostMapping("/1688/preview")
    public ProductInfoCompletionView preview1688(@RequestBody ProductInfoCompletionCommand command) {
        try {
            return productInfoCompletionService.preview1688(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
