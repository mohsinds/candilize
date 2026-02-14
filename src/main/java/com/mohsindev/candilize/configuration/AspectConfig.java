package com.mohsindev.candilize.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Enables AspectJ-style AOP for the application.
 * Required for @Aspect beans (e.g. LoggingAspect) to be applied.
 */
@Configuration
@EnableAspectJAutoProxy
public class AspectConfig {
}
