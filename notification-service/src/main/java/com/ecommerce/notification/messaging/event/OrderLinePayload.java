package com.ecommerce.notification.messaging.event;

import java.math.BigDecimal;

public record OrderLinePayload(Long productId, String productName, BigDecimal unitPrice, int quantity) {}
