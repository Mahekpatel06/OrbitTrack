package com.ownSpaceProject.IssTelemetryTracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionRequestDto {
    private String email;
    private String targetCity;
    private Double latitude;
    private Double longitude;
    private Double minElevation; // e.g. 15.0 or 40.0
}
