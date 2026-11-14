package vn.tsolve.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TriagePropertiesTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(Properties.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TriageProperties.class)
    static class Properties {}

    @Test
    void readsTheThresholdWithDefault07_AC_005_11() {
        runner.run(context -> assertThat(context.getBean(TriageProperties.class).minSimilarity())
                .isEqualTo(0.7));
        for (String value : new String[] {"0", "0.55", "1"}) {
            runner.withPropertyValues("tsolve.triage.min-similarity=" + value).run(context -> assertThat(
                            context.getBean(TriageProperties.class).minSimilarity())
                    .isEqualTo(Double.parseDouble(value)));
        }
    }

    @Test
    void failsToStartOnAThresholdOutside0To1_AC_005_11() {
        for (String value : new String[] {"1.5", "-0.1", "abc", "NaN"}) {
            runner.withPropertyValues("tsolve.triage.min-similarity=" + value)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
