package com.ecommerce.catalog.product;

import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public Product toEntity(ProductRequest request) {
        return Product.builder()
                .name(request.name().trim())
                .description(request.description())
                .price(request.price())
                .build();
    }

    public void updateEntity(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
    }

    public ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
