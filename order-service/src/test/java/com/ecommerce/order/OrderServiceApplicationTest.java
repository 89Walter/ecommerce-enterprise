package com.ecommerce.order;

import com.ecommerce.order.catalog.CatalogProductClient;
import com.ecommerce.order.catalog.CatalogProductResponse;
import com.ecommerce.order.messaging.EventTopology;
import com.ecommerce.order.messaging.OutboxEvent;
import com.ecommerce.order.messaging.OutboxEventRepository;
import com.ecommerce.order.order.CreateOrderRequest;
import com.ecommerce.order.order.CustomerContact;
import com.ecommerce.order.order.OrderItemRequest;
import com.ecommerce.order.order.OrderResponse;
import com.ecommerce.order.order.OrderService;
import com.ecommerce.order.order.ShippingAddressRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class OrderServiceApplicationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private CatalogProductClient catalogProductClient;

    @Test
    void storesOrderPlacedEventInOutboxTogetherWithTheOrder() {
        given(catalogProductClient.findById(7L))
                .willReturn(new CatalogProductResponse(7L, "Keyboard", new BigDecimal("23.40")));

        OrderResponse order = orderService.create("customer-1", new CustomerContact("ada@example.test", "Ada Lovelace"), new CreateOrderRequest(
                List.of(new OrderItemRequest(7L, 2)),
                new ShippingAddressRequest("Ada Lovelace", "Via Roma 1", "Torino", "10100", "IT")
        ));

        List<OutboxEvent> events = outboxEventRepository.findByAggregateIdOrderByIdAsc(String.valueOf(order.id()));
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getRoutingKey()).isEqualTo(EventTopology.ORDER_PLACED);
            assertThat(event.getPublishedAt()).isNull();
            assertThat(event.getPayload())
                    .contains("\"orderId\":" + order.id())
                    .contains("\"amount\":46.80")
                    .contains("\"currency\":\"EUR\"")
                    .contains("\"customerEmail\":\"ada@example.test\"")
                    .contains("\"customerName\":\"Ada Lovelace\"");
        });
    }
}
