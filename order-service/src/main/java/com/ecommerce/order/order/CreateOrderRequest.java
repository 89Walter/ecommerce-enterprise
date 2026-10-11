package com.ecommerce.order.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "L'ordine deve contenere almeno un prodotto.")
        @Size(max = 50, message = "Un ordine può contenere al massimo 50 prodotti distinti.")
        List<@NotNull @Valid OrderItemRequest> items,
        @NotNull(message = "L'indirizzo di spedizione è obbligatorio.")
        @Valid ShippingAddressRequest shippingAddress
) {}
