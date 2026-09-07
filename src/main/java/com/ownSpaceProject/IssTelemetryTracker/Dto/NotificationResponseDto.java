package com.ownSpaceProject.IssTelemetryTracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {
    private boolean success;
    private String message;
    private String deliveryMode; // "DELIVERED_SMTP" or "SIMULATED_LOG"
    private String recipient;
    private String targetCity;
    private String upcomingPassIst;
    private Double maxElevationDeg;
}
