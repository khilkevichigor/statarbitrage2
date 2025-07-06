package com.example.statarbitrage.ui.services;

import com.example.statarbitrage.ui.views.MainView;
import com.vaadin.flow.component.UI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UIUpdateService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<UI, MainView> activeViews = new ConcurrentHashMap<>();

    public UIUpdateService() {
        startPeriodicUpdates();
    }

    public void registerView(UI ui, MainView mainView) {
        activeViews.put(ui, mainView);
        log.debug("Registered UI view: {}", ui.getUIId());
    }

    public void unregisterView(UI ui) {
        activeViews.remove(ui);
        log.debug("Unregistered UI view: {}", ui.getUIId());
    }

    public void triggerUpdate() {
        activeViews.forEach((ui, mainView) -> {
            if (ui.isAttached()) {
                try {
                    ui.access(mainView::updateUI);
                } catch (Exception e) {
                    log.error("Error updating UI for view: {}", ui.getUIId(), e);
                }
            } else {
                // Clean up detached UIs
                activeViews.remove(ui);
            }
        });
    }

    private void startPeriodicUpdates() {
        scheduler.scheduleAtFixedRate(this::triggerUpdate, 5, 15, TimeUnit.SECONDS);
        log.info("Started periodic UI updates every 15 seconds");
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}