package com.mohsindev.candilize.auth.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Candilize Auth API")
                        .description("Auth and Config microservice - users, JWT, pairs, intervals, internal config")
                        .version("1.0"));
    }
}
