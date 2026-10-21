package com.ecommerce.notification;

import com.ecommerce.notification.messaging.event.OrderCancelledEvent;
import com.ecommerce.notification.messaging.event.OrderConfirmedEvent;
import com.ecommerce.notification.messaging.event.OrderLinePayload;
import com.ecommerce.notification.messaging.event.PaymentRefundedEvent;
import com.ecommerce.notification.messaging.event.ShippingAddressPayload;
import com.ecommerce.notification.messaging.event.ShippingUpdatedEvent;
import com.ecommerce.notification.notification.ContactNotYetKnownException;
import com.ecommerce.notification.notification.NotificationService;
import com.ecommerce.notification.notification.NotificationType;
import com.ecommerce.notification.notification.SentNotification;
import com.ecommerce.notification.notification.SentNotificationRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Database H2 reale; il server SMTP è simulato: si controllano le email "consegnate" al JavaMailSender. */
@SpringBootTest
class NotificationServiceApplicationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SentNotificationRepository sentNotificationRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void realMimeMessages() {
        reset(mailSender);
        given(mailSender.createMimeMessage()).willAnswer(invocation -> new JavaMailSenderImpl().createMimeMessage());
    }

    @Test
    void emailsTheCustomerWhenTheOrderIsConfirmedShippedAndDelivered() throws Exception {
        notificationService.onOrderConfirmed(confirmed(301L, "ada@example.test"));
        notificationService.onShippingUpdated(shipping(301L, "SHIPPED"));
        notificationService.onShippingUpdated(shipping(301L, "DELIVERED"));

        List<MimeMessage> sent = sentMessages(3);
        assertThat(sent).extracting(MimeMessage::getSubject).containsExactly(
                "Ordine #301 confermato",
                "Il tuo ordine #301 è stato spedito",
                "Ordine #301 consegnato");
        assertThat(((InternetAddress) sent.get(0).getAllRecipients()[0]).getAddress()).isEqualTo("ada@example.test");
        assertThat(((InternetAddress) sent.get(0).getFrom()[0]).getAddress()).isEqualTo("noreply@bottega.local");
        assertThat(types(301L)).containsExactly(
                NotificationType.ORDER_CONFIRMED, NotificationType.ORDER_SHIPPED, NotificationType.ORDER_DELIVERED);
    }

    @Test
    void emailsCancellationAndRefund() throws Exception {
        notificationService.onOrderConfirmed(confirmed(302L, "grace@example.test"));
        notificationService.onOrderCancelled(new OrderCancelledEvent(id(), Instant.now(), 302L, "customer-1",
                "grace@example.test", "Grace Hopper", "Ho cambiato idea"));
        notificationService.onPaymentRefunded(new PaymentRefundedEvent(id(), Instant.now(), 302L, 9L,
                new BigDecimal("46.80"), "EUR", "SIM-REFUND-1"));

        assertThat(sentMessages(3)).extracting(MimeMessage::getSubject)
                .containsExactly("Ordine #302 confermato", "Ordine #302 annullato", "Rimborso dell'ordine #302");
    }

    @Test
    void neverSendsTheSameEmailTwice() {
        OrderConfirmedEvent event = confirmed(303L, "ada@example.test");

        notificationService.onOrderConfirmed(event);
        notificationService.onOrderConfirmed(event);
        // Stesso tipo di comunicazione con un evento diverso: bloccato dal registro (order_id, type).
        notificationService.onOrderConfirmed(confirmed(303L, "ada@example.test"));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void retriesWhenShippingArrivesBeforeTheOrderContact() {
        assertThatThrownBy(() -> notificationService.onShippingUpdated(shipping(304L, "SHIPPED")))
                .isInstanceOf(ContactNotYetKnownException.class);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void skipsOrdersWithoutEmailAndStatusesWithoutEmail() {
        notificationService.onOrderConfirmed(confirmed(305L, null));
        notificationService.onShippingUpdated(shipping(305L, "CREATED"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void doesNotRecordTheNotificationWhenSmtpFails() {
        willThrow(new MailSendException("SMTP giù")).given(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> notificationService.onOrderConfirmed(confirmed(306L, "ada@example.test")))
                .isInstanceOf(MailSendException.class);

        // La transazione è annullata: niente registro, e l'evento potrà essere rielaborato al retry.
        assertThat(types(306L)).isEmpty();
    }

    private List<MimeMessage> sentMessages(int expected) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        assertThat(captor.getAllValues()).hasSize(expected);
        return captor.getAllValues();
    }

    private List<NotificationType> types(Long orderId) {
        return sentNotificationRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(SentNotification::getType)
                .toList();
    }

    private static OrderConfirmedEvent confirmed(Long orderId, String email) {
        return new OrderConfirmedEvent(id(), Instant.now(), orderId, "customer-1", email, "Ada Lovelace",
                new ShippingAddressPayload("Ada Lovelace", "Via Roma 1", "Torino", "10100", "IT"),
                List.of(new OrderLinePayload(7L, "Tastiera meccanica", new BigDecimal("23.40"), 2)));
    }

    private static ShippingUpdatedEvent shipping(Long orderId, String status) {
        return new ShippingUpdatedEvent(id(), Instant.now(), orderId, 3L, status, "SIMULATED", "TRK-ABC");
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }
}
