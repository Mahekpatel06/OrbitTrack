package com.ownSpaceProject.IssTelemetryTracker.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InfoDto {
    private Integer satid;
    private String satname;

    public InfoDto() {
        super();
    }

    public InfoDto(Integer satid, String satname) {
        this.satid = satid;
        this.satname = satname;
    }
}
