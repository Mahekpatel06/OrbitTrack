package com.ownSpaceProject.IssTelemetryTracker.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssTelDto {

    private Integer index;

    @JsonProperty(value = "satid")
    private Integer issId;

    @JsonProperty("satlatitude")
    private Double latitude;

    @JsonProperty("satlongitude")
    private Double longitude;

    private Double velocity;

    @JsonProperty("eclipsed")
    private String visibility;

    @JsonProperty("sataltitude")
    private Double altitude;
//    private String units;
    private Long timestamp;
    private Double travelledDis;

    public IssTelDto() {
        super();
    }

    public IssTelDto(Integer index, Integer issId, Double latitude, Double longitude, Double velocity, String visibility, Double altitude, Long timestamp, Double travelledDis) {
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
