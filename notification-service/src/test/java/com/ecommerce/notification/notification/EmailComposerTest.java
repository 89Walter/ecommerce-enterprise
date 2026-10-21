package com.ecommerce.notification.notification;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.messaging.event.OrderLinePayload;
import com.ecommerce.notification.messaging.event.ShippingAddressPayload;
import com.ecommerce.notification.notification.EmailComposer.EmailMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailComposerTest {

    private final EmailComposer composer =
            new EmailComposer(new NotificationProperties("noreply@bottega.local", "Bottega", "http://localhost:3000"));

    @Test
    void orderConfirmationListsItemsTotalAddressAndLink() {
        EmailMessage email = composer.orderConfirmed(12L, "Ada",
                new ShippingAddressPayload("Ada Lovelace", "Via Roma 1", "Torino", "10100", "IT"),
                List.of(new OrderLinePayload(7L, "Tastiera meccanica", new BigDecimal("23.40"), 2)));

        assertThat(email.subject()).isEqualTo("Ordine #12 confermato");
        assertThat(email.text())
                .contains("Ciao Ada,")
                .contains("2 x Tastiera meccanica")
                .contains("Totale: 46,80")
                .contains("Via Roma 1, 10100 Torino, IT")
                .contains("http://localhost:3000/orders/12");
        assertThat(email.html()).contains("href=\"http://localhost:3000/orders/12\"");
    }

    @Test
    void escapesUserProvidedTextInHtml() {
        EmailMessage email = composer.orderCancelled(13L, "<script>alert(1)</script>", "<b>motivo</b>");

        assertThat(email.html())
                .doesNotContain("<script>")
                .doesNotContain("<b>motivo</b>")
                .contains("&lt;script&gt;");
    }

    @Test
    void greetsGenericallyWithoutAName() {
        assertThat(composer.orderDelivered(14L, null).text()).startsWith("Ciao,");
    }
}
