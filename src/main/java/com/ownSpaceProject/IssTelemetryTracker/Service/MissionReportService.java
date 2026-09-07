package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.ownSpaceProject.IssTelemetryTracker.Jpa.GroundStJpaRepo;
import com.ownSpaceProject.IssTelemetryTracker.Jpa.IssAlertJpaRepository;
import com.ownSpaceProject.IssTelemetryTracker.Jpa.IssTelJpaRepository;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.GroundStation;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssAlert;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTelemetry;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MissionReportService {

    private final IssTelJpaRepository issTelRepo;
    private final IssAlertJpaRepository issAlertRepo;
    private final GroundStJpaRepo groundStRepo;
    private final DistanceService distanceService;

    private static final DateTimeFormatter IST_FORMATTER = DateTimeFormatter
            .ofPattern("dd MMM yyyy, hh:mm:ss a")
            .withZone(ZoneId.of("Asia/Kolkata"));

    /**
     * Generates a structured multi-page PDF Mission Audit Report.
     */
    public byte[] generateMissionAuditPdf() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            // Color Palette
            Color navyHeader = new Color(15, 23, 42); // #0f172a
            Color cyanAccent = new Color(2, 132, 199); // #0284c7
            Color darkMuted = new Color(71, 85, 105); // #475569
            Color lightRow = new Color(248, 250, 252);
            Color altRow = new Color(241, 245, 249);

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, navyHeader);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, cyanAccent);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 8, darkMuted);
            Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, navyHeader);
            Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
            Font tdBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);

            // Document Header
            Paragraph title = new Paragraph("INTERNATIONAL SPACE STATION (ISS)", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph sub = new Paragraph("MISSION AUDIT & TELEMETRY PASS LOG REPORT", subtitleFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            document.add(sub);

            String generationDateIst = IST_FORMATTER.format(Instant.now()) + " IST";
            Paragraph meta = new Paragraph("Generated On: " + generationDateIst + "  |  Orbit Tracking Model: SGP4 Ephemeris", metaFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(10);
            document.add(meta);

            LineSeparator line = new LineSeparator(1, 100, cyanAccent, Element.ALIGN_CENTER, -2);
            document.add(line);
            document.add(new Paragraph(" "));

            // Section 1: Executive Mission Overview
            Paragraph sec1 = new Paragraph("1. EXECUTIVE ORBITAL FLIGHT METRICS", sectionTitleFont);
            sec1.setSpacingAfter(6);
            document.add(sec1);

            Optional<IssTelemetry> latestTelOpt = issTelRepo.findTopByOrderByIndexDesc();
            Double totalDistance = issTelRepo.getTotalDistanceTravelled();
            long totalPings = issTelRepo.count();
            long dayPings = issTelRepo.countByVisibilityIgnoreCase("daylight");
            long totalAlerts = issAlertRepo.count();
            Long totalAlertSec = issAlertRepo.getTotalAlertDurationSeconds();

            PdfPTable statsTable = new PdfPTable(4);
            statsTable.setWidthPercentage(100);
            statsTable.setWidths(new float[]{2.5f, 2.5f, 2.5f, 2.5f});

            addStatCell(statsTable, "Cumulative Distance", String.format("%.1f km", totalDistance != null ? totalDistance : 0.0), thFont, navyHeader);
            addStatCell(statsTable, "Telemetry Pings", String.format("%,d", totalPings), thFont, navyHeader);
            addStatCell(statsTable, "Geofence Passes", String.format("%,d passes", totalAlerts), thFont, navyHeader);
            addStatCell(statsTable, "Contact Time", formatDuration(totalAlertSec != null ? totalAlertSec : 0), thFont, navyHeader);

            if (latestTelOpt.isPresent()) {
                IssTelemetry tel = latestTelOpt.get();
                addStatCell(statsTable, "Current Speed", String.format("%.2f km/s", tel.getVelocity() != null ? tel.getVelocity() : 7.66), thFont, cyanAccent);
                addStatCell(statsTable, "Current Altitude", String.format("%.2f km", tel.getAltitude() != null ? tel.getAltitude() : 420.0), thFont, cyanAccent);
                addStatCell(statsTable, "Current Position", String.format("%.2f°, %.2f°", tel.getLatitude(), tel.getLongitude()), thFont, cyanAccent);
                addStatCell(statsTable, "Solar Exposure", tel.getVisibility() != null ? tel.getVisibility().toUpperCase() : "DAYLIGHT", thFont, cyanAccent);
            }
            document.add(statsTable);
            document.add(new Paragraph(" "));

            // Section 2: Historical Geofence Pass Logs
            Paragraph sec2 = new Paragraph("2. GROUND STATION GEOFENCE PASS LOGS (HISTORICAL AUDIT)", sectionTitleFont);
            sec2.setSpacingAfter(6);
            document.add(sec2);

            List<IssAlert> alerts = issAlertRepo.findAllByOrderByStartTimestampDesc();
            PdfPTable alertTable = new PdfPTable(6);
            alertTable.setWidthPercentage(100);
            alertTable.setWidths(new float[]{2.2f, 2.2f, 2.2f, 1.2f, 1.8f, 1.8f});

            addHeaderCell(alertTable, "Ground Station", thFont, navyHeader);
            addHeaderCell(alertTable, "AOS Time (IST)", thFont, navyHeader);
            addHeaderCell(alertTable, "LOS Time (IST)", thFont, navyHeader);
            addHeaderCell(alertTable, "Duration", thFont, navyHeader);
            addHeaderCell(alertTable, "Entry Coordinates", thFont, navyHeader);
            addHeaderCell(alertTable, "Exit Coordinates", thFont, navyHeader);

            int maxRows = Math.min(alerts.size(), 30);
            for (int i = 0; i < maxRows; i++) {
                IssAlert a = alerts.get(i);
                Color rowBg = (i % 2 == 0) ? lightRow : altRow;

                addBodyCell(alertTable, a.getStationName(), tdBoldFont, rowBg);
                addBodyCell(alertTable, a.getStartTimestamp() != null ? a.getStartTimestamp().format(DateTimeFormatter.ofPattern("dd MMM, hh:mm:ss a")) : "N/A", tdFont, rowBg);
                addBodyCell(alertTable, a.getEndTimestamp() != null ? a.getEndTimestamp().format(DateTimeFormatter.ofPattern("dd MMM, hh:mm:ss a")) : "In Contact", tdFont, rowBg);
                addBodyCell(alertTable, a.getDuration() > 0 ? a.getDuration() + "s" : "Active", tdFont, rowBg);
                addBodyCell(alertTable, String.format("%.2f°, %.2f°", a.getStartLatitude(), a.getStartLongitude()), tdFont, rowBg);
                addBodyCell(alertTable, a.getEndLatitude() != 0.0 ? String.format("%.2f°, %.2f°", a.getEndLatitude(), a.getEndLongitude()) : "--", tdFont, rowBg);
            }
            if (alerts.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No geofence passes recorded in database yet.", tdFont));
                emptyCell.setColspan(6);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                emptyCell.setPadding(8);
                alertTable.addCell(emptyCell);
            }
            document.add(alertTable);
            document.add(new Paragraph(" "));

            // Section 3: Station Contact Distribution & Analytics
            Paragraph sec3 = new Paragraph("3. GROUND STATION CONTACT DISTRIBUTION", sectionTitleFont);
            sec3.setSpacingAfter(6);
            document.add(sec3);

            List<Object[]> stationPasses = issAlertRepo.findStationPassCounts();
            PdfPTable distTable = new PdfPTable(3);
            distTable.setWidthPercentage(100);
            distTable.setWidths(new float[]{4f, 2f, 2f});

            addHeaderCell(distTable, "Ground Station Name", thFont, navyHeader);
            addHeaderCell(distTable, "Total Passes Logged", thFont, navyHeader);
            addHeaderCell(distTable, "Network Status", thFont, navyHeader);

            int distRows = Math.min(stationPasses.size(), 15);
            for (int i = 0; i < distRows; i++) {
                Object[] row = stationPasses.get(i);
                Color rowBg = (i % 2 == 0) ? lightRow : altRow;
                String stName = String.valueOf(row[0]);
                long count = ((Number) row[1]).longValue();

                addBodyCell(distTable, stName, tdBoldFont, rowBg);
                addBodyCell(distTable, count + " passes", tdFont, rowBg);
                addBodyCell(distTable, "OPERATIONAL", tdFont, rowBg);
            }
            if (stationPasses.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No station contact distribution data yet.", tdFont));
                emptyCell.setColspan(3);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                emptyCell.setPadding(8);
                distTable.addCell(emptyCell);
            }
            document.add(distTable);

            // Document Footer Note
            Paragraph footer = new Paragraph("\n* Official Telemetry Record. All coordinates WGS-84 ellipsoid. Time formatted in Asia/Kolkata (IST).", metaFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Mission Audit PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a structured multi-sheet Excel (.xlsx) Mission Audit Workbook.
     */
    public byte[] generateMissionAuditExcel() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Colors & Fonts
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 10);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            CellStyle boldStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);

            // -----------------------------------------------------------------
            // SHEET 1: Mission Overview
            // -----------------------------------------------------------------
            Sheet sheet1 = workbook.createSheet("Mission Overview");
            int r = 0;
            Row titleRow = sheet1.createRow(r++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("INTERNATIONAL SPACE STATION (ISS) - MISSION AUDIT REPORT");
            titleCell.setCellStyle(boldStyle);

            Row metaRow = sheet1.createRow(r++);
            metaRow.createCell(0).setCellValue("Generated at: " + IST_FORMATTER.format(Instant.now()) + " IST");
            r++; // blank row

            // Summary Table
            Row thRow = sheet1.createRow(r++);
            thRow.createCell(0).setCellValue("Mission Metric");
            thRow.createCell(1).setCellValue("Value");
            thRow.createCell(2).setCellValue("Unit / Description");
            for (int i = 0; i < 3; i++) thRow.getCell(i).setCellStyle(headerStyle);

            Double totalDistance = issTelRepo.getTotalDistanceTravelled();
            long totalPings = issTelRepo.count();
            long dayPings = issTelRepo.countByVisibilityIgnoreCase("daylight");
            long totalAlerts = issAlertRepo.count();
            Long totalAlertSec = issAlertRepo.getTotalAlertDurationSeconds();
            long totalStations = groundStRepo.count();
            Optional<IssTelemetry> latest = issTelRepo.findTopByOrderByIndexDesc();

            addExcelSummaryRow(sheet1, r++, "Cumulative Distance Travelled", String.format("%.2f", totalDistance != null ? totalDistance : 0.0), "Kilometers", dataStyle);
            addExcelSummaryRow(sheet1, r++, "Total Telemetry Pings", String.valueOf(totalPings), "Records logged", dataStyle);
            addExcelSummaryRow(sheet1, r++, "Total Geofence Passes", String.valueOf(totalAlerts), "Passes into 2,000 km zone", dataStyle);
            addExcelSummaryRow(sheet1, r++, "Total Airspace Contact Time", formatDuration(totalAlertSec != null ? totalAlertSec : 0), "Aggregated duration", dataStyle);
            addExcelSummaryRow(sheet1, r++, "Global Ground Stations Registered", String.valueOf(totalStations), "Tracking stations in network", dataStyle);

            if (latest.isPresent()) {
                IssTelemetry tel = latest.get();
                addExcelSummaryRow(sheet1, r++, "Current Orbital Velocity", String.format("%.2f", tel.getVelocity()), "km/s", dataStyle);
                addExcelSummaryRow(sheet1, r++, "Current Altitude", String.format("%.2f", tel.getAltitude()), "km", dataStyle);
                addExcelSummaryRow(sheet1, r++, "Current Latitude", String.format("%.4f", tel.getLatitude()), "Degrees", dataStyle);
                addExcelSummaryRow(sheet1, r++, "Current Longitude", String.format("%.4f", tel.getLongitude()), "Degrees", dataStyle);
                addExcelSummaryRow(sheet1, r++, "Current Solar Exposure", tel.getVisibility() != null ? tel.getVisibility().toUpperCase() : "DAYLIGHT", "Status", dataStyle);
            }

            for (int i = 0; i < 3; i++) sheet1.autoSizeColumn(i);

            // -----------------------------------------------------------------
            // SHEET 2: Geofence Pass History
            // -----------------------------------------------------------------
            Sheet sheet2 = workbook.createSheet("Geofence Pass History");
            int r2 = 0;
            Row passHead = sheet2.createRow(r2++);
            String[] passCols = {"Pass ID", "Station Name", "AOS Time (IST)", "LOS Time (IST)", "Duration (s)", "Duration Formatted", "Entry Lat", "Entry Lon", "Exit Lat", "Exit Lon"};
            for (int i = 0; i < passCols.length; i++) {
                Cell c = passHead.createCell(i);
                c.setCellValue(passCols[i]);
                c.setCellStyle(headerStyle);
            }

            List<IssAlert> alerts = issAlertRepo.findAllByOrderByStartTimestampDesc();
            for (IssAlert a : alerts) {
                Row row = sheet2.createRow(r2++);
                row.createCell(0).setCellValue(a.getId() != null ? a.getId() : 0);
                row.createCell(1).setCellValue(a.getStationName() != null ? a.getStationName() : "Unknown");
                row.createCell(2).setCellValue(a.getStartTimestamp() != null ? a.getStartTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " IST" : "N/A");
                row.createCell(3).setCellValue(a.getEndTimestamp() != null ? a.getEndTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " IST" : "In Contact");
                row.createCell(4).setCellValue(a.getDuration());
                row.createCell(5).setCellValue(formatDuration(a.getDuration()));
                row.createCell(6).setCellValue(a.getStartLatitude());
                row.createCell(7).setCellValue(a.getStartLongitude());
                row.createCell(8).setCellValue(a.getEndLatitude());
                row.createCell(9).setCellValue(a.getEndLongitude());

                for (int i = 0; i < passCols.length; i++) row.getCell(i).setCellStyle(dataStyle);
            }
            for (int i = 0; i < passCols.length; i++) sheet2.autoSizeColumn(i);

            // -----------------------------------------------------------------
            // SHEET 3: Station Contact Analytics
            // -----------------------------------------------------------------
            Sheet sheet3 = workbook.createSheet("Station Analytics");
            int r3 = 0;
            Row stHead = sheet3.createRow(r3++);
            String[] stCols = {"Station Name", "Total Passes Logged", "Status"};
            for (int i = 0; i < stCols.length; i++) {
                Cell c = stHead.createCell(i);
                c.setCellValue(stCols[i]);
                c.setCellStyle(headerStyle);
            }

            List<Object[]> stationPasses = issAlertRepo.findStationPassCounts();
            for (Object[] sp : stationPasses) {
                Row row = sheet3.createRow(r3++);
                row.createCell(0).setCellValue(String.valueOf(sp[0]));
                row.createCell(1).setCellValue(((Number) sp[1]).longValue());
                row.createCell(2).setCellValue("OPERATIONAL");
                for (int i = 0; i < stCols.length; i++) row.getCell(i).setCellStyle(dataStyle);
            }
            for (int i = 0; i < stCols.length; i++) sheet3.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Mission Audit Excel: " + e.getMessage(), e);
        }
    }

    // Helper: PDF Header Cell
    private void addHeaderCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
        table.addCell(cell);
    }

    // Helper: PDF Body Cell
    private void addBodyCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        table.addCell(cell);
    }

    // Helper: PDF Stat Cell
    private void addStatCell(PdfPTable table, String title, String value, Font font, Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        Paragraph p1 = new Paragraph(title.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.LIGHT_GRAY));
        Paragraph p2 = new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE));
        cell.addElement(p1);
        cell.addElement(p2);
        table.addCell(cell);
    }

    // Helper: Excel Row
    private void addExcelSummaryRow(Sheet sheet, int rowIdx, String metric, String val, String desc, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        Cell c0 = row.createCell(0);
        c0.setCellValue(metric);
        c0.setCellStyle(style);

        Cell c1 = row.createCell(1);
        c1.setCellValue(val);
        c1.setCellStyle(style);

        Cell c2 = row.createCell(2);
        c2.setCellValue(desc);
        c2.setCellStyle(style);
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";
        long m = seconds / 60;
        long s = seconds % 60;
        if (m > 0) return String.format("%dm %02ds", m, s);
        return String.format("%ds", s);
    }
}
