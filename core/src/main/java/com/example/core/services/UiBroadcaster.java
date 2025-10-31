package com.example.core.services;

import com.example.shared.events.UpdateUiEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.shared.Registration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Broadcaster для отправки событий во все активные Vaadin UI
 */
@Slf4j
@Service
public class UiBroadcaster {
    
    private static final Executor executor = Executors.newSingleThreadExecutor();
    private static final LinkedList<Consumer<String>> listeners = new LinkedList<>();

    public static synchronized Registration register(Consumer<String> listener) {
        log.debug("📡 UiBroadcaster: Регистрируем новый UI listener");
        listeners.add(listener);

        return () -> {
            log.debug("📡 UiBroadcaster: Удаляем UI listener");
            synchronized (UiBroadcaster.class) {
                listeners.remove(listener);
            }
        };
    }

    public static synchronized void broadcast(String message) {
        log.info("📡 UiBroadcaster: Отправляем сообщение во все UI ({}): {}", listeners.size(), message);
        for (Consumer<String> listener : listeners) {
            executor.execute(() -> {
                try {
                    listener.accept(message);
                } catch (Exception e) {
                    log.error("❌ UiBroadcaster: Ошибка при отправке в UI: {}", e.getMessage(), e);
                }
            });
        }
    }

    @EventListener
    public void handleUpdateUi(UpdateUiEvent event) {
        try {
            log.info("📡 UiBroadcaster: ПОЛУЧЕНО событие UpdateUiEvent - отправляем broadcast");
            log.info("📡 UiBroadcaster: Thread: {}", Thread.currentThread().getName());
            
            // Отправляем сообщение во все активные UI
            broadcast("STABLE_PAIRS_UPDATE");
            
        } catch (Exception e) {
            log.error("❌ UiBroadcaster: Ошибка при обработке события UpdateUiEvent: {}", e.getMessage(), e);
        }
    }
}