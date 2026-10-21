package com.ecommerce.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param fromAddress mittente delle email (es. noreply@bottega.local)
 * @param fromName    nome visualizzato del mittente
 * @param shopUrl     indirizzo del sito, per i link "vedi il tuo ordine"
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String fromAddress, String fromName, String shopUrl) {}
