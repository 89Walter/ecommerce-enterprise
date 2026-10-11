package com.ecommerce.order.order;

import com.ecommerce.order.catalog.CatalogProductClient;
import com.ecommerce.order.catalog.CatalogProductResponse;
import com.ecommerce.order.exception.CatalogServiceException;
import com.ecommerce.order.exception.InvalidOrderException;
import com.ecommerce.order.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private static final int MAX_QUANTITY_PER_PRODUCT = 99;
    private static final BigDecimal MAX_SUBTOTAL = new BigDecimal("9999999999.99");

    private final OrderRepository orderRepository;
    private final CatalogProductClient catalogProductClient;
    private final OrderMapper orderMapper;
    private final OrderEventPublisher orderEventPublisher;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    /**
     * Le chiamate HTTP al Catalog avvengono fuori dalla transazione, così una risposta lenta
     * non tiene occupata una connessione al database. La transazione copre solo il salvataggio
     * dell'ordine e dell'evento ORDER_PLACED nell'outbox.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderResponse create(String customerId, CreateOrderRequest request) {
        String normalizedCustomerId = normalizeCustomerId(customerId);
        Map<Long, Integer> quantities = aggregateQuantities(request.items());
        List<OrderLineSnapshot> lines = quantities.entrySet().stream()
                .map(entry -> snapshot(entry.getKey(), entry.getValue()))
                .toList();

        BigDecimal subtotal = lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        if (subtotal.compareTo(MAX_SUBTOTAL) > 0) {
            throw new InvalidOrderException("Il totale dell'ordine supera l'importo massimo consentito.");
        }

        PurchaseOrder order = PurchaseOrder.create(
                normalizedCustomerId,
                subtotal,
                toAddress(request.shippingAddress()),
                lines
        );
        return transactionOperations.execute(status -> {
            PurchaseOrder savedOrder = orderRepository.save(order);
            orderEventPublisher.orderPlaced(savedOrder);
            return orderMapper.toResponse(savedOrder);
        });
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllForCustomer(String customerId, Pageable pageable) {
        String normalizedCustomerId = normalizeCustomerId(customerId);
        return orderRepository.findByCustomerId(normalizedCustomerId, pageable).map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(OrderStatus status, Pageable pageable) {
        Page<PurchaseOrder> orders = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return orders.map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(String customerId, Long orderId) {
        return orderMapper.toResponse(getOwnedOrder(customerId, orderId));
    }

    public OrderResponse cancel(String customerId, Long orderId, String reason) {
        PurchaseOrder order = getOwnedOrder(customerId, orderId);
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            throw new InvalidOrderException("La motivazione dell'annullamento è obbligatoria.");
        }
        order.cancel(normalizedReason, LocalDateTime.now(clock));
        PurchaseOrder savedOrder = orderRepository.save(order);
        orderEventPublisher.orderCancelled(savedOrder);
        return orderMapper.toResponse(savedOrder);
    }

    private Map<Long, Integer> aggregateQuantities(List<OrderItemRequest> requestedItems) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : requestedItems) {
            if (item.productId() == null || item.productId() <= 0) {
                throw new InvalidOrderException("L'ID prodotto deve essere maggiore di zero.");
            }
            int quantity = quantities.getOrDefault(item.productId(), 0) + item.quantity();
            if (quantity > MAX_QUANTITY_PER_PRODUCT) {
                throw new InvalidOrderException("La quantità massima per prodotto è 99.");
            }
            quantities.put(item.productId(), quantity);
        }
        if (quantities.isEmpty()) {
            throw new InvalidOrderException("L'ordine deve contenere almeno un prodotto.");
        }
        return quantities;
    }

    private OrderLineSnapshot snapshot(Long productId, int quantity) {
        CatalogProductResponse product = catalogProductClient.findById(productId);
        if (!productId.equals(product.id())
                || product.name() == null
                || product.name().isBlank()
                || product.price() == null
                || product.price().signum() <= 0) {
            throw new CatalogServiceException(
                    "Il Catalog ha restituito dati non validi per il prodotto " + productId + "."
            );
        }

        BigDecimal unitPrice;
        try {
            unitPrice = product.price().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new CatalogServiceException("Il Catalog ha restituito un prezzo con precisione non valida.");
        }
        return new OrderLineSnapshot(productId, product.name().trim(), unitPrice, quantity);
    }

    private ShippingAddress toAddress(ShippingAddressRequest request) {
        return new ShippingAddress(
                request.recipientName().trim(),
                request.addressLine().trim(),
                request.city().trim(),
                request.postalCode().trim(),
                request.countryCode().trim().toUpperCase(Locale.ROOT)
        );
    }

    private PurchaseOrder getOwnedOrder(String customerId, Long orderId) {
        String normalizedCustomerId = normalizeCustomerId(customerId);
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getCustomerId().equals(normalizedCustomerId)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    private String normalizeCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new InvalidOrderException("Il token non contiene un identificativo utente valido.");
        }
        return customerId.trim();
    }
}
