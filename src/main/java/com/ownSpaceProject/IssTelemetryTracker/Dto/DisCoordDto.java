package com.ownSpaceProject.IssTelemetryTracker.Dto;

import lombok.Data;

@Data
public class DisCoordDto {

    public Double latitude;
    public Double longitude;
    public Double altitude;

    public DisCoordDto() {
        super();
    }
    public DisCoordDto(Double latitude, Double longitude, Double altitude) {

        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }

}
