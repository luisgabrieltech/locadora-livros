package br.com.locadora.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OperatorPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void rejectsMissingCredentials() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsBlankPassword() {
        contextRunner.withPropertyValues("app.operator.username=operator", "app.operator.password= ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsExternallyConfiguredCredentialsWithoutExposingThemInToString() {
        contextRunner.withPropertyValues(
                        "app.operator.username=test-operator", "app.operator.password=test-only-password")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OperatorProperties.class).toString())
                            .doesNotContain("test-operator", "test-only-password");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OperatorProperties.class)
    static class PropertiesConfiguration {}
}
