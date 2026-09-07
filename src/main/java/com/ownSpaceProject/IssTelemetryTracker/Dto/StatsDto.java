package com.ownSpaceProject.IssTelemetryTracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsDto {

    private Double totalDistanceTravelledKm;
    private Long totalTelemetryRecords;
    private Long daylightCount;
    private Long eclipsedCount;
    private Double daylightPercentage;

    private Long totalAlertsLogged;
    private Long totalPassDurationSeconds;
    private String mostVisitedGroundStation;
    private Long mostVisitedGroundStationPassCount;

    private Double latestAltitudeKm;
    private Double latestVelocityKmS;
    private Long latestTimestamp;
}
