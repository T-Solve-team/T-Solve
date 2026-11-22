package vn.tsolve.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class TriagePropertiesTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(Properties.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TriageProperties.class)
    static class Properties {}

    @Test
    void readsTheThresholdWithDefault060_AC_005_11() {
        runner.run(context -> assertThat(context.getBean(TriageProperties.class).minSimilarity())
                .isEqualTo(0.60));
        for (String value : new String[] {"0", "0.55", "1"}) {
            runner.withPropertyValues("tsolve.triage.min-similarity=" + value).run(context -> assertThat(
                            context.getBean(TriageProperties.class).minSimilarity())
                    .isEqualTo(Double.parseDouble(value)));
        }
    }

    @Test
    void applicationYmlDefaultIs060_AC_005_11() throws IOException {
        // The application.yml placeholder default wins over the record default when the variable is unset.
        var yml = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(yml);

        assertThat(environment.getProperty("tsolve.triage.min-similarity")).isEqualTo("0.60");
    }

    @Test
    void failsToStartOnAThresholdOutside0To1_AC_005_11() {
        for (String value : new String[] {"1.5", "-0.1", "abc", "NaN"}) {
            runner.withPropertyValues("tsolve.triage.min-similarity=" + value)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
