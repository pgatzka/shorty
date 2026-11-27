package io.github.pgatzka.shorty;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class DeployHealthIndicatorTest {

    @Test
    void upByDefault() {
        assertThat(new DeployHealthIndicator(false).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void canBeForcedDownToTestRollback() {
        assertThat(new DeployHealthIndicator(true).health().getStatus()).isEqualTo(Status.DOWN);
    }
}
