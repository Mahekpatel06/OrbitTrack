package com.ownSpaceProject.IssTelemetryTracker.TelParams;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class GroundStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private String country;
    private Double latitude;
    private Double longitude;

    public GroundStation() {
        super();
    }

    public GroundStation(String name, String country, double lat, double lon) {
        this.name = name;
        this.country= country;
        this.latitude = lat;
        this.longitude = lon;
    }
}
