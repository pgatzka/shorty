package io.github.pgatzka.shorty.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.pgatzka.shorty.publishing.domain.ChannelSettings;
import io.github.pgatzka.shorty.publishing.service.ChannelSettingsService;
import io.github.pgatzka.shorty.schedule.domain.ScheduleSettings;
import io.github.pgatzka.shorty.schedule.service.ScheduleService;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ScheduleViewTest extends ViewTestBase {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @MockitoBean
    ScheduleService scheduleService;

    @MockitoBean
    ChannelSettingsService channelSettingsService;

    @BeforeEach
    void setUp() {
        when(scheduleService.settings()).thenReturn(ScheduleSettings.defaults(BERLIN));
        when(channelSettingsService.current()).thenReturn(new ChannelSettings("@me", "public"));
    }

    @Test
    void showsCurrentSettings() {
        when(scheduleService.nextRun()).thenReturn(Optional.of(ZonedDateTime.of(2026, 11, 27, 20, 0, 0, 0, BERLIN)));
        ScheduleView view = navigate(ScheduleView.class);

        assertThat(view.enabledField().getValue()).isFalse();
        assertThat(view.timesField().getValue()).isEqualTo("09:00, 15:00, 20:00");
        assertThat(view.zoneField().getValue()).isEqualTo("Europe/Berlin");
        assertThat(view.nextRun().getText()).isEqualTo("Next post: Fri 2026-11-27 20:00 (Europe/Berlin)");
        assertThat(view.handleField().getValue()).isEqualTo("@me");
        assertThat(view.privacyField().getValue()).isEqualTo("public");
    }

    @Test
    void savesSchedule() {
        when(scheduleService.nextRun()).thenReturn(Optional.empty());
        ScheduleView view = navigate(ScheduleView.class);

        test(view.enabledField()).click();
        test(view.timesField()).setValue("08:30, 19:00");
        test(view.saveScheduleButton()).click();

        verify(scheduleService).update(true, List.of(LocalTime.of(8, 30), LocalTime.of(19, 0)), BERLIN);
        assertThat(view.nextRun().getText()).isEqualTo("Automatic posting is off.");
        assertThat(notifications()).contains("Schedule saved");
    }

    @Test
    void rejectsInvalidTimes() {
        when(scheduleService.nextRun()).thenReturn(Optional.empty());
        ScheduleView view = navigate(ScheduleView.class);

        test(view.timesField()).setValue("late");
        test(view.saveScheduleButton()).click();

        verify(scheduleService, never()).update(anyBoolean(), anyList(), any());
        assertThat(notifications()).contains("Times must look like 09:00, 15:30");
    }

    @Test
    void savesChannel() {
        when(scheduleService.nextRun()).thenReturn(Optional.empty());
        when(channelSettingsService.update("other", "unlisted")).thenReturn(new ChannelSettings("other", "unlisted"));
        ScheduleView view = navigate(ScheduleView.class);

        test(view.handleField()).setValue("other");
        test(view.privacyField()).selectItem("unlisted");
        test(view.saveChannelButton()).click();

        assertThat(view.handleField().getValue()).isEqualTo("@other");
        assertThat(notifications()).contains("Channel settings saved");
    }

    @Test
    void channelErrorsAreShown() {
        when(scheduleService.nextRun()).thenReturn(Optional.empty());
        when(channelSettingsService.update(any(), any())).thenThrow(new IllegalArgumentException("Privacy must be"));
        ScheduleView view = navigate(ScheduleView.class);

        test(view.saveChannelButton()).click();

        assertThat(notifications()).contains("Privacy must be");
    }
}
