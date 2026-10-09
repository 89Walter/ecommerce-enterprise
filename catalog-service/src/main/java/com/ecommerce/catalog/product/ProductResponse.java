package com.ecommerce.catalog.product;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        LocalDateTime createdAt
) {}
