package io.github.pgatzka.shorty;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pgatzka.shorty.analytics.domain.StatsSnapshot;
import io.github.pgatzka.shorty.analytics.repository.StatsSnapshotRepository;
import io.github.pgatzka.shorty.analytics.service.AnalyticsService;
import io.github.pgatzka.shorty.publishing.domain.ChannelSettings;
import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.repository.UploadRecordRepository;
import io.github.pgatzka.shorty.publishing.service.ChannelSettingsService;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.schedule.domain.ScheduleSettings;
import io.github.pgatzka.shorty.schedule.service.ScheduleService;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.youtube.domain.YouTubeCredential;
import io.github.pgatzka.shorty.youtube.repository.YouTubeCredentialRepository;
import io.github.pgatzka.shorty.youtube.service.TokenCipher;
import io.github.pgatzka.shorty.youtube.service.YouTubeAuthService;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The whole application against a real PostgreSQL: Flyway, JPA mappings, persistence of settings. */
@SpringBootTest(
        properties = {
            "shorty.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "shorty.youtube.client-id=test-client",
            "shorty.youtube.client-secret=test-secret"
        })
@Import(TestcontainersConfiguration.class)
class ShortyApplicationIT {

    @Autowired
    HealthEndpoint health;

    @Autowired
    UploadRecordRepository uploads;

    @Autowired
    PublishingService publishingService;

    @Autowired
    ChannelSettingsService channelSettings;

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    YouTubeCredentialRepository credentials;

    @Autowired
    YouTubeAuthService authService;

    @Autowired
    TokenCipher cipher;

    @Autowired
    StatsSnapshotRepository snapshots;

    @Autowired
    AnalyticsService analyticsService;

    @Test
    void startsHealthy() {
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void storesUploadHistory() {
        UploadRecord upload = new UploadRecord(
                PublishTrigger.MANUAL, SortAlgorithm.SELECTION, VisualStyle.MIRRORED_BARS, Instant.now());
        upload.uploaded("abc", Instant.now());
        uploads.save(upload);

        assertThat(publishingService.recentHistory(5))
                .extracting(UploadRecord::getId)
                .contains(upload.getId());
        assertThat(publishingService.uploadedVideos(5))
                .extracting(UploadRecord::getYoutubeVideoId)
                .contains("abc");
    }

    @Test
    void storesSettings() {
        channelSettings.update("sorty", "private");
        assertThat(channelSettings.current().getChannelHandle()).isEqualTo("@sorty");

        ZoneId zone = ZoneId.of("Europe/Berlin");
        scheduleService.update(true, List.of(LocalTime.of(7, 30), LocalTime.of(18, 0)), zone);
        ScheduleSettings settings = scheduleService.settings();
        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.times()).containsExactly(LocalTime.of(7, 30), LocalTime.of(18, 0));
        assertThat(settings.zone()).isEqualTo(zone);
        assertThat(scheduleService.nextRun()).isPresent();
        assertThat(ChannelSettings.PRIVACY_STATUSES)
                .contains(channelSettings.current().getPrivacyStatus());
    }

    @Test
    void tokenIsStoredEncryptedAndSurvivesReload() {
        credentials.save(new YouTubeCredential(cipher.encrypt("refresh-secret"), Instant.now()));

        YouTubeCredential stored =
                credentials.findById(YouTubeCredential.SINGLETON_ID).orElseThrow();
        assertThat(stored.getEncryptedRefreshToken()).doesNotContain("refresh-secret");
        assertThat(authService.credential().getRefreshToken()).isEqualTo("refresh-secret");

        authService.markRevoked("revoked in test");
        assertThat(authService.status().connected()).isFalse();
        assertThat(authService.status().error()).isEqualTo("revoked in test");
        authService.disconnect();
        assertThat(credentials.count()).isZero();
    }

    @Test
    void latestSnapshotQuery() {
        UploadRecord upload = new UploadRecord(
                PublishTrigger.SCHEDULED, SortAlgorithm.QUICK, VisualStyle.CLASSIC_BARS, Instant.now());
        upload.uploaded("snap1", Instant.now());
        uploads.save(upload);
        snapshots.save(new StatsSnapshot("snap1", Instant.parse("2026-11-01T00:00:00Z"), 1, 0, 0));
        snapshots.save(new StatsSnapshot("snap1", Instant.parse("2026-11-02T00:00:00Z"), 9, 2, 1));

        assertThat(snapshots.findLatest(List.of("snap1")))
                .singleElement()
                .extracting(StatsSnapshot::getViews)
                .isEqualTo(9L);
        assertThat(analyticsService.performance())
                .filteredOn(p -> p.videoId().equals("snap1"))
                .singleElement()
                .satisfies(p -> assertThat(p.views()).isEqualTo(9));
    }
}
