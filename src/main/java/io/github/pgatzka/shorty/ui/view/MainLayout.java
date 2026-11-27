package io.github.pgatzka.shorty.ui.view;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;

/** Frame around all screens: header plus navigation drawer. */
public class MainLayout extends AppLayout {

    private static final long serialVersionUID = 1L;

    public MainLayout() {
        H1 title = new H1("Shorty");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");
        addToNavbar(new DrawerToggle(), title);

        SideNav navigation = new SideNav();
        navigation.addItem(
                new SideNavItem("Preview", PreviewView.class),
                new SideNavItem("Post now", PostNowView.class),
                new SideNavItem("Schedule", ScheduleView.class),
                new SideNavItem("History", HistoryView.class),
                new SideNavItem("Analytics", AnalyticsView.class));
        addToDrawer(navigation);
    }
}
