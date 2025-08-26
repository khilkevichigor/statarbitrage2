package com.example.csv.service;

import com.example.shared.events.CsvEvent;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Основной сервис для экспорта данных в CSV формат
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvService {
    private final AppendPairDataToCsvService appendPairDataToCsvService;

    /**
     * Обработка событий для экспорта в CSV
     */
    @Bean
    public Consumer<CsvEvent> csvEventsConsumer() {
        return this::handleCsvExportEvent;
    }

    private void handleCsvExportEvent(CsvEvent event) {
        log.info("📄 Получено событие для экспорта CSV: {}", event.getEventType());

        try {
            // Обработка различных типов событий для экспорта
            switch (event.getEventType()) {
                case "EXPORT_TRADES":
                    exportTradesToCsv(event);
                    break;
                case "EXPORT_PORTFOLIO":
                    exportPortfolioToCsv(event);
                    break;
                case "EXPORT_ANALYTICS":
                    exportAnalyticsToCsv(event);
                    break;
                case "EXPORT_CUSTOM_REPORT":
                    exportCustomReportToCsv(event);
                    break;
                case "EXPORT_PAIR_DATA_REPORT":
                    exportPairDataReportToCsv(event);
                    break;
                default:
                    log.warn("⚠️ Неизвестный тип события для CSV экспорта: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при экспорте в CSV: {}", e.getMessage(), e);
        }
    }

    private void exportPairDataReportToCsv(CsvEvent event) {
        log.info("📋 Экспорт пользовательского отчета в CSV");

        try {
            TradingPair tradingPair = event.getTradingPair();
            appendPairDataToCsvService.appendPairDataToCsv(tradingPair);
            log.info("PairData {} успешно добавлена в csv файл.", tradingPair.getPairName());
        } catch (Exception e) {
            log.error("❌ Ошибка при экспорте пользовательского отчета в CSV: {}", e.getMessage(), e);
        }
    }

    /**
     * Экспорт торговых данных в CSV
     */
    private void exportTradesToCsv(CsvEvent event) {
        log.info("📊 Экспорт торговых данных в CSV");

        try {
            // Логика экспорта торговых данных
            String filename = generateTradesCsvFilename();
            log.info("📄 Создан файл экспорта сделок: {}", filename);

            // Здесь будет логика создания CSV файла со сделками
            writeTradesToCsv(filename, event);

        } catch (Exception e) {
            log.error("❌ Ошибка при экспорте сделок в CSV: {}", e.getMessage(), e);
        }
    }

    /**
     * Экспорт портфеля в CSV
     */
    private void exportPortfolioToCsv(CsvEvent event) {
        log.info("💼 Экспорт портфеля в CSV");

        try {
            String filename = generatePortfolioCsvFilename();
            log.info("📄 Создан файл экспорта портфеля: {}", filename);

            // Здесь будет логика создания CSV файла с данными портфеля
            writePortfolioToCsv(filename, event);

        } catch (Exception e) {
            log.error("❌ Ошибка при экспорте портфеля в CSV: {}", e.getMessage(), e);
        }
    }

    /**
     * Экспорт аналитики в CSV
     */
    private void exportAnalyticsToCsv(CsvEvent event) {
        log.info("📈 Экспорт аналитики в CSV");

        try {
            String filename = generateAnalyticsCsvFilename();
            log.info("📄 Создан файл экспорта аналитики: {}", filename);

            // Здесь будет логика создания CSV файла с аналитическими данными
            writeAnalyticsToCsv(filename, event);

        } catch (Exception e) {
            log.error("❌ Ошибка при экспорте аналитики в CSV: {}", e.getMessage(), e);
        }
    }

    /**
     * Экспорт пользовательского отчета в CSV
     */
    private void exportCustomReportToCsv(CsvEvent event) {
        log.info("📋 Экспорт пользовательского отчета в CSV");

        try {
            String filename = generateCustomReportCsvFilename(event);
            log.info("📄 Создан файл пользовательского отчета: {}", filename);

            // Здесь будет логика создания CSV файла с пользовательскими данными
            writeCustomReportToCsv(filename, event);

        } catch (Exception e) {
            log.error("❌ Ошибка при экспорте пользовательского отчета в CSV: {}", e.getMessage(), e);
        }
    }

    /**
     * Генерация имени файла для экспорта сделок
     */
    private String generateTradesCsvFilename() {
        return String.format("closed_trades_%s.csv",
                java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm")
                ));
    }

    /**
     * Генерация имени файла для экспорта портфеля
     */
    private String generatePortfolioCsvFilename() {
        return String.format("portfolio_%s.csv",
                java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm")
                ));
    }

    /**
     * Генерация имени файла для экспорта аналитики
     */
    private String generateAnalyticsCsvFilename() {
        return String.format("analytics_%s.csv",
                java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm")
                ));
    }

    /**
     * Генерация имени файла для пользовательского отчета
     */
    private String generateCustomReportCsvFilename(CsvEvent event) {
        String reportType = "custom"; // Так как в TradingEvent нет метода getData()
        return String.format("%s_report_%s.csv", reportType,
                java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm")
                ));
    }

    /**
     * Запись торговых данных в CSV файл
     */
    private void writeTradesToCsv(String filename, CsvEvent event) {
        log.debug("✍️ Запись торговых данных в файл: {}", filename);
        // Здесь будет логика записи данных в CSV файл
    }

    /**
     * Запись данных портфеля в CSV файл
     */
    private void writePortfolioToCsv(String filename, CsvEvent event) {
        log.debug("✍️ Запись данных портфеля в файл: {}", filename);
        // Здесь будет логика записи данных в CSV файл
    }

    /**
     * Запись аналитических данных в CSV файл
     */
    private void writeAnalyticsToCsv(String filename, CsvEvent event) {
        log.debug("✍️ Запись аналитических данных в файл: {}", filename);
        // Здесь будет логика записи данных в CSV файл
    }

    /**
     * Запись пользовательского отчета в CSV файл
     */
    private void writeCustomReportToCsv(String filename, CsvEvent event) {
        log.debug("✍️ Запись пользовательского отчета в файл: {}", filename);
        // Здесь будет логика записи данных в CSV файл
    }
}