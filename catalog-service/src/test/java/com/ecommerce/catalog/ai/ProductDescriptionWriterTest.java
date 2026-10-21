package com.ecommerce.catalog.ai;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.MessageCreateParams;
import com.ecommerce.catalog.product.ProductResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductDescriptionWriterTest {

    private static final ProductResponse COFFEE_MACHINE = ProductResponse.builder()
            .id(36L).name("Macchina da caffè espresso Barista")
            .description("Macchina espresso con macinacaffè integrato e lancia vapore.")
            .price(new BigDecimal("429.00")).build();

    @Test
    void refusesToRunWithoutAnApiKey() {
        ProductDescriptionWriter writer = new ProductDescriptionWriter(properties(""));

        assertThat(writer.configured()).isFalse();
        assertThatThrownBy(() -> writer.suggestDescription(COFFEE_MACHINE)).isInstanceOf(AiNotConfiguredException.class);
    }

    @Test
    void buildsARequestWithTheProductSheetEffortAndFallback() throws Exception {
        ProductDescriptionWriter writer = new ProductDescriptionWriter(properties("test-key"));

        MessageCreateParams params = writer.params(COFFEE_MACHINE);
        String body = ObjectMappers.jsonMapper().writeValueAsString(params._body());

        assertThat(params.model().asString()).isEqualTo("claude-opus-5-5");
        assertThat(body)
                .contains("Macchina da caffè espresso Barista")
                .contains("429,00")
                .contains("\"effort\":\"low\"");
        assertThat(params._additionalBodyProperties()).containsKey("fallbacks");
        assertThat(params._headers().values("anthropic-beta")).contains("server-side-fallback-2026-07-01");
    }

    @Test
    void marksMissingDescriptionExplicitly() {
        ProductResponse withoutDescription = ProductResponse.builder().id(1L).name("Puzzle").price(BigDecimal.TEN).build();

        assertThat(ProductDescriptionWriter.productSheet(withoutDescription)).contains("(nessuna)");
    }

    private static CatalogAiProperties properties(String apiKey) {
        return new CatalogAiProperties(apiKey, "claude-opus-5-5", "low", 4000, Duration.ofSeconds(30));
    }
}
