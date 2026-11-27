package io.github.pgatzka.shorty.ui.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.pgatzka.shorty.publishing.domain.ChannelSettings;
import io.github.pgatzka.shorty.publishing.service.ChannelSettingsService;
import io.github.pgatzka.shorty.schedule.domain.ScheduleSettings;
import io.github.pgatzka.shorty.schedule.service.ScheduleService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Schedule and channel settings. */
@Route(value = "schedule", layout = MainLayout.class)
@PageTitle("Schedule | Shorty")
public class ScheduleView extends VerticalLayout {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter NEXT_RUN =
            DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm (VV)", Locale.ROOT);

    private final transient ScheduleService scheduleService;
    private final transient ChannelSettingsService channelSettingsService;
    private final Checkbox enabled = new Checkbox("Post automatically");
    private final TextField times = new TextField("Times of day");
    private final ComboBox<String> zone = new ComboBox<>("Time zone");
    private final Span nextRun = new Span();
    private final Button saveSchedule = new Button("Save schedule");
    private final TextField handle = new TextField("Channel handle");
    private final Select<String> privacy = new Select<>();
    private final Button saveChannel = new Button("Save channel");

    public ScheduleView(ScheduleService scheduleService, ChannelSettingsService channelSettingsService) {
        this.scheduleService = scheduleService;
        this.channelSettingsService = channelSettingsService;

        times.setHelperText("24-hour times, comma-separated, e.g. 09:00, 15:00, 20:00");
        times.setWidth("24em");
        zone.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        zone.setWidth("24em");
        saveSchedule.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveSchedule.addClickListener(event -> saveSchedule());

        handle.setHelperText("Shown in the video and description, e.g. @mychannel");
        privacy.setLabel("Visibility of uploads");
        privacy.setItems(ChannelSettings.PRIVACY_STATUSES.stream().sorted().toList());
        saveChannel.addClickListener(event -> saveChannel());

        add(
                new H2("Schedule"),
                enabled,
                times,
                zone,
                nextRun,
                saveSchedule,
                new H3("Channel"),
                handle,
                privacy,
                saveChannel);
        load();
    }

    private void load() {
        ScheduleSettings settings = scheduleService.settings();
        enabled.setValue(settings.isEnabled());
        times.setValue(settings.getPostTimes());
        zone.setValue(settings.zone().getId());
        ChannelSettings channel = channelSettingsService.current();
        handle.setValue(channel.getChannelHandle());
        privacy.setValue(channel.getPrivacyStatus());
        showNextRun();
    }

    void saveSchedule() {
        try {
            ZoneId zoneId = ScheduleSettings.parseZone(zone.getValue() == null ? "" : zone.getValue());
            scheduleService.update(enabled.getValue(), ScheduleSettings.parseTimes(times.getValue()), zoneId);
            Formats.success("Schedule saved");
            load();
        } catch (IllegalArgumentException e) {
            Formats.error(e.getMessage());
        }
    }

    void saveChannel() {
        try {
            ChannelSettings saved = channelSettingsService.update(handle.getValue(), privacy.getValue());
            handle.setValue(saved.getChannelHandle());
            Formats.success("Channel settings saved");
        } catch (IllegalArgumentException e) {
            Formats.error(e.getMessage());
        }
    }

    private void showNextRun() {
        nextRun.setText(scheduleService
                .nextRun()
                .map(next -> "Next post: " + NEXT_RUN.format(next))
                .orElse("Automatic posting is off."));
    }

    Checkbox enabledField() {
        return enabled;
    }

    TextField timesField() {
        return times;
    }

    ComboBox<String> zoneField() {
        return zone;
    }

    Span nextRun() {
        return nextRun;
    }

    TextField handleField() {
        return handle;
    }

    Select<String> privacyField() {
        return privacy;
    }

    Button saveScheduleButton() {
        return saveSchedule;
    }

    Button saveChannelButton() {
        return saveChannel;
    }
}
