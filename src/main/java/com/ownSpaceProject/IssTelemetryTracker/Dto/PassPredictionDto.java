package com.ownSpaceProject.IssTelemetryTracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassPredictionDto {

    private String targetName;
    private Double targetLatitude;
    private Double targetLongitude;
    private Long generatedAt;

    @Builder.Default
    private List<PassDetailDto> passes = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassDetailDto {
        private Long aosTimestamp;
        private Long losTimestamp;
        private String formattedAosIst;
        private String formattedLosIst;
        private Integer durationSeconds;
        private String durationFormatted;
        private Double maxElevationDeg;
        private String passType; // VISIBLE SIGHTING, DAYLIGHT PASS, RADIO PASS
        private String quality;  // HIGH, MEDIUM, LOW
    }
}
