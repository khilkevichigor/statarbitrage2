package com.example.core.ui.components;

import com.example.core.services.PositionService;
import com.example.shared.enums.PositionStatus;
import com.example.shared.enums.PositionType;
import com.example.shared.models.Position;
import com.example.shared.utils.NumberFormatter;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@SpringComponent
@UIScope
public class PositionsComponent extends VerticalLayout {

    private final PositionService positionService;

    private final Grid<Position> positionsGrid;
    private final ComboBox<PositionStatus> statusFilter;
    private final ComboBox<PositionType> typeFilter;
    private final VerticalLayout statisticsLayout;
    private final Checkbox showDeletedCheckbox;

    private Consumer<Void> uiUpdateCallback;

    public PositionsComponent(PositionService positionService) {
        this.positionService = positionService;
        this.positionsGrid = new Grid<>(Position.class, false);
        this.statusFilter = new ComboBox<>("Фильтр по статусу");
        this.typeFilter = new ComboBox<>("Фильтр по типу");
        this.statisticsLayout = new VerticalLayout();
        this.showDeletedCheckbox = new Checkbox("Показать удаленные");

        initializeComponent();
        setupGrid();
        setupFilters();
    }

    private void initializeComponent() {
        setSpacing(true);
        setPadding(false);
        setSizeFull();

        // Создаем статистику
        updateStatistics();

        // Создаем фильтры
        HorizontalLayout filtersLayout = new HorizontalLayout(statusFilter, typeFilter, showDeletedCheckbox);
        filtersLayout.setAlignItems(Alignment.END);

        add(statisticsLayout, filtersLayout, positionsGrid);
    }

    private void setupFilters() {
        // Настройка фильтра статуса
        statusFilter.setItems(PositionStatus.values());
        statusFilter.setItemLabelGenerator(PositionStatus::getDisplayName);
        statusFilter.setClearButtonVisible(true);
        statusFilter.setPlaceholder("Все статусы");
        statusFilter.addValueChangeListener(e -> refreshPositions());

        // Настройка фильтра типа
        typeFilter.setItems(PositionType.values());
        typeFilter.setItemLabelGenerator(PositionType::getDisplayName);
        typeFilter.setClearButtonVisible(true);
        typeFilter.setPlaceholder("Все типы");
        typeFilter.addValueChangeListener(e -> refreshPositions());

        // Настройка чекбокса для удаленных
        showDeletedCheckbox.setValue(false);
        showDeletedCheckbox.addValueChangeListener(e -> refreshPositions());
    }

    private void setupGrid() {
        positionsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        positionsGrid.setSizeFull();

        // Основные колонки
        positionsGrid.addColumn(Position::getId)
                .setHeader("ID")
                .setAutoWidth(true)
                .setFlexGrow(0);

        positionsGrid.addColumn(Position::getPositionId)
                .setHeader("Position ID")
                .setAutoWidth(true)
                .setFlexGrow(0);

        positionsGrid.addColumn(Position::getSymbol)
                .setHeader("Символ")
                .setAutoWidth(true)
                .setFlexGrow(0);

        positionsGrid.addColumn(new ComponentRenderer<>(this::createTypeRenderer))
                .setHeader("Тип")
                .setAutoWidth(true)
                .setFlexGrow(0);

        positionsGrid.addColumn(new ComponentRenderer<>(this::createStatusRenderer))
                .setHeader("Статус")
                .setAutoWidth(true)
                .setFlexGrow(0);

        // Размер и цены
        positionsGrid.addColumn(position ->
                        position.getSize() != null ? NumberFormatter.format(position.getSize(), 4) : "")
                .setHeader("Размер")
                .setAutoWidth(true)
                .setFlexGrow(0);

        positionsGrid.addColumn(position ->
                        position.getEntryPrice() != null ? NumberFormatter.format(position.getEntryPrice(), 4) : "")
                .setHeader("Цена входа")
                .setAutoWidth(true)
                .setFlexGrow(0);

        positionsGrid.addColumn(position ->
                        position.getCurrentPrice() != null ? NumberFormatter.format(position.getCurrentPrice(), 4) : "")
                .setHeader("Текущая цена")
                .setAutoWidth(true)
                .setFlexGrow(0);

        // Плечо
        positionsGrid.addColumn(position ->
                        position.getLeverage() != null ? NumberFormatter.format(position.getLeverage(), 1) + "x" : "")
                .setHeader("Плечо")
                .setAutoWidth(true)
                .setFlexGrow(0);

        // PnL
        positionsGrid.addColumn(new ComponentRenderer<>(this::createUnrealizedPnLRenderer))
                .setHeader("Нереализованный PnL")
                .setAutoWidth(true)
                .setFlexGrow(0);

        positionsGrid.addColumn(new ComponentRenderer<>(this::createRealizedPnLRenderer))
                .setHeader("Реализованный PnL")
                .setAutoWidth(true)
                .setFlexGrow(0);

        // Время
        positionsGrid.addColumn(position ->
                        position.getOpenTime() != null ?
                                position.getOpenTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) : "")
                .setHeader("Время открытия")
                .setAutoWidth(true)
                .setFlexGrow(0);

        positionsGrid.addColumn(position ->
                        position.getLastUpdated() != null ?
                                position.getLastUpdated().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) : "")
                .setHeader("Последнее обновление")
                .setAutoWidth(true)
                .setFlexGrow(1);

        // Действия
        positionsGrid.addColumn(new ComponentRenderer<>(this::createActionsRenderer))
                .setHeader("Действия")
                .setAutoWidth(true)
                .setFlexGrow(0);

        // Стиль для удаленных позиций
        positionsGrid.setClassNameGenerator(position -> 
            position.getIsDeleted() ? "deleted-position" : null);

        refreshPositions();
    }

    private Span createTypeRenderer(Position position) {
        Span span = new Span(position.getDirectionString());
        if (position.getType() == PositionType.LONG) {
            span.getElement().getThemeList().add("badge success");
        } else {
            span.getElement().getThemeList().add("badge error");
        }
        return span;
    }

    private Span createStatusRenderer(Position position) {
        Span span = new Span(position.getStatus().getDisplayName());

        switch (position.getStatus()) {
            case OPEN:
                span.getElement().getThemeList().add("badge success");
                break;
            case CLOSED:
                span.getElement().getThemeList().add("badge");
                break;
            case PENDING:
                span.getElement().getThemeList().add("badge primary");
                break;
            case CLOSING:
                span.getElement().getThemeList().add("badge contrast");
                break;
            case FAILED:
                span.getElement().getThemeList().add("badge error");
                break;
        }
        return span;
    }

    private Span createUnrealizedPnLRenderer(Position position) {
        if (position.getUnrealizedPnLUSDT() == null) {
            return new Span("-");
        }

        String usdtText = NumberFormatter.format(position.getUnrealizedPnLUSDT(), 2) + " USDT";
        String percentText = position.getUnrealizedPnLPercent() != null ?
                " (" + NumberFormatter.format(position.getUnrealizedPnLPercent(), 2) + "%)" : "";

        Span span = new Span(usdtText + percentText);

        if (position.getUnrealizedPnLUSDT().compareTo(BigDecimal.ZERO) > 0) {
            span.getElement().getStyle().set("color", "var(--lumo-success-text-color)");
        } else if (position.getUnrealizedPnLUSDT().compareTo(BigDecimal.ZERO) < 0) {
            span.getElement().getStyle().set("color", "var(--lumo-error-text-color)");
        }

        return span;
    }

    private Span createRealizedPnLRenderer(Position position) {
        if (position.getRealizedPnLUSDT() == null) {
            return new Span("-");
        }

        String usdtText = NumberFormatter.format(position.getRealizedPnLUSDT(), 2) + " USDT";
        String percentText = position.getRealizedPnLPercent() != null ?
                " (" + NumberFormatter.format(position.getRealizedPnLPercent(), 2) + "%)" : "";

        Span span = new Span(usdtText + percentText);

        if (position.getRealizedPnLUSDT().compareTo(BigDecimal.ZERO) > 0) {
            span.getElement().getStyle().set("color", "var(--lumo-success-text-color)");
        } else if (position.getRealizedPnLUSDT().compareTo(BigDecimal.ZERO) < 0) {
            span.getElement().getStyle().set("color", "var(--lumo-error-text-color)");
        }

        return span;
    }

    private HorizontalLayout createActionsRenderer(Position position) {
        Button detailsButton = new Button(VaadinIcon.INFO_CIRCLE.create());
        detailsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        detailsButton.setTooltipText("Подробности");
        detailsButton.addClickListener(e -> showPositionDetails(position));

        HorizontalLayout layout = new HorizontalLayout(detailsButton);

        if (position.getIsDeleted()) {
            // Если позиция удалена - показываем кнопку восстановления
            Button restoreButton = new Button(VaadinIcon.REFRESH.create());
            restoreButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
            restoreButton.setTooltipText("Восстановить");
            restoreButton.addClickListener(e -> confirmRestorePosition(position));
            layout.add(restoreButton);
        } else {
            // Если позиция активна - показываем кнопку удаления
            Button deleteButton = new Button(VaadinIcon.TRASH.create());
            deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteButton.setTooltipText("Удалить");
            deleteButton.addClickListener(e -> confirmDeletePosition(position));
            layout.add(deleteButton);
        }

        return layout;
    }

    private void showPositionDetails(Position position) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Детали позиции " + position.getSymbol());

        VerticalLayout content = new VerticalLayout();
        content.add(
                new Span("ID: " + position.getId()),
                new Span("Position ID: " + position.getPositionId()),
                new Span("Trading Pair ID: " + position.getTradingPairId()),
                new Span("Символ: " + position.getSymbol()),
                new Span("Тип: " + position.getType().getDisplayName()),
                new Span("Статус: " + position.getStatus().getDisplayName()),
                new Span("Размер: " + (position.getSize() != null ? NumberFormatter.format(position.getSize(), 8) : "N/A")),
                new Span("Цена входа: " + (position.getEntryPrice() != null ? NumberFormatter.format(position.getEntryPrice(), 8) : "N/A")),
                new Span("Текущая цена: " + (position.getCurrentPrice() != null ? NumberFormatter.format(position.getCurrentPrice(), 8) : "N/A")),
                new Span("Плечо: " + (position.getLeverage() != null ? NumberFormatter.format(position.getLeverage(), 2) + "x" : "N/A")),
                new Span("Выделенная сумма: " + (position.getAllocatedAmount() != null ? NumberFormatter.format(position.getAllocatedAmount(), 2) + " USDT" : "N/A")),
                new Span("Комиссии открытия: " + (position.getOpeningFees() != null ? NumberFormatter.format(position.getOpeningFees(), 4) : "N/A")),
                new Span("Комиссии фандинга: " + (position.getFundingFees() != null ? NumberFormatter.format(position.getFundingFees(), 4) : "N/A")),
                new Span("Таймфрейм: " + (position.getTimeframe() != null ? position.getTimeframe() : "N/A")),
                new Span("Количество свечей: " + (position.getCandleCount() != null ? position.getCandleCount() : "N/A"))
        );

        if (position.getMetadata() != null && !position.getMetadata().isEmpty()) {
            content.add(new Span("Метаданные: " + position.getMetadata()));
        }

        dialog.add(content);

        Button closeButton = new Button("Закрыть", e -> dialog.close());
        dialog.getFooter().add(closeButton);

        dialog.open();
    }

    private void confirmDeletePosition(Position position) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Подтверждение удаления");

        VerticalLayout content = new VerticalLayout();
        content.add(new Span("Вы уверены, что хотите удалить позицию " + position.getSymbol() + "?"));

        Button confirmButton = new Button("Удалить", e -> {
            deletePosition(position);
            confirmDialog.close();
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        Button cancelButton = new Button("Отмена", e -> confirmDialog.close());

        HorizontalLayout buttons = new HorizontalLayout(confirmButton, cancelButton);
        content.add(buttons);

        confirmDialog.add(content);
        confirmDialog.open();
    }

    private void confirmRestorePosition(Position position) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Подтверждение восстановления");

        VerticalLayout content = new VerticalLayout();
        content.add(new Span("Вы уверены, что хотите восстановить позицию " + position.getSymbol() + "?"));

        Button confirmButton = new Button("Восстановить", e -> {
            restorePosition(position);
            confirmDialog.close();
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        Button cancelButton = new Button("Отмена", e -> confirmDialog.close());

        HorizontalLayout buttons = new HorizontalLayout(confirmButton, cancelButton);
        content.add(buttons);

        confirmDialog.add(content);
        confirmDialog.open();
    }

    private void restorePosition(Position position) {
        try {
            positionService.restore(position);
            Notification.show("Позиция успешно восстановлена", 3000, Notification.Position.TOP_END);
            refreshPositions();
            updateStatistics();
            triggerUIUpdate();
        } catch (Exception e) {
            log.error("Ошибка при восстановлении позиции", e);
            Notification.show("Ошибка при восстановлении позиции: " + e.getMessage(),
                    5000, Notification.Position.TOP_END);
        }
    }

    private void deletePosition(Position position) {
        try {
            positionService.softDelete(position);
            Notification.show("Позиция помечена как удаленная (можно восстановить)", 3000, Notification.Position.TOP_END);
            refreshPositions();
            updateStatistics();
            triggerUIUpdate();
        } catch (Exception e) {
            log.error("Ошибка при удалении позиции", e);
            Notification.show("Ошибка при удалении позиции: " + e.getMessage(),
                    5000, Notification.Position.TOP_END);
        }
    }

    private void updateStatistics() {
        statisticsLayout.removeAll();

        try {
            Map<String, Object> stats = positionService.getPositionsStatistics();

            H3 title = new H3("Статистика позиций");

            HorizontalLayout statsRow1 = new HorizontalLayout();
            statsRow1.add(
                    createStatBadge("Всего позиций", stats.get("totalPositions").toString(), "primary"),
                    createStatBadge("Открытых", stats.get("openPositions").toString(), "success"),
                    createStatBadge("Закрытых", stats.get("closedPositions").toString(), "contrast")
            );

            HorizontalLayout statsRow2 = new HorizontalLayout();
            statsRow2.add(
                    createStatBadge("LONG позиций", stats.get("longPositions").toString(), "success"),
                    createStatBadge("SHORT позиций", stats.get("shortPositions").toString(), "error")
            );

            BigDecimal unrealizedPnL = (BigDecimal) stats.get("totalUnrealizedPnL");
            BigDecimal realizedPnL = (BigDecimal) stats.get("totalRealizedPnL");

            HorizontalLayout statsRow3 = new HorizontalLayout();
            statsRow3.add(
                    createPnLBadge("Нереализованный PnL", unrealizedPnL),
                    createPnLBadge("Реализованный PnL", realizedPnL)
            );

            statisticsLayout.add(title, statsRow1, statsRow2, statsRow3);

        } catch (Exception e) {
            log.error("Ошибка при обновлении статистики", e);
            statisticsLayout.add(new Span("Ошибка при загрузке статистики"));
        }
    }

    private Span createStatBadge(String label, String value, String theme) {
        Span badge = new Span(label + ": " + value);
        badge.getElement().getThemeList().add("badge " + theme);
        return badge;
    }

    private Span createPnLBadge(String label, BigDecimal value) {
        String valueStr = value != null ? NumberFormatter.format(value, 2) + " USDT" : "0.00 USDT";
        Span badge = new Span(label + ": " + valueStr);

        if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
            badge.getElement().getThemeList().add("badge success");
        } else if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            badge.getElement().getThemeList().add("badge error");
        } else {
            badge.getElement().getThemeList().add("badge");
        }

        return badge;
    }

    public void refreshPositions() {
        try {
            // Получаем позиции с учетом фильтра удаленных
            List<Position> positions;
            if (showDeletedCheckbox.getValue()) {
                positions = positionService.getAllPositions(true); // Включая удаленные
            } else {
                positions = positionService.getAllPositions(); // Только активные
            }

            // Применяем фильтры
            if (statusFilter.getValue() != null) {
                positions = positions.stream()
                        .filter(p -> p.getStatus() == statusFilter.getValue())
                        .toList();
            }

            if (typeFilter.getValue() != null) {
                positions = positions.stream()
                        .filter(p -> p.getType() == typeFilter.getValue())
                        .toList();
            }

            positionsGrid.setItems(positions);

        } catch (Exception e) {
            log.error("Ошибка при обновлении списка позиций", e);
            Notification.show("Ошибка при загрузке позиций: " + e.getMessage(),
                    5000, Notification.Position.TOP_END);
        }
    }

    public void setUIUpdateCallback(Consumer<Void> callback) {
        this.uiUpdateCallback = callback;
    }

    private void triggerUIUpdate() {
        if (uiUpdateCallback != null) {
            uiUpdateCallback.accept(null);
        }
    }
}