package io.github.pgatzka.shorty.ui.view;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.lumo.Lumo;

/** Application shell: theme and page-level settings. */
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@PWA(name = "Shorty", shortName = "Shorty")
public class AppShell implements AppShellConfigurator {

    private static final long serialVersionUID = 1L;
}
