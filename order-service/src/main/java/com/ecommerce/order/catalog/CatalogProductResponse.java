package com.ecommerce.order.catalog;

import java.math.BigDecimal;

public record CatalogProductResponse(Long id, String name, BigDecimal price) {}
