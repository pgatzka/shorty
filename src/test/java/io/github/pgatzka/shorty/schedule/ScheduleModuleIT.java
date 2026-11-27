package io.github.pgatzka.shorty.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.github.pgatzka.shorty.TestcontainersConfiguration;
import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.schedule.repository.ScheduleSettingsRepository;
import io.github.pgatzka.shorty.schedule.service.ScheduleService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The schedule module on its own (Spring Modulith module test); publishing is mocked. */
@ApplicationModuleTest
@Import({TestcontainersConfiguration.class, ScheduleModuleIT.FixedClock.class})
class ScheduleModuleIT {

    static final Instant NOW = Instant.parse("2026-11-27T19:00:30Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @MockitoBean
    PublishingService publishingService;

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    ScheduleSettingsRepository repository;

    @Test
    void publishesWhenASlotIsDue() {
        scheduleService.update(true, List.of(LocalTime.of(19, 0)), ZoneId.of("UTC"));
        // Saving the schedule skips slots that already passed; pretend it was saved earlier.
        var settings = repository.findById(1).orElseThrow();
        settings.setLastSlot(Instant.parse("2026-11-27T18:00:00Z"));
        repository.save(settings);

        scheduleService.tick();

        verify(publishingService).startPublish(PublishTrigger.SCHEDULED);
        assertThat(repository.findById(1).orElseThrow().getLastSlot()).isEqualTo(Instant.parse("2026-11-27T19:00:00Z"));
    }
}
