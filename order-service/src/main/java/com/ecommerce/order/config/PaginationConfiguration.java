package com.ecommerce.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class PaginationConfiguration {

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer pageableSizeLimit() {
        return resolver -> resolver.setMaxPageSize(100);
    }
}
