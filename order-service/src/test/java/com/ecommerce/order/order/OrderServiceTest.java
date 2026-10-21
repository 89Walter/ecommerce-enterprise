package com.ecommerce.order.order;

import com.ecommerce.order.catalog.CatalogProductClient;
import com.ecommerce.order.catalog.CatalogProductResponse;
import com.ecommerce.order.exception.CatalogProductNotFoundException;
import com.ecommerce.order.exception.InvalidOrderException;
import com.ecommerce.order.exception.OrderNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);

    private OrderRepository orderRepository;
    private CatalogProductClient catalogProductClient;
    private OrderEventPublisher orderEventPublisher;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        catalogProductClient = mock(CatalogProductClient.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        orderService = new OrderService(
                orderRepository,
                catalogProductClient,
                new OrderMapper(),
                orderEventPublisher,
                TransactionOperations.withoutTransaction(),
                Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createsOrderWithCatalogPriceSnapshotsAndMergesDuplicateProducts() {
        given(catalogProductClient.findById(7L))
                .willReturn(new CatalogProductResponse(7L, " Keyboard ", new BigDecimal("23.40")));
        given(orderRepository.save(any(PurchaseOrder.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.create(
                " customer-1 ",
                new CustomerContact("ada@example.test", "Ada Lovelace"),
                request(List.of(new OrderItemRequest(7L, 2), new OrderItemRequest(7L, 1)))
        );

        assertThat(response.customerId()).isEqualTo("customer-1");
        assertThat(response.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("Keyboard");
            assertThat(item.unitPrice()).isEqualByComparingTo("23.40");
            assertThat(item.quantity()).isEqualTo(3);
            assertThat(item.lineTotal()).isEqualByComparingTo("70.20");
        });
        assertThat(response.subtotal()).isEqualByComparingTo("70.20");
        assertThat(response.shippingAddress().countryCode()).isEqualTo("IT");
        verify(catalogProductClient).findById(7L);
        verify(orderEventPublisher).orderPlaced(any(PurchaseOrder.class));
    }

    @Test
    void rejectsAggregatedQuantityAboveLimitBeforeCallingCatalog() {
        CreateOrderRequest request = request(List.of(
                new OrderItemRequest(7L, 50),
                new OrderItemRequest(7L, 50)
        ));

        assertThatThrownBy(() -> orderService.create("customer-1", CustomerContact.UNKNOWN, request))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("La quantità massima per prodotto è 99.");

        verify(catalogProductClient, never()).findById(any());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void doesNotPersistAnOrderWhenCatalogProductDoesNotExist() {
        given(catalogProductClient.findById(404L)).willThrow(new CatalogProductNotFoundException(404L));

        assertThatThrownBy(() -> orderService.create(
                "customer-1",
                CustomerContact.UNKNOWN,
                request(List.of(new OrderItemRequest(404L, 1)))
        )).isInstanceOf(CatalogProductNotFoundException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void hidesOrdersOwnedByAnotherCustomer() {
        PurchaseOrder order = purchaseOrder("customer-1");
        given(orderRepository.findById(12L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findById("customer-2", 12L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Ordine con ID 12 non trovato.");
    }

    @Test
    void cancelsOwnedOrderStoresNormalizedReasonAndPublishesEvent() {
        PurchaseOrder order = purchaseOrder("customer-1");
        given(orderRepository.findById(12L)).willReturn(Optional.of(order));
        given(orderRepository.save(any(PurchaseOrder.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancel("customer-1", 12L, "  Changed my mind  ");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Changed my mind");
        assertThat(response.cancelledAt()).isEqualTo(NOW);
        verify(orderEventPublisher).orderCancelled(order);
    }

    @Test
    void refusesCancellationAfterShipment() {
        PurchaseOrder order = purchaseOrder("customer-1");
        order.advanceTo(OrderStatus.CONFIRMED, NOW);
        order.advanceTo(OrderStatus.SHIPPED, NOW);
        given(orderRepository.findById(12L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel("customer-1", 12L, "Changed my mind"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("Transizione ordine non consentita: SHIPPED -> CANCELLED.");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void listsOnlyTheAuthenticatedCustomersOrders() {
        given(orderRepository.findByCustomerId("customer-1", PageRequest.of(0, 20)))
                .willReturn(Page.empty());

        assertThat(orderService.findAllForCustomer("customer-1", PageRequest.of(0, 20))).isEmpty();

        verify(orderRepository).findByCustomerId("customer-1", PageRequest.of(0, 20));
    }

    @Test
    void adminListingFiltersByStatusWhenProvided() {
        given(orderRepository.findByStatus(OrderStatus.CONFIRMED, PageRequest.of(0, 20)))
                .willReturn(Page.empty());

        assertThat(orderService.findAll(OrderStatus.CONFIRMED, PageRequest.of(0, 20))).isEmpty();

        verify(orderRepository).findByStatus(OrderStatus.CONFIRMED, PageRequest.of(0, 20));
    }

    static CreateOrderRequest request(List<OrderItemRequest> items) {
        return new CreateOrderRequest(
                items,
                new ShippingAddressRequest("Ada Lovelace", "Via Roma 1", "Torino", "10100", "it")
        );
    }

    static PurchaseOrder purchaseOrder(String customerId) {
        return PurchaseOrder.create(
                customerId,
                new BigDecimal("23.40"),
                new ShippingAddress("Ada Lovelace", "Via Roma 1", "Torino", "10100", "IT"),
                List.of(new OrderLineSnapshot(7L, "Keyboard", new BigDecimal("23.40"), 1))
        );
    }
}
