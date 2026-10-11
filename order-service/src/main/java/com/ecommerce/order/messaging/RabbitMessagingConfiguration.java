package com.ecommerce.order.messaging;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableScheduling
public class RabbitMessagingConfiguration {

    /** Exchange, code e dead-letter queue di tutto il sistema (dichiarazione idempotente). */
    @Bean
    Declarables eventTopology() {
        return EventTopology.declarables();
    }

    /**
     * I messaggi sono JSON senza nomi di classi Java: il tipo viene dedotto dal parametro
     * del metodo @RabbitListener, così ogni servizio definisce i propri record degli eventi.
     */
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
