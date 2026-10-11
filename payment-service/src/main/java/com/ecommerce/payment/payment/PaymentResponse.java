package com.ecommerce.payment.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String transactionReference,
        String refundReference,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime refundedAt
) {

    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getTransactionReference(),
                payment.getRefundReference(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getCompletedAt(),
                payment.getRefundedAt()
        );
    }
}
