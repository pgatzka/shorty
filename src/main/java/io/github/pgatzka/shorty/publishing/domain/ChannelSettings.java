package io.github.pgatzka.shorty.publishing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Set;

/** Channel-level publishing settings (single row). */
@Entity
@Table(name = "channel_settings")
public class ChannelSettings {

    public static final int SINGLETON_ID = 1;
    public static final Set<String> PRIVACY_STATUSES = Set.of("public", "unlisted", "private");

    @Id
    private Integer id;

    @Column(name = "channel_handle", nullable = false)
    private String channelHandle;

    @Column(name = "privacy_status", nullable = false)
    private String privacyStatus;

    protected ChannelSettings() {
        // for JPA
    }

    public ChannelSettings(String channelHandle, String privacyStatus) {
        this.id = SINGLETON_ID;
        update(channelHandle, privacyStatus);
    }

    public static ChannelSettings defaults() {
        return new ChannelSettings("", "public");
    }

    /** Sets both values; the handle gets a leading {@code @} if missing. */
    public final void update(String channelHandle, String privacyStatus) {
        String status = privacyStatus == null ? "" : privacyStatus.strip().toLowerCase(Locale.ROOT);
        if (!PRIVACY_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Privacy must be one of " + PRIVACY_STATUSES);
        }
        this.channelHandle = normalizeHandle(channelHandle);
        this.privacyStatus = status;
    }

    static String normalizeHandle(String handle) {
        String value = handle == null ? "" : handle.strip();
        if (value.isEmpty() || value.startsWith("@")) {
            return value;
        }
        return "@" + value;
    }

    public Integer getId() {
        return id;
    }

    public String getChannelHandle() {
        return channelHandle;
    }

    public String getPrivacyStatus() {
        return privacyStatus;
    }
}
