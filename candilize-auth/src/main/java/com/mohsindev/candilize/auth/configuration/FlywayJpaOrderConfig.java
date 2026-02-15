package com.mohsindev.candilize.auth.configuration;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures Flyway runs before JPA schema validation by making EntityManagerFactory
 * depend on the bean that runs Flyway migration (flywayInitializer).
 */
@Configuration
public class FlywayJpaOrderConfig implements BeanFactoryPostProcessor {

    /** Bean that runs Flyway.migrate() at startup (from spring-boot-starter-flyway). */
    private static final String FLYWAY_INITIALIZER_BEAN = "flywayInitializer";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            return;
        }
        if (!registry.containsBeanDefinition(FLYWAY_INITIALIZER_BEAN)) {
            return;
        }
        if (!registry.containsBeanDefinition("entityManagerFactory")) {
            return;
        }
        BeanDefinition bd = registry.getBeanDefinition("entityManagerFactory");
        String[] dependsOn = bd.getDependsOn();
        if (dependsOn == null) {
            bd.setDependsOn(FLYWAY_INITIALIZER_BEAN);
        } else {
            for (String d : dependsOn) {
                if (FLYWAY_INITIALIZER_BEAN.equals(d)) return;
            }
            String[] updated = new String[dependsOn.length + 1];
            updated[0] = FLYWAY_INITIALIZER_BEAN;
            System.arraycopy(dependsOn, 0, updated, 1, dependsOn.length);
            bd.setDependsOn(updated);
        }
    }
}
