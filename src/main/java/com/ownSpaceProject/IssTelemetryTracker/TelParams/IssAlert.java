package com.ownSpaceProject.IssTelemetryTracker.TelParams;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class IssAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stationName;

    // Explicit Entry Coordinates & Time
    private double startLatitude;
    private double startLongitude;
    private LocalDateTime startTimestamp;

    // Explicit Exit Coordinates & Time
    private double endLatitude;
    private double endLongitude;
    private LocalDateTime endTimestamp;

    private long duration; // Total seconds

    public IssAlert() {
        super();
    }

    // Complete snapshot constructor
    public IssAlert(String stationName, double startLatitude, double startLongitude, LocalDateTime startTimestamp,
                    double endLatitude, double endLongitude, LocalDateTime endTimestamp, long duration) {
        this.stationName = stationName;
        this.startLatitude = startLatitude;
        this.startLongitude = startLongitude;
        this.startTimestamp = startTimestamp;
        this.endLatitude = endLatitude;
        this.endLongitude = endLongitude;
        this.endTimestamp = endTimestamp;
        this.duration = duration;
    }
}
