package com.ecommerce.order.order;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(PurchaseOrder order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineTotal()
                ))
                .toList();
        ShippingAddress address = order.getShippingAddress();
        ShippingAddressResponse addressResponse = new ShippingAddressResponse(
                address.getRecipientName(),
                address.getAddressLine(),
                address.getCity(),
                address.getPostalCode(),
                address.getCountryCode()
        );
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                items,
                order.getSubtotal(),
                addressResponse,
                order.getCancellationReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getConfirmedAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                order.getRefundedAt()
        );
    }
}
