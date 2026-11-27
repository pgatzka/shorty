package io.github.pgatzka.shorty.schedule.repository;

import io.github.pgatzka.shorty.schedule.domain.ScheduleSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/** Schedule settings (single row). */
public interface ScheduleSettingsRepository extends JpaRepository<ScheduleSettings, Integer> {}
