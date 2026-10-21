package com.ecommerce.assistant.tools;

import com.anthropic.core.JsonValue;
import com.ecommerce.assistant.config.AssistantProperties;
import com.ecommerce.assistant.tools.ShopApiClient.ApiResult;
import com.ecommerce.assistant.tools.ShopTools.ToolOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ShopToolsTest {

    private ShopApiClient shopApiClient;
    private ShopTools tools;

    @BeforeEach
    void setUp() {
        shopApiClient = mock(ShopApiClient.class);
        given(shopApiClient.get(any(), any())).willReturn(new ApiResult(true, "{}"));
        AssistantProperties properties = new AssistantProperties(
                new AssistantProperties.Ai("", "claude-opus-5-5", "low", 16000, 8, Duration.ofSeconds(10)),
                new AssistantProperties.Services("http://catalog:8081/api/v1/catalog", "http://order:8082/api/v1/orders",
                        "http://payment:8084/api/v1/payments", "http://shipping:8085/api/v1/shipments",
                        "http://user:8083/api/v1/users", Duration.ofSeconds(2)),
                new AssistantProperties.Conversation(Duration.ofHours(2), 20)
        );
        tools = new ShopTools(shopApiClient, properties);
    }

    @ParameterizedTest
    @CsvSource({
            "get_product,        product_id, http://catalog:8081/api/v1/catalog/products/7",
            "get_order,          order_id,   http://order:8082/api/v1/orders/7",
            "get_order_payment,  order_id,   http://payment:8084/api/v1/payments/orders/7",
            "get_order_shipment, order_id,   http://shipping:8085/api/v1/shipments/orders/7"
    })
    void callsTheOwningServiceWithTheUserToken(String tool, String field, String expectedUri) {
        ToolOutcome outcome = tools.execute(tool, JsonValue.from(Map.of(field, 7)), "user-token");

        assertThat(outcome.isError()).isFalse();
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(shopApiClient).get(uri.capture(), eq("user-token"));
        assertThat(uri.getValue()).hasToString(expectedUri);
    }

    @Test
    void listsOnlyTheLatestOrdersAndReadsTheProfile() {
        tools.execute("list_my_orders", JsonValue.from(Map.of()), "t");
        tools.execute("get_my_profile", JsonValue.from(Map.of()), "t");

        verify(shopApiClient).get(URI.create("http://order:8082/api/v1/orders?size=10"), "t");
        verify(shopApiClient).get(URI.create("http://user:8083/api/v1/users/me"), "t");
    }

    @Test
    void rejectsUnknownToolsAndMissingParametersWithoutCallingServices() {
        assertThat(tools.execute("delete_everything", JsonValue.from(Map.of()), "t").isError()).isTrue();
        assertThat(tools.execute("search_products", JsonValue.from(Map.of("query", " ")), "t").isError()).isTrue();
        assertThat(tools.execute("get_order", JsonValue.from(Map.of("order_id", "12")), "t").isError()).isTrue();

        verify(shopApiClient, never()).get(any(), any());
    }

    @Test
    void passesServiceErrorsToTheModel() {
        given(shopApiClient.get(any(), any())).willReturn(new ApiResult(false, "Non trovato."));

        ToolOutcome outcome = tools.execute("get_order", JsonValue.from(Map.of("order_id", 99)), "t");

        assertThat(outcome.isError()).isTrue();
        assertThat(outcome.content()).isEqualTo("Non trovato.");
    }

    @Test
    void declaresStrictReadOnlyTools() {
        assertThat(tools.definitions())
                .extracting(tool -> tool.name())
                .containsExactly("search_products", "get_product", "list_my_orders", "get_order",
                        "get_order_payment", "get_order_shipment", "get_my_profile");
        assertThat(tools.definitions()).allSatisfy(tool -> assertThat(tool.strict()).contains(true));
    }
}
