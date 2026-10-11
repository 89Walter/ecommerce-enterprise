package com.ecommerce.order.order;

import java.math.BigDecimal;

record OrderLineSnapshot(Long productId, String productName, BigDecimal unitPrice, int quantity) {}
