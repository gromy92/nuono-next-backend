package com.nuono.next.product;

import java.util.List;

interface ProductImageGenerator {
    GeneratedProductImage generate(String prompt, List<String> referenceImageUrls);
}
