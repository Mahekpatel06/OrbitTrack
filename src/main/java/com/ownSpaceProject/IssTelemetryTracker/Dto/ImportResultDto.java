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
public class ImportResultDto {

    private boolean success;
    private String message;
    private int totalRowsRead;
    private int insertedCount;
    private int updatedCount;
    private int skippedCount;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private List<String> importedStationNames = new ArrayList<>();
}
