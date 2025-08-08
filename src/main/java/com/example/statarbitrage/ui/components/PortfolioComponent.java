package com.example.statarbitrage.ui.components;

import com.example.statarbitrage.core.services.SettingsService;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.Portfolio;
import com.example.statarbitrage.trading.model.TradingProviderSwitchResult;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.theme.lumo.LumoUtility;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * UI компонент для мониторинга портфолио и управления торговлей
 */
@Slf4j
@SpringComponent
@UIScope
public class PortfolioComponent extends VerticalLayout {

    private final TradingIntegrationService tradingIntegrationServiceImpl;
    private final SettingsService settingsService;

    // UI элементы
    private Span totalBalanceLabel;
    private Span availableBalanceLabel;
    private Span reservedBalanceLabel;
    private Span totalReturnLabel;
    private Span unrealizedPnLLabel;
    private Span realizedPnLLabel;
    private Span activePositionsLabel;
    private Span maxDrawdownLabel;
    private Span utilizationLabel;
    private ComboBox<TradingProviderType> tradingModeComboBox;

    // Флаг для предотвращения рекурсии
    private boolean isUpdatingComboBox = false;

    public PortfolioComponent(TradingIntegrationService tradingIntegrationServiceImpl, SettingsService settingsService) {
        this.tradingIntegrationServiceImpl = tradingIntegrationServiceImpl;
        this.settingsService = settingsService;
        initializeComponent();
        createPortfolioCards();
        updatePortfolioInfo();
        updateTradingModeAvailability();
    }

    private void initializeComponent() {
        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        H3 title = new H3("💰 Портфолио и торговля");
        title.addClassNames(LumoUtility.TextColor.PRIMARY, LumoUtility.FontSize.LARGE);
        title.getStyle().set("margin-bottom", "1.5rem").set("text-align", "center");

        add(title);
    }

    private void createPortfolioCards() {
        // Карточка с режимом торговли
        add(createTradingModeCard());

        // Карточка с основной информацией о портфолио
        add(createMainPortfolioCard());

        // Карточка с детальной информацией
        add(createDetailedPortfolioCard());
    }

    private Details createTradingModeCard() {
        Div cardContent = new Div();
        cardContent.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.MEDIUM);
        cardContent.getStyle().set("padding", "1.5rem");

        HorizontalLayout header = new HorizontalLayout();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.setWidthFull();

        Icon icon = new Icon(VaadinIcon.COG);
        icon.addClassNames(LumoUtility.TextColor.PRIMARY);

        H4 title = new H4("Режим торговли");
        title.getStyle().set("margin", "0");

        // Комбобокс для выбора режима торговли
        tradingModeComboBox = new ComboBox<>();
        tradingModeComboBox.setItems(TradingProviderType.values());
        tradingModeComboBox.setItemLabelGenerator(TradingProviderType::getDisplayName);
        tradingModeComboBox.setValue(tradingIntegrationServiceImpl.getCurrentTradingMode());
        tradingModeComboBox.setWidth("250px");

        tradingModeComboBox.addValueChangeListener(event -> {
            TradingProviderType newMode = event.getValue();
            if (newMode != null && newMode != event.getOldValue()) {
                handleTradingModeSwitch(newMode, event.getOldValue());
            }
        });

        header.add(icon, title);
        header.setFlexGrow(1, title);

        cardContent.add(header, tradingModeComboBox);

        Details details = new Details();
        details.setSummaryText("⚙️ Настройки торговли");
        details.setContent(cardContent);
        details.setOpened(true);
        details.getStyle().set("margin-bottom", "1rem");
        details.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderRadius.LARGE);

        return details;
    }

    private Details createMainPortfolioCard() {
        Div cardContent = new Div();
        cardContent.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.MEDIUM);
        cardContent.getStyle().set("padding", "1.5rem");

        // Создаем сетку с основными метриками
        HorizontalLayout metricsRow1 = new HorizontalLayout();
        metricsRow1.setWidthFull();
        metricsRow1.setSpacing(true);

        // Общий баланс
        VerticalLayout totalBalanceCard = createMetricCard("💰", "Общий баланс", "$0.00");
        totalBalanceLabel = (Span) totalBalanceCard.getComponentAt(1);
        totalBalanceLabel.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.BOLD);

        // Общая доходность
        VerticalLayout totalReturnCard = createMetricCard("📈", "Общая доходность", "0.00%");
        totalReturnLabel = (Span) totalReturnCard.getComponentAt(1);
        totalReturnLabel.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.SEMIBOLD);

        // Активные позиции
        VerticalLayout activePositionsCard = createMetricCard("🎯", "Активные позиции", "0");
        activePositionsLabel = (Span) activePositionsCard.getComponentAt(1);
        activePositionsLabel.addClassNames(LumoUtility.FontSize.LARGE);

        metricsRow1.add(totalBalanceCard, totalReturnCard, activePositionsCard);

        HorizontalLayout metricsRow2 = new HorizontalLayout();
        metricsRow2.setWidthFull();
        metricsRow2.setSpacing(true);

        // Доступный баланс
        VerticalLayout availableCard = createMetricCard("💵", "Доступно", "$0.00");
        availableBalanceLabel = (Span) availableCard.getComponentAt(1);

        // Зарезервировано
        VerticalLayout reservedCard = createMetricCard("🔒", "Зарезервировано", "$0.00");
        reservedBalanceLabel = (Span) reservedCard.getComponentAt(1);

        // Использование депо
        VerticalLayout utilizationCard = createMetricCard("📊", "Использование", "0%");
        utilizationLabel = (Span) utilizationCard.getComponentAt(1);

        metricsRow2.add(availableCard, reservedCard, utilizationCard);

        cardContent.add(metricsRow1, metricsRow2);

        Details details = new Details();
        details.setSummaryText("💰 Основные метрики портфолио");
        details.setContent(cardContent);
        details.setOpened(true);
        details.getStyle().set("margin-bottom", "1rem");
        details.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderRadius.LARGE);

        return details;
    }

    private Details createDetailedPortfolioCard() {
        Div cardContent = new Div();
        cardContent.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.MEDIUM);
        cardContent.getStyle().set("padding", "1.5rem");

        HorizontalLayout detailsRow = new HorizontalLayout();
        detailsRow.setWidthFull();
        detailsRow.setSpacing(true);

        // Нереализованная прибыль
        VerticalLayout unrealizedCard = createMetricCard("📊", "Нереализованная P&L", "$0.00");
        unrealizedPnLLabel = (Span) unrealizedCard.getComponentAt(1);

        // Реализованная прибыль
        VerticalLayout realizedCard = createMetricCard("✅", "Реализованная P&L", "$0.00");
        realizedPnLLabel = (Span) realizedCard.getComponentAt(1);

        // Максимальная просадка
        VerticalLayout drawdownCard = createMetricCard("📉", "Макс. просадка", "0.00%");
        maxDrawdownLabel = (Span) drawdownCard.getComponentAt(1);

        detailsRow.add(unrealizedCard, realizedCard, drawdownCard);
        cardContent.add(detailsRow);

        Details details = new Details();
        details.setSummaryText("📊 Детальная статистика");
        details.setContent(cardContent);
        details.setOpened(false);
        details.getStyle().set("margin-bottom", "1rem");
        details.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderRadius.LARGE);

        return details;
    }

    private VerticalLayout createMetricCard(String icon, String title, String value) {
        VerticalLayout card = new VerticalLayout();
        card.setSpacing(false);
        card.setPadding(true);
        card.addClassNames(LumoUtility.Background.CONTRAST_10, LumoUtility.BorderRadius.MEDIUM);
        card.getStyle().set("text-align", "center").set("min-width", "150px");

        Span iconSpan = new Span(icon);
        iconSpan.getStyle().set("font-size", "1.5rem");

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        Span valueSpan = new Span(value);
        valueSpan.addClassNames(LumoUtility.FontWeight.SEMIBOLD);

        card.add(iconSpan, titleSpan, valueSpan);
        return card;
    }

    /**
     * Обновление информации о портфолио
     */
    public void updatePortfolioInfo() {
        try {
            Portfolio portfolio = tradingIntegrationServiceImpl.getPortfolioInfo();

            if (portfolio != null) {
                updateUI(portfolio);
            } else {
                // Показываем нулевые значения если портфолио недоступно
                clearUI();
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении информации о портфолио", e);
            clearUI();
        }
    }

    private void updateUI(Portfolio portfolio) {
        // Основные метрики
        totalBalanceLabel.setText(formatCurrency(portfolio.getTotalBalance()));
        availableBalanceLabel.setText(formatCurrency(portfolio.getAvailableBalance()));
        reservedBalanceLabel.setText(formatCurrency(portfolio.getReservedBalance()));

        // Доходность с цветом
        BigDecimal totalReturn = portfolio.getTotalReturn();
        totalReturnLabel.setText(formatPercent(totalReturn));
        updateColorByValue(totalReturnLabel, totalReturn);

        // Прибыли/убытки
        unrealizedPnLLabel.setText(formatCurrency(portfolio.getUnrealizedPnL()));
        updateColorByValue(unrealizedPnLLabel, portfolio.getUnrealizedPnL());

        realizedPnLLabel.setText(formatCurrency(portfolio.getRealizedPnL()));
        updateColorByValue(realizedPnLLabel, portfolio.getRealizedPnL());

        // Другие метрики
        activePositionsLabel.setText(String.valueOf(portfolio.getActivePositionsCount()));
        maxDrawdownLabel.setText(formatPercent(portfolio.getMaxDrawdown()));
        utilizationLabel.setText(formatPercent(portfolio.getDepositUtilization()));

        // Цвет для просадки (всегда красный если > 0)
        if (portfolio.getMaxDrawdown() != null && portfolio.getMaxDrawdown().compareTo(BigDecimal.ZERO) > 0) {
            maxDrawdownLabel.getStyle().set("color", "var(--lumo-error-color)");
        } else {
            maxDrawdownLabel.getStyle().remove("color");
        }

        // Цвет для использования депо
        BigDecimal utilization = portfolio.getDepositUtilization();
        if (utilization != null) {
            if (utilization.compareTo(BigDecimal.valueOf(80)) > 0) {
                utilizationLabel.getStyle().set("color", "var(--lumo-error-color)");
            } else if (utilization.compareTo(BigDecimal.valueOf(60)) > 0) {
                utilizationLabel.getStyle().set("color", "var(--lumo-warning-color)");
            } else {
                utilizationLabel.getStyle().set("color", "var(--lumo-success-color)");
            }
        }
    }

    private void clearUI() {
        totalBalanceLabel.setText("$0.00");
        availableBalanceLabel.setText("$0.00");
        reservedBalanceLabel.setText("$0.00");
        totalReturnLabel.setText("0.00%");
        unrealizedPnLLabel.setText("$0.00");
        realizedPnLLabel.setText("$0.00");
        activePositionsLabel.setText("0");
        maxDrawdownLabel.setText("0.00%");
        utilizationLabel.setText("0%");

        // Убираем цвета
        totalReturnLabel.getStyle().remove("color");
        unrealizedPnLLabel.getStyle().remove("color");
        realizedPnLLabel.getStyle().remove("color");
        maxDrawdownLabel.getStyle().remove("color");
        utilizationLabel.getStyle().remove("color");
    }

    private void updateColorByValue(Span label, BigDecimal value) {
        if (value == null) {
            label.getStyle().remove("color");
            return;
        }

        if (value.compareTo(BigDecimal.ZERO) > 0) {
            label.getStyle().set("color", "var(--lumo-success-color)");
        } else if (value.compareTo(BigDecimal.ZERO) < 0) {
            label.getStyle().set("color", "var(--lumo-error-color)");
        } else {
            label.getStyle().remove("color");
        }
    }

    private String formatCurrency(BigDecimal value) {
        if (value == null) return "$0.00";
        return "$" + value.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) return "0.00%";
        return value.setScale(2, RoundingMode.HALF_UP).toString() + "%";
    }

    /**
     * Обновление доступности режима торговли в зависимости от состояния автотрейдинга
     */
    public void updateTradingModeAvailability() {
        try {
            boolean isAutoTradingEnabled = settingsService.getSettings().isAutoTradingEnabled();
            log.info("🔄 PortfolioComponent: updateTradingModeAvailability() вызван - autoTrading={}", isAutoTradingEnabled);

            if (isAutoTradingEnabled) {
                // Блокируем комбобокс и меняем стиль
                tradingModeComboBox.setEnabled(false);
                tradingModeComboBox.getStyle().set("opacity", "0.6");

                // Добавляем подсказку
                tradingModeComboBox.setTooltipText("🔒 Режим торговли заблокирован пока включен автотрейдинг");

                log.debug("Режим торговли заблокирован - автотрейдинг включен");
            } else {
                // Разблокируем комбобокс
                tradingModeComboBox.setEnabled(true);
                tradingModeComboBox.getStyle().remove("opacity");

                // Убираем подсказку
                tradingModeComboBox.setTooltipText("Выберите режим торговли");

                log.debug("Режим торговли разблокирован - автотрейдинг выключен");
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении доступности режима торговли", e);
        }
    }

    /**
     * Обработка переключения режима торговли с детальными сообщениями об ошибках
     */
    private void handleTradingModeSwitch(TradingProviderType newMode, TradingProviderType oldMode) {
        // Проверяем флаг предотвращения рекурсии
        if (isUpdatingComboBox) {
            return;
        }

        try {
            // Проверяем, что автотрейдинг выключен
            if (settingsService.getSettings().isAutoTradingEnabled()) {
                String message = "⚠️ Невозможно изменить режим торговли при включенном автотрейдинге.\n\nСначала отключите автотрейдинг в настройках.";
                Notification notification = Notification.show(message);
                notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_WARNING);
                notification.setDuration(5000);

                // Устанавливаем флаг перед изменением значения для предотвращения рекурсии
                isUpdatingComboBox = true;
                try {
                    tradingModeComboBox.setValue(oldMode);
                } finally {
                    isUpdatingComboBox = false;
                }
                return;
            }

            log.info("Переключение режима торговли с {} на {}", oldMode, newMode);

            // Используем метод с детальной информацией об ошибках
            TradingProviderSwitchResult result = tradingIntegrationServiceImpl.switchTradingModeWithDetails(newMode);

            if (result.isSuccess()) {
                // Успешное переключение
                String successMessage = "✅ Режим торговли успешно изменен на: " + newMode.getDisplayName();
                Notification successNotification = Notification.show(successMessage);
                successNotification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);

                // Обновляем информацию о портфолио
                updatePortfolioInfo();

                log.info("Успешное переключение на режим: {}", newMode.getDisplayName());

            } else {
                // Ошибка переключения - показываем детальное сообщение
                handleSwitchError(result, newMode, oldMode);
            }

        } catch (Exception e) {
            // Неожиданная ошибка
            log.error("Неожиданная ошибка при переключении режима торговли", e);

            String errorMessage = "❌ Системная ошибка при переключении режима торговли: " + e.getMessage();
            Notification errorNotification = Notification.show(errorMessage);
            errorNotification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            errorNotification.setDuration(5000);

            // Устанавливаем флаг перед изменением значения для предотвращения рекурсии
            isUpdatingComboBox = true;
            try {
                tradingModeComboBox.setValue(oldMode);
            } finally {
                isUpdatingComboBox = false;
            }
        }
    }

    /**
     * Обработка ошибок переключения с детальными сообщениями
     */
    private void handleSwitchError(TradingProviderSwitchResult result, TradingProviderType newMode, TradingProviderType oldMode) {
        log.error("Ошибка переключения режима торговли: тип={}, сообщение={}",
                result.getErrorType(), result.getErrorMessage());

        // Базовое сообщение об ошибке
        String errorMessage = result.getUserMessage() != null ?
                result.getUserMessage() :
                "❌ Не удалось переключиться на режим: " + newMode.getDisplayName();

        // Добавляем рекомендации если есть
        if (result.getRecommendation() != null && !result.getRecommendation().isEmpty()) {
            errorMessage += "\n\n💡 Рекомендация: " + result.getRecommendation();
        }

        // Определяем тип уведомления и длительность показа в зависимости от типа ошибки
        Notification errorNotification = createErrorNotification(result.getErrorType(), errorMessage);
        errorNotification.open();

        // Устанавливаем флаг перед изменением значения для предотвращения рекурсии
        isUpdatingComboBox = true;
        try {
            tradingModeComboBox.setValue(oldMode);
        } finally {
            isUpdatingComboBox = false;
        }
    }

    /**
     * Создание уведомления об ошибке в зависимости от типа
     */
    private Notification createErrorNotification(TradingProviderSwitchResult.SwitchErrorType errorType, String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);

        // Устанавливаем длительность в зависимости от типа ошибки
        switch (errorType) {
            case PROVIDER_NOT_IMPLEMENTED:
                notification.setDuration(3000); // Короткое уведомление для не реализованных провайдеров
                break;
            case CONFIGURATION_MISSING:
            case INVALID_CREDENTIALS:
                notification.setDuration(7000); // Длинное уведомление для проблем с конфигурацией
                break;
            case CONNECTION_ERROR:
                notification.setDuration(5000); // Среднее уведомление для проблем с соединением
                break;
            case INTERNAL_ERROR:
            default:
                notification.setDuration(4000); // Стандартное уведомление
                break;
        }

        return notification;
    }
}