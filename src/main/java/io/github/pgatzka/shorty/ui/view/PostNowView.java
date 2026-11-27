package io.github.pgatzka.shorty.ui.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.youtube.domain.ConnectionStatus;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import io.github.pgatzka.shorty.youtube.service.YouTubeAuthService;
import java.util.List;
import java.util.Map;

/** Connect YouTube and publish a video immediately. */
@Route(value = "post", layout = MainLayout.class)
@PageTitle("Post now | Shorty")
public class PostNowView extends VerticalLayout implements BeforeEnterObserver {

    private static final long serialVersionUID = 1L;

    private final transient PublishingService publishingService;
    private final transient YouTubeAuthService authService;
    private final Span connection = new Span();
    private final Button connect = new Button("Connect YouTube");
    private final TextField pasted = new TextField("Redirect URL or code");
    private final Button finish = new Button("Finish connecting");
    private final Button disconnect = new Button("Disconnect");
    private final Button post = new Button("Post now");

    public PostNowView(PublishingService publishingService, YouTubeAuthService authService) {
        this.publishingService = publishingService;
        this.authService = authService;

        connect.addClickListener(event -> openConsent());
        pasted.setWidth("40em");
        pasted.setHelperText("If the page after approving does not load (not opened via localhost), copy its full "
                + "URL from the address bar and paste it here.");
        finish.addClickListener(event -> finishConnecting());
        disconnect.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        disconnect.addClickListener(event -> {
            authService.disconnect();
            refresh();
        });
        post.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        post.addClickListener(event -> postNow());

        HorizontalLayout pasteRow = new HorizontalLayout(pasted, finish);
        pasteRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        add(
                new H2("Post now"),
                new H3("YouTube connection"),
                connection,
                new HorizontalLayout(connect, disconnect),
                pasteRow,
                new H3("Publish"),
                new Paragraph("Renders the next algorithm and style in rotation and uploads it as a Short."),
                post,
                new RouterLink("Open upload history", HistoryView.class));
        refresh();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Map<String, List<String>> parameters =
                event.getLocation().getQueryParameters().getParameters();
        if (parameters.containsKey("connected")) {
            Formats.success("YouTube connected");
        }
        List<String> errors = parameters.getOrDefault("oauthError", List.of());
        if (!errors.isEmpty()) {
            Formats.error("Connecting failed: " + errors.getFirst());
        }
        refresh();
    }

    void openConsent() {
        try {
            String url = authService.authorizationUrl();
            getUI().ifPresent(ui -> ui.getPage().open(url, "_blank"));
        } catch (YouTubeException e) {
            Formats.error(e.getMessage());
        }
    }

    void finishConnecting() {
        try {
            authService.completeAuthorization(pasted.getValue());
            pasted.clear();
            Formats.success("YouTube connected");
        } catch (YouTubeException e) {
            Formats.error(e.getMessage());
        }
        refresh();
    }

    void postNow() {
        UploadRecord upload = publishingService.startPublish(PublishTrigger.MANUAL);
        Formats.success(
                "Queued #" + upload.getId() + ": " + upload.getAlgorithm().displayName() + ", "
                        + upload.getStyle().displayName() + ". Follow it in the history.");
    }

    private void refresh() {
        ConnectionStatus status = authService.status();
        connection.setText(describe(status));
        connect.setEnabled(status.configured());
        finish.setEnabled(status.configured());
        pasted.setEnabled(status.configured());
        disconnect.setEnabled(status.connectedAt() != null);
        post.setEnabled(status.configured() && status.connected());
    }

    static String describe(ConnectionStatus status) {
        if (!status.configured()) {
            return "Not configured: set SHORTY_GOOGLE_CLIENT_ID, SHORTY_GOOGLE_CLIENT_SECRET and "
                    + "SHORTY_ENCRYPTION_KEY on the server.";
        }
        if (status.connected()) {
            return "Connected since " + Formats.dateTime(status.connectedAt()) + ".";
        }
        if (status.error() != null) {
            return "Not connected: " + status.error();
        }
        return "Not connected yet.";
    }

    Span connection() {
        return connection;
    }

    Button postButton() {
        return post;
    }

    Button connectButton() {
        return connect;
    }

    Button finishButton() {
        return finish;
    }

    Button disconnectButton() {
        return disconnect;
    }

    TextField pastedField() {
        return pasted;
    }
}
