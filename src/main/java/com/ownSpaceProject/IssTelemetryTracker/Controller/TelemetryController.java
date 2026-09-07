package com.ownSpaceProject.IssTelemetryTracker.Controller;

import com.ownSpaceProject.IssTelemetryTracker.Dto.ImportResultDto;
import com.ownSpaceProject.IssTelemetryTracker.Dto.PassPredictionDto;
import com.ownSpaceProject.IssTelemetryTracker.Dto.StatsDto;
import com.ownSpaceProject.IssTelemetryTracker.Service.GroundStationExcelService;
import com.ownSpaceProject.IssTelemetryTracker.Service.PassPredictionService;
import com.ownSpaceProject.IssTelemetryTracker.Service.TelemetryAnalyticsService;
import com.ownSpaceProject.IssTelemetryTracker.Service.TelemetrySseService;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.GroundStation;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssAlert;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTelemetry;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TelemetryController {

    @Autowired
    private TelemetryAnalyticsService analyticsService;

    @Autowired
    private TelemetrySseService sseService;

    @Autowired
    private GroundStationExcelService excelService;

    @Autowired
    private PassPredictionService passPredictionService;

    /**
     * GET /api/stream
     * Opens a real-time Server-Sent Events (SSE) stream for live telemetry & alerts.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamTelemetry() {
        return sseService.createEmitter();
    }

    /**
     * GET /api/telemetry/latest
     * Returns the most recent satellite telemetry snapshot.
     */
    @GetMapping("/telemetry/latest")
    public ResponseEntity<IssTelemetry> getLatestTelemetry() {
        return analyticsService.getLatestTelemetry()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * GET /api/telemetry/history?limit=100
     * Returns historical telemetry records.
     */
    @GetMapping("/telemetry/history")
    public ResponseEntity<List<IssTelemetry>> getTelemetryHistory(
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return ResponseEntity.ok(analyticsService.getTelemetryHistory(limit));
    }

    /**
     * GET /api/alerts
     * Returns all historical ground station pass-over alerts.
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<IssAlert>> getAllAlerts() {
        return ResponseEntity.ok(analyticsService.getAllAlerts());
    }

    /**
     * GET /api/alerts/latest
     * Returns the latest ground station pass alert.
     */
    @GetMapping("/alerts/latest")
    public ResponseEntity<IssAlert> getLatestAlert() {
        return analyticsService.getLatestAlert()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * GET /api/ground-stations
     * Returns all registered tracking stations in the global network.
     */
    @GetMapping("/ground-stations")
    public ResponseEntity<List<GroundStation>> getAllGroundStations() {
        return ResponseEntity.ok(analyticsService.getAllGroundStations());
    }

    /**
     * POST /api/ground-stations/upload
     * Uploads an Excel (.xlsx/.xls) file and bulk-imports global tracking stations.
     */
    @PostMapping(value = "/ground-stations/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDto> uploadGroundStations(@RequestParam("file") MultipartFile file) {
        ImportResultDto result = excelService.parseAndSaveStations(file);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/ground-stations/template
     * Generates and downloads a formatted sample Excel template (.xlsx) for ground stations.
     */
    @GetMapping("/ground-stations/template")
    public ResponseEntity<byte[]> downloadGroundStationTemplate() {
        byte[] excelBytes = excelService.generateSampleTemplate();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ground_stations_template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    /**
     * GET /api/statistics
     * Returns key telemetry analytics and metrics.
     */
    @GetMapping("/statistics")
    public ResponseEntity<StatsDto> getStatistics() {
        return ResponseEntity.ok(analyticsService.getStatistics());
    }

    /**
     * GET /api/tle/latest
     * Returns current Two-Line Element (TLE) ephemeris used by the orbital propagator.
     */
    @GetMapping("/tle/latest")
    public ResponseEntity<IssTle> getLatestTle() {
        return ResponseEntity.ok(analyticsService.getLatestTle());
    }

    @Autowired
    private com.ownSpaceProject.IssTelemetryTracker.Service.MissionReportService missionReportService;

    /**
     * GET /api/passes/predict
     * Predicts upcoming visible and radio flyovers (AOS, LOS, max elevation)
     * over a target city or ground station coordinate for the next 1 to 5 days.
     */
    @GetMapping("/passes/predict")
    public ResponseEntity<com.ownSpaceProject.IssTelemetryTracker.Dto.PassPredictionDto> predictPasses(
            @RequestParam(name = "lat") Double lat,
            @RequestParam(name = "lon") Double lon,
            @RequestParam(name = "name", defaultValue = "Target Location") String name,
            @RequestParam(name = "days", defaultValue = "2") int days) {
        return ResponseEntity.ok(passPredictionService.predictPasses(lat, lon, name, days));
    }

    /**
     * GET /api/reports/mission-audit-pdf
     * Generates and downloads structured PDF Mission Audit Report.
     */
    @GetMapping("/reports/mission-audit-pdf")
    public ResponseEntity<byte[]> downloadMissionAuditPdf() {
        byte[] pdfBytes = missionReportService.generateMissionAuditPdf();
        String filename = "iss-mission-audit-report-" + System.currentTimeMillis() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }

    /**
     * GET /api/reports/mission-audit-excel
     * Generates and downloads structured multi-sheet Excel (.xlsx) Mission Audit Workbook.
     */
    @GetMapping("/reports/mission-audit-excel")
    public ResponseEntity<byte[]> downloadMissionAuditExcel() {
        byte[] excelBytes = missionReportService.generateMissionAuditExcel();
        String filename = "iss-mission-audit-report-" + System.currentTimeMillis() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(excelBytes.length)
                .body(excelBytes);
    }
}
