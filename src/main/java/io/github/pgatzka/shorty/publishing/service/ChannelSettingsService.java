package io.github.pgatzka.shorty.publishing.service;

import io.github.pgatzka.shorty.publishing.domain.ChannelSettings;
import io.github.pgatzka.shorty.publishing.repository.ChannelSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and updates the channel settings. */
@Service
public class ChannelSettingsService {

    private final ChannelSettingsRepository repository;

    public ChannelSettingsService(ChannelSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ChannelSettings current() {
        return repository.findById(ChannelSettings.SINGLETON_ID).orElseGet(ChannelSettings::defaults);
    }

    @Transactional
    public ChannelSettings update(String channelHandle, String privacyStatus) {
        ChannelSettings settings =
                repository.findById(ChannelSettings.SINGLETON_ID).orElseGet(ChannelSettings::defaults);
        settings.update(channelHandle, privacyStatus);
        return repository.save(settings);
    }
}
