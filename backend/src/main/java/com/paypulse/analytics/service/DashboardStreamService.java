package com.paypulse.analytics.service;

import com.paypulse.payment.service.PaymentStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DashboardStreamService {

    private static final Logger log = LoggerFactory.getLogger(DashboardStreamService.class);
    private static final long DEBOUNCE_MS = 2000; // max 1 push per 2s

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AnalyticsService analyticsService;
    private final AtomicBoolean pushScheduled = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DashboardStreamService(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Send initial data immediately on connect
        try {
            emitter.send(SseEmitter.event()
                    .name("kpi")
                    .data(analyticsService.getSummary(null, null)));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    @Async("notificationExecutor")
    @EventListener
    public void onPaymentStatusChanged(PaymentStatusChangedEvent event) {
        if (emitters.isEmpty()) return;
        // Debounce: schedule a push only if one isn't already pending
        if (pushScheduled.compareAndSet(false, true)) {
            scheduler.schedule(this::pushToAll, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void pushToAll() {
        pushScheduled.set(false);
        if (emitters.isEmpty()) return;
        try {
            Object payload = analyticsService.getSummary(null, null);
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("kpi").data(payload));
                } catch (IOException e) {
                    emitters.remove(emitter);
                }
            }
        } catch (Exception e) {
            log.error("Error pushing SSE update: {}", e.getMessage());
        }
    }
}