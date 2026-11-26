package com.ritsync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pushes a fresh heatmap to {@code /topic/session/{id}/heatmap} after each successful check-in. */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeatmapPublisher {

    public record CheckInEvent(Long sessionId) {}

    private final HeatmapAnalyticsService heatmapService;
    private final SimpMessagingTemplate messaging;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onCheckIn(CheckInEvent event) {
        publish(event.sessionId());
    }

    public void publish(Long sessionId) {
        try {
            messaging.convertAndSend("/topic/session/" + sessionId + "/heatmap",
                heatmapService.getSessionHeatmap(sessionId));
        } catch (Exception e) {
            log.warn("Could not publish heatmap for session {}: {}", sessionId, e.getMessage());
        }
    }
}
