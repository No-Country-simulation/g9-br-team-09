package br.com.g9.energiai.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MlApiPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(MlApiClientConfig.class)
            .withPropertyValues(
                    "ml.api.base-url=http://ml-api.internal:9000",
                    "ml.api.connect-timeout=3s",
                    "ml.api.read-timeout=7s"
            );

    @Test
    void shouldBindConfiguredUrlAndTimeoutsToDedicatedMlClient() {
        contextRunner.run(context -> {
            MlApiProperties properties = context.getBean(MlApiProperties.class);

            assertEquals("http://ml-api.internal:9000", properties.baseUrl().toString());
            assertEquals(Duration.ofSeconds(3), properties.connectTimeout());
            assertEquals(Duration.ofSeconds(7), properties.readTimeout());
            assertNotNull(context.getBean("mlRestClient", RestClient.class));
        });
    }
}
