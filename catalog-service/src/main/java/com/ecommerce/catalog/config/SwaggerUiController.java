package com.ecommerce.catalog.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerUiController {

    @GetMapping("/swagger-ui/index.html")
    public String swaggerUi() {
        return "forward:/swagger-ui-custom.html";
    }

}
