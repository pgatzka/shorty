package io.github.pgatzka.shorty.ui.view;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.notification.Notification;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Browserless UI tests: real views, mocked services, no browser and no database. */
@SpringJUnitConfig(ViewTestBase.EmptyConfig.class)
@ViewPackages(classes = MainLayout.class)
abstract class ViewTestBase extends SpringBrowserlessTest {

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {}

    /** Texts of the notifications currently shown. */
    List<String> notifications() {
        return $(Notification.class).all().stream()
                .map(notification -> notification.getElement().getProperty("text", ""))
                .toList();
    }
}
