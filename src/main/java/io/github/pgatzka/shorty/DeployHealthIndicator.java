package io.github.pgatzka.shorty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Lets a deployment be marked unhealthy on purpose ({@code shorty.health.force-down=true}) to test
 * the automatic rollback of the deploy job.
 */
@Component("deploy")
class DeployHealthIndicator implements HealthIndicator {

    private final boolean forceDown;

    DeployHealthIndicator(@Value("${shorty.health.force-down:false}") boolean forceDown) {
        this.forceDown = forceDown;
    }

    @Override
    public Health health() {
        if (forceDown) {
            return Health.down()
                    .withDetail("reason", "shorty.health.force-down is set")
                    .build();
        }
        return Health.up().build();
    }
}
