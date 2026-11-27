package io.github.pgatzka.shorty.publishing.repository;

import io.github.pgatzka.shorty.publishing.domain.ChannelSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/** Channel settings (single row). */
public interface ChannelSettingsRepository extends JpaRepository<ChannelSettings, Integer> {}
