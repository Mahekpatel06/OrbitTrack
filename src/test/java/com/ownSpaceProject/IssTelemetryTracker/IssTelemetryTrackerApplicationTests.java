package com.ownSpaceProject.IssTelemetryTracker;

import com.ownSpaceProject.IssTelemetryTracker.Dto.PassPredictionDto;
import com.ownSpaceProject.IssTelemetryTracker.Service.MissionReportService;
import com.ownSpaceProject.IssTelemetryTracker.Service.PassPredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IssTelemetryTrackerApplicationTests {

    @Autowired
    private PassPredictionService passPredictionService;

    @Autowired
    private MissionReportService missionReportService;

    @Test
    void contextLoads() {
    }

    @Test
    void testPassPrediction() {
        assertNotNull(passPredictionService);
        PassPredictionDto result = passPredictionService.predictPasses(12.9716, 77.5946, "Bengaluru, India", 2);
        assertNotNull(result);
        System.out.println("Predicted passes count: " + result.getPasses().size());
    }

    @Test
    void testGenerateMissionAuditPdf() {
        assertNotNull(missionReportService);
        byte[] pdf = missionReportService.generateMissionAuditPdf();
        assertNotNull(pdf);
        assertTrue(pdf.length > 500, "Generated PDF should be greater than 500 bytes");
        System.out.println("Mission Audit PDF generated successfully! Size: " + pdf.length + " bytes.");
    }

    @Autowired
    private com.ownSpaceProject.IssTelemetryTracker.Service.FlyoverAlertDispatcherService dispatcherService;

    @Autowired
    private com.ownSpaceProject.IssTelemetryTracker.Jpa.FlyoverSubscriptionJpaRepo subscriptionRepo;

    @Autowired
    private com.ownSpaceProject.IssTelemetryTracker.Jpa.FlyoverNotificationLogJpaRepo notificationLogRepo;

    @Test
    void testFlyoverSubscriptionAndAlertDispatch() {
        assertNotNull(dispatcherService);

        // 1. Subscribe
        com.ownSpaceProject.IssTelemetryTracker.Dto.SubscriptionRequestDto subDto =
                com.ownSpaceProject.IssTelemetryTracker.Dto.SubscriptionRequestDto.builder()
                        .email("test.observer@space.org")
                        .targetCity("Bengaluru, India")
                        .latitude(12.9716)
                        .longitude(77.5946)
                        .minElevation(15.0)
                        .build();

        var sub = dispatcherService.subscribe(subDto);
        assertNotNull(sub);
        assertEquals("test.observer@space.org", sub.getEmail());
        assertTrue(sub.isActive());

        // 2. Dispatch Test Alert Email
        com.ownSpaceProject.IssTelemetryTracker.Dto.NotificationResponseDto response =
                dispatcherService.sendTestAlertEmail("test.observer@space.org", "Bengaluru, India", 12.9716, 77.5946);
        assertNotNull(response);
        assertTrue(response.isSuccess());
        System.out.println("Notification dispatch result: " + response.getMessage() + " | Delivery Mode: " + response.getDeliveryMode());

        // 3. Verify Database Audit Log
        var logs = notificationLogRepo.findByRecipientEmailIgnoreCaseOrderByDispatchedAtDesc("test.observer@space.org");
        assertFalse(logs.isEmpty(), "An audit log entry must be persisted");
        System.out.println("Persisted log entry: " + logs.get(0).getSubject() + " | Status: " + logs.get(0).getStatus());

        // 4. Unsubscribe
        boolean unsubResult = dispatcherService.unsubscribe("test.observer@space.org");
        assertTrue(unsubResult);
    }
}
