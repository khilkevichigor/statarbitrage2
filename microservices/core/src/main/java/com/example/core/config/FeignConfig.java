package com.example.core.config;

import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    /**
     * Конфигурация таймаутов для Feign клиентов
     * Увеличиваем таймауты для работы с candles микросервисом
     * который может долго обрабатывать запросы на получение большого количества свечей
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                30, TimeUnit.SECONDS, // connectTimeout - таймаут подключения 30 секунд
                1800, TimeUnit.SECONDS, // readTimeout - таймаут чтения 3 минуты
                true // followRedirects
        );
    }
}