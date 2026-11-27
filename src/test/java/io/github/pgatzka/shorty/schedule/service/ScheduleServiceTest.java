package io.github.pgatzka.shorty.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.schedule.domain.ScheduleSettings;
import io.github.pgatzka.shorty.schedule.repository.ScheduleSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleServiceTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final List<LocalTime> TIMES = List.of(LocalTime.of(9, 0), LocalTime.of(20, 0));

    private final ScheduleSettingsRepository repository = mock(ScheduleSettingsRepository.class);
    private final PublishingService publishing = mock(PublishingService.class);

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ScheduleService at(String localDateTime) {
        Instant now =
                ZonedDateTime.parse(localDateTime + "+01:00[Europe/Berlin]").toInstant();
        return new ScheduleService(repository, publishing, Clock.fixed(now, BERLIN), Duration.ofMinutes(15));
    }

    private static Instant berlin(String localDateTime) {
        return ZonedDateTime.parse(localDateTime + "+01:00[Europe/Berlin]").toInstant();
    }

    @Test
    void dueSlotWithinGrace() {
        ScheduleSettings settings = new ScheduleSettings(true, TIMES, BERLIN);
        ScheduleService service = at("2026-11-27T09:05");

        assertThat(service.dueSlot(settings, berlin("2026-11-27T09:05"))).contains(berlin("2026-11-27T09:00"));
        assertThat(service.dueSlot(settings, berlin("2026-11-27T09:20"))).isEmpty();
        assertThat(service.dueSlot(settings, berlin("2026-11-27T08:59"))).isEmpty();
        assertThat(service.dueSlot(settings, berlin("2026-11-27T00:05"))).isEmpty();

        settings.setLastSlot(berlin("2026-11-27T09:00"));
        assertThat(service.dueSlot(settings, berlin("2026-11-27T09:05"))).isEmpty();
        assertThat(service.dueSlot(settings, berlin("2026-11-27T20:01"))).contains(berlin("2026-11-27T20:00"));
    }

    @Test
    void slotJustBeforeMidnightIsFoundAfterMidnight() {
        ScheduleSettings settings = new ScheduleSettings(true, List.of(LocalTime.of(23, 55)), BERLIN);
        assertThat(at("2026-11-28T00:02").dueSlot(settings, berlin("2026-11-28T00:02")))
                .contains(berlin("2026-11-27T23:55"));
    }

    @Test
    void tickPublishesOncePerSlot() {
        ScheduleSettings settings = new ScheduleSettings(true, TIMES, BERLIN);
        when(repository.findById(1)).thenReturn(Optional.of(settings));
        ScheduleService service = at("2026-11-27T20:00");

        service.tick();
        service.tick();

        verify(publishing).startPublish(PublishTrigger.SCHEDULED);
        assertThat(settings.getLastSlot()).isEqualTo(berlin("2026-11-27T20:00"));
    }

    @Test
    void tickDoesNothingWhenDisabledOrUnset() {
        ScheduleService service = at("2026-11-27T20:00");
        when(repository.findById(1)).thenReturn(Optional.empty());
        service.tick();
        when(repository.findById(1)).thenReturn(Optional.of(new ScheduleSettings(false, TIMES, BERLIN)));
        service.tick();
        verify(publishing, never()).startPublish(any());
    }

    @Test
    void updateDoesNotFireMissedSlots() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        ScheduleService service = at("2026-11-27T09:05");

        ScheduleSettings saved = service.update(true, TIMES, BERLIN);

        assertThat(saved.getLastSlot()).isEqualTo(berlin("2026-11-27T09:05"));
        assertThat(service.dueSlot(saved, berlin("2026-11-27T09:06"))).isEmpty();
    }

    @Test
    void nextRun() {
        when(repository.findById(1)).thenReturn(Optional.of(new ScheduleSettings(true, TIMES, BERLIN)));
        assertThat(at("2026-11-27T10:00").nextRun()).contains(ZonedDateTime.of(2026, 11, 27, 20, 0, 0, 0, BERLIN));
        assertThat(at("2026-11-27T21:00").nextRun()).contains(ZonedDateTime.of(2026, 11, 28, 9, 0, 0, 0, BERLIN));

        when(repository.findById(1)).thenReturn(Optional.of(new ScheduleSettings(false, TIMES, BERLIN)));
        assertThat(at("2026-11-27T10:00").nextRun()).isEmpty();
    }

    @Test
    void defaultsWhenNothingStored() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        ScheduleSettings settings = at("2026-11-27T10:00").settings();
        assertThat(settings.isEnabled()).isFalse();
        assertThat(settings.zone()).isEqualTo(BERLIN);
        assertThat(settings.times()).hasSize(3);
    }

    @Test
    void settingsParseAndValidate() {
        assertThat(ScheduleSettings.parseTimes("20:00, 9:00;09:00  13:30"))
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(13, 30), LocalTime.of(20, 0));
        assertThat(ScheduleSettings.parseTimes(" ")).isEmpty();
        assertThat(ScheduleSettings.parseTimes(null)).isEmpty();
        assertThatThrownBy(() -> ScheduleSettings.parseTimes("25:00")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ScheduleSettings.parseZone(" Europe/Berlin ")).isEqualTo(BERLIN);
        assertThatThrownBy(() -> ScheduleSettings.parseZone("Mars/Base")).isInstanceOf(IllegalArgumentException.class);

        ScheduleSettings settings = new ScheduleSettings(false, List.of(), BERLIN);
        assertThatThrownBy(() -> settings.update(true, List.of(), BERLIN)).isInstanceOf(IllegalArgumentException.class);
        List<LocalTime> tooMany = IntStream.range(0, 25)
                .mapToObj(i -> LocalTime.of(i % 24, i / 24))
                .toList();
        assertThatThrownBy(() -> settings.update(false, tooMany, BERLIN)).isInstanceOf(IllegalArgumentException.class);

        settings.update(true, List.of(LocalTime.of(20, 0, 30), LocalTime.of(9, 0)), BERLIN);
        assertThat(settings.getPostTimes()).isEqualTo("09:00, 20:00");
        assertThat(settings.getId()).isEqualTo(1);
    }
}
