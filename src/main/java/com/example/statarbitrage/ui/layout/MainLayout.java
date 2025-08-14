package com.example.statarbitrage.ui.layout;

import com.example.statarbitrage.ui.views.*;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Главный layout с боковым меню для всех страниц приложения
 */
public class MainLayout extends AppLayout {

    private H1 viewTitle;

    public MainLayout() {
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.getElement().setAttribute("aria-label", "Menu toggle");
        toggle.setTooltipText("Открыть/закрыть меню");

        viewTitle = new H1("StatArbitrage");
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        addToNavbar(false, toggle, viewTitle);
    }

    private void createDrawer() {
        SideNav nav = new SideNav();
        nav.setLabel("Навигация");

        // Создаем пункты меню
        nav.addItem(new SideNavItem("Главная", DashboardView.class, VaadinIcon.HOME.create()));
        nav.addItem(new SideNavItem("Портфолио", PortfolioView.class, VaadinIcon.WALLET.create()));
        nav.addItem(new SideNavItem("Настройки", SettingsView.class, VaadinIcon.COG.create()));

        // Пункты меню для торговых пар
        nav.addItem(new SideNavItem("Отобранные пары", SelectedPairsView.class, VaadinIcon.FILTER.create()));
        nav.addItem(new SideNavItem("Торгуемые пары", TradingPairsView.class, VaadinIcon.PLAY.create()));
        nav.addItem(new SideNavItem("Наблюдаемые пары", ObservedPairsView.class, VaadinIcon.EYE.create()));
        nav.addItem(new SideNavItem("Закрытые пары", ClosedPairsView.class, VaadinIcon.STOP.create()));
        nav.addItem(new SideNavItem("Пары с ошибками", ErrorPairsView.class, VaadinIcon.ERASER.create()));
        nav.addItem(new SideNavItem("Статистика", StatisticsView.class, VaadinIcon.CHART.create()));

        // Стилизуем меню
        nav.getElement().getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-right", "1px solid var(--lumo-contrast-10pct)");

        addToDrawer(nav);
    }

    @Override
    protected void afterNavigation() {
        super.afterNavigation();
        viewTitle.setText(getCurrentPageTitle());
    }

    private String getCurrentPageTitle() {
        String route = getContent().getClass().getSimpleName();
        return switch (route) {
            case "DashboardView" -> "Главная";
            case "PortfolioView" -> "Портфолио";
            case "SettingsView" -> "Настройки";
            case "SelectedPairsView" -> "Отобранные пары";
            case "TradingPairsView" -> "Торгуемые пары";
            case "ObservedPairsView" -> "Наблюдаемые пары";
            case "ClosedPairsView" -> "Закрытые пары";
            case "ErrorPairsView" -> "Пары с ошибками";
            case "StatisticsView" -> "Статистика";
            default -> "StatArbitrage";
        };
    }
}