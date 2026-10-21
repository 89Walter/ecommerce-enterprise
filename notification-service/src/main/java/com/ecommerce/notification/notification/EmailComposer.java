package com.ecommerce.notification.notification;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.messaging.event.OrderLinePayload;
import com.ecommerce.notification.messaging.event.ShippingAddressPayload;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Testi delle email, in due versioni: testo semplice e HTML. Ogni dato che arriva dall'esterno
 * (nomi, prodotti, indirizzi, motivi) viene "escapato" prima di entrare nell'HTML.
 */
@Component
public class EmailComposer {

    private final NotificationProperties properties;

    public EmailComposer(NotificationProperties properties) {
        this.properties = properties;
    }

    public EmailMessage orderConfirmed(Long orderId, String name, ShippingAddressPayload address, List<OrderLinePayload> items) {
        BigDecimal total = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder text = new StringBuilder();
        StringBuilder rows = new StringBuilder();
        for (OrderLinePayload item : items) {
            BigDecimal lineTotal = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
            text.append("- ").append(item.quantity()).append(" x ").append(item.productName())
                    .append(": ").append(euro(lineTotal)).append('\n');
            rows.append("<tr><td>").append(item.quantity()).append(" &times; ").append(escape(item.productName()))
                    .append("</td><td style=\"text-align:right\">").append(euro(lineTotal)).append("</td></tr>");
        }
        String shipTo = address == null ? "" : String.join(", ",
                address.recipientName(), address.addressLine(), address.postalCode() + " " + address.city(), address.countryCode());

        return message(
                "Ordine #" + orderId + " confermato",
                greeting(name) + "\n\nabbiamo ricevuto il pagamento: il tuo ordine #" + orderId + " è confermato "
                        + "e lo stiamo preparando.\n\n" + text + "\nTotale: " + euro(total)
                        + (shipTo.isEmpty() ? "" : "\n\nSpedizione a: " + shipTo),
                "<p>" + escape(greeting(name)) + "</p>"
                        + "<p>abbiamo ricevuto il pagamento: il tuo ordine <strong>#" + orderId + "</strong> è confermato "
                        + "e lo stiamo preparando.</p>"
                        + "<table style=\"width:100%;border-collapse:collapse\">" + rows
                        + "<tr><td style=\"padding-top:8px\"><strong>Totale</strong></td>"
                        + "<td style=\"text-align:right;padding-top:8px\"><strong>" + euro(total) + "</strong></td></tr></table>"
                        + (shipTo.isEmpty() ? "" : "<p>Spedizione a: " + escape(shipTo) + "</p>"),
                orderId
        );
    }

    public EmailMessage orderCancelled(Long orderId, String name, String reason) {
        String why = reason == null || reason.isBlank() ? "" : "Motivo: " + reason;
        return message(
                "Ordine #" + orderId + " annullato",
                greeting(name) + "\n\nil tuo ordine #" + orderId + " è stato annullato.\n" + why
                        + "\n\nSe era già stato addebitato, riceverai una conferma del rimborso a parte.",
                "<p>" + escape(greeting(name)) + "</p>"
                        + "<p>il tuo ordine <strong>#" + orderId + "</strong> è stato annullato.</p>"
                        + (why.isEmpty() ? "" : "<p>" + escape(why) + "</p>")
                        + "<p>Se era già stato addebitato, riceverai una conferma del rimborso a parte.</p>",
                orderId
        );
    }

    public EmailMessage paymentRefunded(Long orderId, String name, BigDecimal amount) {
        return message(
                "Rimborso dell'ordine #" + orderId,
                greeting(name) + "\n\nabbiamo rimborsato " + euro(amount) + " per l'ordine #" + orderId
                        + ". L'accredito può richiedere alcuni giorni lavorativi, a seconda della tua banca.",
                "<p>" + escape(greeting(name)) + "</p>"
                        + "<p>abbiamo rimborsato <strong>" + euro(amount) + "</strong> per l'ordine <strong>#" + orderId
                        + "</strong>. L'accredito può richiedere alcuni giorni lavorativi, a seconda della tua banca.</p>",
                orderId
        );
    }

    public EmailMessage orderShipped(Long orderId, String name, String carrier, String trackingCode) {
        String tracking = "Corriere: " + nullToDash(carrier) + " - Codice di tracking: " + nullToDash(trackingCode);
        return message(
                "Il tuo ordine #" + orderId + " è stato spedito",
                greeting(name) + "\n\nil tuo ordine #" + orderId + " è partito.\n" + tracking,
                "<p>" + escape(greeting(name)) + "</p>"
                        + "<p>il tuo ordine <strong>#" + orderId + "</strong> è partito.</p>"
                        + "<p>" + escape(tracking) + "</p>",
                orderId
        );
    }

    public EmailMessage orderDelivered(Long orderId, String name) {
        return message(
                "Ordine #" + orderId + " consegnato",
                greeting(name) + "\n\nil tuo ordine #" + orderId + " risulta consegnato. Grazie per aver acquistato da noi!",
                "<p>" + escape(greeting(name)) + "</p>"
                        + "<p>il tuo ordine <strong>#" + orderId + "</strong> risulta consegnato. "
                        + "Grazie per aver acquistato da noi!</p>",
                orderId
        );
    }

    private EmailMessage message(String subject, String text, String htmlBody, Long orderId) {
        String orderUrl = properties.shopUrl() + "/orders/" + orderId;
        String fullText = text + "\n\nVedi l'ordine: " + orderUrl + "\n\n-- \n" + properties.fromName();
        String html = """
                <!doctype html>
                <html lang="it"><body style="margin:0;background:#f6f5f2;font-family:Arial,sans-serif;color:#1d1b18">
                <div style="max-width:560px;margin:24px auto;background:#ffffff;border-radius:12px;padding:24px">
                <h2 style="margin-top:0;color:#2f5d50">%s</h2>
                %s
                <p><a href="%s" style="display:inline-block;background:#2f5d50;color:#ffffff;padding:10px 16px;border-radius:8px;text-decoration:none">Vedi l'ordine</a></p>
                <p style="color:#6b665e;font-size:12px">%s - email automatica, non rispondere a questo indirizzo.</p>
                </div></body></html>
                """.formatted(escape(subject), htmlBody, escape(orderUrl), escape(properties.fromName()));
        return new EmailMessage(subject, fullText, html);
    }

    private static String greeting(String name) {
        return name == null || name.isBlank() ? "Ciao," : "Ciao " + name + ",";
    }

    private static String euro(BigDecimal amount) {
        return amount == null ? "-" : NumberFormat.getCurrencyInstance(Locale.ITALY).format(amount);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    public record EmailMessage(String subject, String text, String html) {}
}
