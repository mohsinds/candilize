package com.mohsindev.candilize.auth.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration for JSON serialization/deserialization.
 * Provides a shared ObjectMapper bean used by REST controllers, AuthEntryPoint, and InternalApiKeyFilter.
 * - JavaTimeModule: enables Java 8 time types (Instant, LocalDateTime, etc.)
 * - WRITE_DATES_AS_TIMESTAMPS disabled: outputs ISO-8601 strings instead of numeric timestamps
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
