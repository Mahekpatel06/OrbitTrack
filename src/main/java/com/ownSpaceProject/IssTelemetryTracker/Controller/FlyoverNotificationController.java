package com.ownSpaceProject.IssTelemetryTracker.Controller;

import com.ownSpaceProject.IssTelemetryTracker.Dto.NotificationResponseDto;
import com.ownSpaceProject.IssTelemetryTracker.Dto.SubscriptionRequestDto;
import com.ownSpaceProject.IssTelemetryTracker.Service.FlyoverAlertDispatcherService;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.FlyoverNotificationLog;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.FlyoverSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FlyoverNotificationController {

    private final FlyoverAlertDispatcherService dispatcherService;

    /**
     * POST /api/notifications/subscribe
     * Registers or updates a flyover alert email subscription.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<FlyoverSubscription> subscribe(@RequestBody SubscriptionRequestDto dto) {
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(dispatcherService.subscribe(dto));
    }

    /**
     * DELETE /api/notifications/unsubscribe?email=...
     * Unsubscribes an email address from flyover alerts.
     */
    @DeleteMapping("/unsubscribe")
    public ResponseEntity<String> unsubscribe(@RequestParam(name = "email") String email) {
        boolean removed = dispatcherService.unsubscribe(email);
        return ResponseEntity.ok(removed ? "Unsubscribed successfully" : "Email not found");
    }

    /**
     * POST /api/notifications/test-email
     * Sends an immediate test flyover alert email.
     */
    @PostMapping("/test-email")
    public ResponseEntity<NotificationResponseDto> sendTestEmail(
            @RequestParam(name = "email") String email,
            @RequestParam(name = "city", defaultValue = "Bengaluru, India") String city,
            @RequestParam(name = "lat", defaultValue = "12.9716") Double lat,
            @RequestParam(name = "lon", defaultValue = "77.5946") Double lon) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(NotificationResponseDto.builder()
                    .success(false)
                    .message("Email address is required")
                    .build());
        }
        return ResponseEntity.ok(dispatcherService.sendTestAlertEmail(email, city, lat, lon));
    }

    /**
     * GET /api/notifications/logs
     * Returns the 20 most recent flyover alert notification delivery logs.
     */
    @GetMapping("/logs")
    public ResponseEntity<List<FlyoverNotificationLog>> getLogs() {
        return ResponseEntity.ok(dispatcherService.getRecentLogs());
    }
}
