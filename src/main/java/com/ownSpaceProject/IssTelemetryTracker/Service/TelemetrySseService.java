package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssAlert;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTelemetry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TelemetrySseService {

    // Thread-safe list of active client SSE connections
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();


//  Registers a new SSE client connection with an extended timeout.
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(1800000L); // 30 minutes

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError((e) -> {
            emitter.complete();
            emitters.remove(emitter);
        });

        // Send initial connection handshake event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"message\":\"Connected to ISS Telemetry Stream\"}"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }


//  Periodic 15-second heartbeat to keep browser SSE connections alive and prevent proxy timeouts.
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}"));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }

//  Broadcasts newly recorded telemetry to all connected clients.
    public void broadcastTelemetry(IssTelemetry telemetry) {
        if (emitters.isEmpty()) return;

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("telemetry")
                        .data(telemetry));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }


//      Broadcasts ground station pass-over alerts to all connected clients.
    public void broadcastAlert(IssAlert alert) {
        if (emitters.isEmpty()) return;

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("alert")
                        .data(alert));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }
}
