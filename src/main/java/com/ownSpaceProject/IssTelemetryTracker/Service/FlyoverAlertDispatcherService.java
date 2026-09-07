package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.Dto.NotificationResponseDto;
import com.ownSpaceProject.IssTelemetryTracker.Dto.PassPredictionDto;
import com.ownSpaceProject.IssTelemetryTracker.Dto.SubscriptionRequestDto;
import com.ownSpaceProject.IssTelemetryTracker.Jpa.FlyoverNotificationLogJpaRepo;
import com.ownSpaceProject.IssTelemetryTracker.Jpa.FlyoverSubscriptionJpaRepo;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.FlyoverNotificationLog;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.FlyoverSubscription;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlyoverAlertDispatcherService {

    private final FlyoverSubscriptionJpaRepo subscriptionRepo;
    private final FlyoverNotificationLogJpaRepo notificationLogRepo;
    private final PassPredictionService passPredictionService;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.notifications.mail-enabled:false}")
    private boolean mailEnabled;

    @Value("${app.notifications.sender-email:mission-control@isstracker.space}")
    private String senderEmail;

    @Value("${app.notifications.window-hours:2}")
    private int windowHours;

    /**
     * Registers or updates a user subscription for flyover email alerts.
     */
    public FlyoverSubscription subscribe(SubscriptionRequestDto dto) {
        String cleanEmail = dto.getEmail().trim().toLowerCase();
        String city = dto.getTargetCity() != null ? dto.getTargetCity().trim() : "Custom Coordinates";
        double lat = dto.getLatitude() != null ? dto.getLatitude() : 12.9716;
        double lon = dto.getLongitude() != null ? dto.getLongitude() : 77.5946;
        double minElev = dto.getMinElevation() != null ? dto.getMinElevation() : 15.0;

        Optional<FlyoverSubscription> existing = subscriptionRepo.findByEmailIgnoreCaseAndTargetCityIgnoreCase(cleanEmail, city);
        FlyoverSubscription sub;
        if (existing.isPresent()) {
            sub = existing.get();
            sub.setLatitude(lat);
            sub.setLongitude(lon);
            sub.setMinElevation(minElev);
            sub.setActive(true);
            log.info("[FLYOVER SUBSCRIPTION] Reactivated subscription for: {} at {}", cleanEmail, city);
        } else {
            sub = FlyoverSubscription.builder()
                    .email(cleanEmail)
                    .targetCity(city)
                    .latitude(lat)
                    .longitude(lon)
                    .minElevation(minElev)
                    .active(true)
                    .subscribedAt(LocalDateTime.now())
                    .build();
            log.info("[FLYOVER SUBSCRIPTION] New subscription created for: {} at {}", cleanEmail, city);
        }
        return subscriptionRepo.save(sub);
    }

    /**
     * Unsubscribes an email from all active alerts.
     */
    public boolean unsubscribe(String email) {
        List<FlyoverSubscription> list = subscriptionRepo.findByEmailIgnoreCase(email.trim().toLowerCase());
        if (list.isEmpty()) return false;
        for (FlyoverSubscription s : list) {
            s.setActive(false);
            subscriptionRepo.save(s);
        }
        log.info("[FLYOVER SUBSCRIPTION] Deactivated alerts for: {}", email);
        return true;
    }

    /**
     * Triggers an immediate test alert email for testing/interview demo purposes.
     */
    public NotificationResponseDto sendTestAlertEmail(String email, String city, Double lat, Double lon) {
        double dLat = lat != null ? lat : 12.9716;
        double dLon = lon != null ? lon : 77.5946;
        String sCity = (city != null && !city.isBlank()) ? city : "Observer Location";

        PassPredictionDto prediction = passPredictionService.predictPasses(dLat, dLon, sCity, 2);
        PassPredictionDto.PassDetailDto samplePass;
        if (!prediction.getPasses().isEmpty()) {
            samplePass = prediction.getPasses().get(0);
        } else {
            samplePass = PassPredictionDto.PassDetailDto.builder()
                    .aosTimestamp(System.currentTimeMillis() / 1000 + 1800)
                    .losTimestamp(System.currentTimeMillis() / 1000 + 2160)
                    .formattedAosIst("Today, 07:45:00 PM IST")
                    .formattedLosIst("Today, 07:51:00 PM IST")
                    .durationSeconds(360)
                    .durationFormatted("6m 00s")
                    .maxElevationDeg(64.5)
                    .passType("HIGH OVERHEAD PASS ★")
                    .quality("HIGH")
                    .build();
        }
        return dispatchEmail(email.trim().toLowerCase(), sCity, samplePass, true);
    }

    /**
     * Background scheduler that checks for upcoming passes over all active subscribers every 10 minutes.
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void evaluateUpcomingPassesAndDispatch() {
        List<FlyoverSubscription> activeSubs = subscriptionRepo.findByActiveTrue();
        if (activeSubs.isEmpty()) {
            return;
        }

        long nowSec = System.currentTimeMillis() / 1000;
        long windowSec = (long) windowHours * 3600;

        for (FlyoverSubscription sub : activeSubs) {
            try {
                PassPredictionDto prediction = passPredictionService.predictPasses(sub.getLatitude(), sub.getLongitude(), sub.getTargetCity(), 1);
                for (PassPredictionDto.PassDetailDto pass : prediction.getPasses()) {
                    long timeUntilAos = pass.getAosTimestamp() - nowSec;
                    // Check if pass is upcoming within notification window (e.g. within 2 hours)
                    if (timeUntilAos > 0 && timeUntilAos <= windowSec) {
                        // Check if elevation meets user's threshold
                        if (pass.getMaxElevationDeg() >= sub.getMinElevation()) {
                            // Check if this specific pass has already been notified
                            if (sub.getLastNotifiedAosEpoch() == null || !sub.getLastNotifiedAosEpoch().equals(pass.getAosTimestamp())) {
                                dispatchEmail(sub.getEmail(), sub.getTargetCity(), pass, false);
                                sub.setLastNotifiedAosEpoch(pass.getAosTimestamp());
                                subscriptionRepo.save(sub);
                                break; // Notify for the next upcoming pass in this cycle
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[FLYOVER DISPATCHER] Failed evaluating passes for subscriber {}: {}", sub.getEmail(), e.getMessage());
            }
        }
    }

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    private boolean isMailConfigured() {
        return mailUsername != null && mailUsername.contains("@") && !mailUsername.toLowerCase().contains("your-email")
                && mailPassword != null && !mailPassword.trim().isEmpty() && !mailPassword.toLowerCase().contains("your-16-char");
    }

    /**
     * Composes and sends a rich HTML email alert.
     */
    private NotificationResponseDto dispatchEmail(String recipient, String city, PassPredictionDto.PassDetailDto pass, boolean isTest) {
        String subject = String.format("%s🚀 ISS Flyover Alert: Passing over %s at %s!",
                isTest ? "[TEST] " : "", city, pass.getFormattedAosIst());

        String htmlContent = buildHtmlEmailTemplate(city, pass, isTest);
        String deliveryMode = "SIMULATED_LOG";
        String statusMessage = null;

        // Auto-detect if SMTP credentials have been provided and are not placeholders
        boolean attemptsSmtp = mailEnabled && isMailConfigured() && mailSender != null;
        String effectiveSender = (mailUsername != null && mailUsername.contains("@") && !mailUsername.contains("your-email")) 
                ? mailUsername.trim() 
                : (senderEmail != null && !senderEmail.isEmpty() ? senderEmail : "mission-control@isstracker.space");

        if (attemptsSmtp) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(effectiveSender, "ISS Mission Control");
                helper.setTo(recipient);
                helper.setSubject(subject);
                helper.setText(htmlContent, true);
                mailSender.send(message);
                deliveryMode = "DELIVERED_SMTP";
                statusMessage = "Real email dispatched successfully to " + recipient + " via Gmail SMTP!";
                log.info("[SMTP SUCCESS] Dispatched flyover alert email to: {} from: {} for pass at {}", recipient, effectiveSender, pass.getFormattedAosIst());
            } catch (Exception e) {
                deliveryMode = "SMTP_FAILED";
                statusMessage = "SMTP Delivery Failed: " + e.getMessage() + ". Check your Google App Password and username in application.properties.";
                log.error("[SMTP ERROR] Failed to send via real SMTP to {}: {}", recipient, e.getMessage());
            }
        } else {
            deliveryMode = "SIMULATED_LOG";
            statusMessage = "Simulated email dispatch logged. Set app.notifications.mail-enabled=true with your Gmail credentials in application.properties for real inbox delivery.";
            log.info("[SIMULATED EMAIL DISPATCH] Dispatched simulated alert to: {} for pass at {}", recipient, pass.getFormattedAosIst());
        }

        // Record in database audit log
        FlyoverNotificationLog auditLog = FlyoverNotificationLog.builder()
                .recipientEmail(recipient)
                .targetCity(city)
                .passAosIst(pass.getFormattedAosIst())
                .maxElevationDeg(pass.getMaxElevationDeg())
                .passType(pass.getPassType())
                .dispatchedAt(LocalDateTime.now())
                .status(deliveryMode)
                .subject(subject)
                .build();
        notificationLogRepo.save(auditLog);

        return NotificationResponseDto.builder()
                .success(!"SMTP_FAILED".equals(deliveryMode))
                .message(statusMessage)
                .deliveryMode(deliveryMode)
                .recipient(recipient)
                .targetCity(city)
                .upcomingPassIst(pass.getFormattedAosIst())
                .maxElevationDeg(pass.getMaxElevationDeg())
                .build();
    }

    /**
     * Builds a responsive dark/cyan NASA-themed HTML email template.
     */
    private String buildHtmlEmailTemplate(String city, PassPredictionDto.PassDetailDto pass, boolean isTest) {
        String star = pass.getMaxElevationDeg() >= 40 ? "⭐ HIGH OVERHEAD PASS" : "SATELLITE FLYOVER";
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { margin: 0; padding: 0; background-color: #050811; font-family: 'Segoe UI', Arial, sans-serif; color: #f8fafc; }
                .container { max-width: 600px; margin: 20px auto; background: #0b1329; border: 1px solid rgba(56, 189, 248, 0.3); border-radius: 12px; overflow: hidden; }
                .header { background: linear-gradient(135deg, #0f172a 0%%, #1e293b 100%%); padding: 24px; text-align: center; border-bottom: 2px solid #0284c7; }
                .logo { font-size: 20px; font-weight: bold; color: #38bdf8; letter-spacing: 2px; }
                .badge { display: inline-block; background: rgba(2, 132, 199, 0.25); color: #38bdf8; border: 1px solid #0284c7; padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: bold; margin-top: 8px; }
                .body-content { padding: 28px 24px; }
                .hero-text { font-size: 20px; font-weight: bold; color: #ffffff; text-align: center; margin-bottom: 20px; }
                .highlight-city { color: #facc15; }
                .metric-card { background: rgba(15, 23, 42, 0.8); border: 1px solid rgba(56, 189, 248, 0.2); border-radius: 8px; padding: 16px; margin-bottom: 20px; }
                .row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid rgba(255, 255, 255, 0.06); }
                .row:last-child { border-bottom: none; }
                .label { color: #94a3b8; font-size: 13px; }
                .val { color: #ffffff; font-weight: bold; font-size: 14px; text-align: right; }
                .tips { background: rgba(234, 179, 8, 0.1); border-left: 4px solid #facc15; padding: 14px; border-radius: 4px; font-size: 13px; color: #fde047; line-height: 1.5; margin-bottom: 20px; }
                .footer { background: #050811; padding: 16px; text-align: center; font-size: 11px; color: #64748b; border-top: 1px solid rgba(255,255,255,0.08); }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <div class="logo">🛰️ ISS MISSION CONTROL</div>
                  <div class="badge">%s</div>
                </div>
                <div class="body-content">
                  <div class="hero-text">The Space Station is passing over <span class="highlight-city">%s</span>!</div>
                  
                  <div class="metric-card">
                    <div class="row">
                      <span class="label">Acquisition of Signal (AOS):</span>
                      <span class="val" style="color: #38bdf8;">%s</span>
                    </div>
                    <div class="row">
                      <span class="label">Loss of Signal (LOS):</span>
                      <span class="val">%s</span>
                    </div>
                    <div class="row">
                      <span class="label">Flyover Duration:</span>
                      <span class="val">%s</span>
                    </div>
                    <div class="row">
                      <span class="label">Max Elevation Angle:</span>
                      <span class="val" style="color: #facc15;">%.1f° (%s)</span>
                    </div>
                    <div class="row">
                      <span class="label">Orbital Velocity:</span>
                      <span class="val">~27,600 km/h (7.66 km/s)</span>
                    </div>
                  </div>

                  <div class="tips">
                    🔭 <strong>Viewing Advice:</strong> Step outside 2–3 minutes before AOS. The International Space Station appears as a bright white, steady light (like Venus or Jupiter) gliding silently across the night sky with no blinking aviation lights.
                  </div>
                </div>
                <div class="footer">
                  Orbital Ephemeris: Orekit SGP4 Ephemeris • Timezone: Asia/Kolkata (IST)<br>
                  Automated Space Flight Telemetry Dispatcher • Confidential Orbital Log
                </div>
              </div>
            </body>
            </html>
            """.formatted(isTest ? "TEST ALERT" : star, city, pass.getFormattedAosIst(), pass.getFormattedLosIst(),
                pass.getDurationFormatted(), pass.getMaxElevationDeg(), pass.getPassType());
    }

    public List<FlyoverNotificationLog> getRecentLogs() {
        return notificationLogRepo.findTop20ByOrderByDispatchedAtDesc();
    }
}
