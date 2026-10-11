package com.ecommerce.order.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String customerId,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        ShippingAddressResponse shippingAddress,
        String cancellationReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime confirmedAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime cancelledAt,
        LocalDateTime refundedAt
) {}
