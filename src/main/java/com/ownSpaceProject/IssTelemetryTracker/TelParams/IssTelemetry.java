package com.ownSpaceProject.IssTelemetryTracker.TelParams;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class IssTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer index;

    private Integer issId;

    private Double latitude;

    private Double longitude;

    private Double velocity;

    private String visibility;

    private Double altitude;

    @Column(columnDefinition = "bigint")
    private Long timestamp;

    private Double travelledDis;

    public IssTelemetry() {
        super();
    }

    public IssTelemetry(Integer index, Integer issId, Double latitude, Double longitude, Double velocity, String visibility, Double altitude, Long timestamp, Double travelledDis) {
        this.index = index;
        this.issId = issId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.velocity = velocity;
        this.visibility = visibility;
        this.altitude = altitude;
        this.timestamp = timestamp;
        this.travelledDis = travelledDis;
    }

}
