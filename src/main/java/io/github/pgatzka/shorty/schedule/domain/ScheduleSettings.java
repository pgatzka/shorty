package io.github.pgatzka.shorty.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** When to post automatically (single row). */
@Entity
@Table(name = "schedule_settings")
public class ScheduleSettings {

    public static final int SINGLETON_ID = 1;

    /** Far below YouTube's default of 100 uploads per day. */
    public static final int MAX_POSTS_PER_DAY = 24;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    @Id
    private Integer id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "post_times", nullable = false)
    private String postTimes;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "last_slot")
    private Instant lastSlot;

    protected ScheduleSettings() {
        // for JPA
    }

    public ScheduleSettings(boolean enabled, List<LocalTime> times, ZoneId zone) {
        this.id = SINGLETON_ID;
        update(enabled, times, zone);
    }

    public static ScheduleSettings defaults(ZoneId zone) {
        return new ScheduleSettings(false, List.of(LocalTime.of(9, 0), LocalTime.of(15, 0), LocalTime.of(20, 0)), zone);
    }

    public final void update(boolean enabled, List<LocalTime> times, ZoneId zone) {
        if (times.size() > MAX_POSTS_PER_DAY) {
            throw new IllegalArgumentException("At most " + MAX_POSTS_PER_DAY + " posts per day");
        }
        if (enabled && times.isEmpty()) {
            throw new IllegalArgumentException("Add at least one time to enable the schedule");
        }
        this.enabled = enabled;
        this.postTimes = times.stream()
                .map(time -> time.withSecond(0).withNano(0))
                .distinct()
                .sorted()
                .map(TIME::format)
                .collect(Collectors.joining(","));
        this.zoneId = zone.getId();
    }

    /** Parses "09:00, 15:30" style input. */
    public static List<LocalTime> parseTimes(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(text.split("[,;\\s]+"))
                    .filter(part -> !part.isBlank())
                    .map(part -> LocalTime.parse(part.length() == 4 ? "0" + part : part, TIME))
                    .distinct()
                    .sorted()
                    .toList();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Times must look like 09:00, 15:30", e);
        }
    }

    public static ZoneId parseZone(String zone) {
        try {
            return ZoneId.of(zone.strip());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown time zone: " + zone, e);
        }
    }

    public Integer getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<LocalTime> times() {
        return parseTimes(postTimes);
    }

    public String getPostTimes() {
        return postTimes.replace(",", ", ");
    }

    public ZoneId zone() {
        return ZoneId.of(zoneId);
    }

    public Instant getLastSlot() {
        return lastSlot;
    }

    public void setLastSlot(Instant lastSlot) {
        this.lastSlot = lastSlot;
    }
}
