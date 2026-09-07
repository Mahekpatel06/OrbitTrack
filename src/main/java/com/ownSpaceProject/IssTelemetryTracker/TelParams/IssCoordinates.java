package com.ownSpaceProject.IssTelemetryTracker.TelParams;

import lombok.Data;

@Data
public class IssCoordinates {

    private Double latitude;
    private Double longitude;
    private Double altitude;

    public IssCoordinates() {
        super();
    }

    public IssCoordinates(Double latitude, Double longitude, Double altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }
}
