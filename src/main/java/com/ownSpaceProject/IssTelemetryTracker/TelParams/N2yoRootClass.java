package com.ownSpaceProject.IssTelemetryTracker.TelParams;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ownSpaceProject.IssTelemetryTracker.Dto.InfoDto;
import com.ownSpaceProject.IssTelemetryTracker.Dto.IssTelDto;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class N2yoRootClass {

    private InfoDto info;
    private List<IssTelDto> positions;

    public N2yoRootClass() {
        super();
    }
}
