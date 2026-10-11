package com.ecommerce.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfiguration {

    /** Orologio iniettabile: nei test si sostituisce con un Clock fisso. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
