package io.github.pgatzka.shorty.ui.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.Command;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small formatting and UI helpers shared by the views. */
final class Formats {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    private Formats() {}

    static String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    static String seconds(Double seconds) {
        return seconds == null ? "" : String.format(Locale.ROOT, "%.0f s", seconds);
    }

    static void success(String message) {
        Notification.show(message, 5000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    static void error(String message) {
        Notification.show(message, 8000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    /** Runs a command against the UI, directly if this thread already holds the session lock. */
    static void access(UI ui, Command command) {
        if (ui.getSession() != null && ui.getSession().hasLock()) {
            command.execute();
        } else {
            ui.access(command);
        }
    }
}
