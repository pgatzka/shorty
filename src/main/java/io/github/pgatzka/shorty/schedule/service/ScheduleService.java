package io.github.pgatzka.shorty.schedule.service;

import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.schedule.domain.ScheduleSettings;
import io.github.pgatzka.shorty.schedule.repository.ScheduleSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores the schedule and starts a publish when a slot is due. */
@Service
public class ScheduleService {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleSettingsRepository repository;
    private final PublishingService publishingService;
    private final Clock clock;
    private final Duration grace;

    public ScheduleService(
            ScheduleSettingsRepository repository,
            PublishingService publishingService,
            Clock clock,
            @Value("${shorty.schedule.grace:PT15M}") Duration grace) {
        this.repository = repository;
        this.publishingService = publishingService;
        this.clock = clock;
        this.grace = grace;
    }

    @Transactional(readOnly = true)
    public ScheduleSettings settings() {
        return repository
                .findById(ScheduleSettings.SINGLETON_ID)
                .orElseGet(() -> ScheduleSettings.defaults(clock.getZone()));
    }

    @Transactional
    public ScheduleSettings update(boolean enabled, List<LocalTime> times, ZoneId zone) {
        ScheduleSettings settings =
                repository.findById(ScheduleSettings.SINGLETON_ID).orElseGet(() -> ScheduleSettings.defaults(zone));
        settings.update(enabled, times, zone);
        // Do not fire slots that passed before the schedule was (re)configured.
        settings.setLastSlot(clock.instant());
        return repository.save(settings);
    }

    /** The next time a video will be posted, if the schedule is enabled. */
    public Optional<ZonedDateTime> nextRun() {
        ScheduleSettings settings = settings();
        if (!settings.isEnabled() || settings.times().isEmpty()) {
            return Optional.empty();
        }
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(settings.zone()));
        LocalDate today = now.toLocalDate();
        for (int day = 0; day <= 1; day++) {
            for (LocalTime time : settings.times()) {
                ZonedDateTime candidate = ZonedDateTime.of(today.plusDays(day), time, settings.zone());
                if (candidate.isAfter(now)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /** Runs periodically; posts once per due slot. Slots missed by more than the grace period are skipped. */
    @Scheduled(fixedDelayString = "${shorty.schedule.check-interval:PT30S}", initialDelayString = "PT20S")
    @Transactional
    public void tick() {
        Optional<ScheduleSettings> stored = repository.findById(ScheduleSettings.SINGLETON_ID);
        if (stored.isEmpty() || !stored.get().isEnabled()) {
            return;
        }
        ScheduleSettings settings = stored.get();
        dueSlot(settings, clock.instant()).ifPresent(slot -> {
            settings.setLastSlot(slot);
            repository.save(settings);
            LOG.info("Scheduled slot {} is due, publishing", slot);
            publishingService.startPublish(PublishTrigger.SCHEDULED);
        });
    }

    /** The latest slot at or before {@code now} that has not fired yet and is within the grace period. */
    Optional<Instant> dueSlot(ScheduleSettings settings, Instant now) {
        ZonedDateTime localNow = now.atZone(settings.zone());
        Instant latest = null;
        for (int day = -1; day <= 0; day++) {
            for (LocalTime time : settings.times()) {
                Instant slot = ZonedDateTime.of(localNow.toLocalDate().plusDays(day), time, settings.zone())
                        .toInstant();
                if (!slot.isAfter(now) && (latest == null || slot.isAfter(latest))) {
                    latest = slot;
                }
            }
        }
        if (latest == null || latest.isBefore(now.minus(grace))) {
            return Optional.empty();
        }
        Instant lastSlot = settings.getLastSlot();
        if (lastSlot != null && !latest.isAfter(lastSlot)) {
            return Optional.empty();
        }
        return Optional.of(latest);
    }
}
