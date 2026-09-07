package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.Dto.ImportResultDto;
import com.ownSpaceProject.IssTelemetryTracker.Jpa.GroundStJpaRepo;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.GroundStation;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GroundStationExcelService {

    @Autowired
    private GroundStJpaRepo groundStJpaRepo;

    /**
     * Parses an uploaded Excel (.xlsx/.xls) file, validates station coordinates,
     * and saves or updates GroundStation records in PostgreSQL.
     */
    public ImportResultDto parseAndSaveStations(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ImportResultDto.builder()
                    .success(false)
                    .message("Uploaded file is empty or missing.")
                    .build();
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            return ImportResultDto.builder()
                    .success(false)
                    .message("Invalid file format. Please upload a valid Microsoft Excel file (.xlsx or .xls).")
                    .build();
        }

        List<String> errors = new ArrayList<>();
        List<String> importedNames = new ArrayList<>();
        int totalRows = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                return ImportResultDto.builder()
                        .success(false)
                        .message("The Excel sheet is empty or contains no data rows.")
                        .build();
            }

            // Header row analysis (Row 0)
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return ImportResultDto.builder()
                        .success(false)
                        .message("Header row not found in the uploaded sheet.")
                        .build();
            }

            int colName = -1;
            int colCountry = -1;
            int colLat = -1;
            int colLon = -1;

            for (Cell cell : headerRow) {
                String header = getCellValueAsString(cell).trim().toLowerCase();
                if (header.contains("station") || header.contains("name")) {
                    colName = cell.getColumnIndex();
                } else if (header.contains("country") || header.contains("nation")) {
                    colCountry = cell.getColumnIndex();
                } else if (header.contains("lat")) {
                    colLat = cell.getColumnIndex();
                } else if (header.contains("lon") || header.contains("long")) {
                    colLon = cell.getColumnIndex();
                }
            }

            // Verify essential columns
            if (colName == -1 || colLat == -1 || colLon == -1) {
                return ImportResultDto.builder()
                        .success(false)
                        .message("Missing required columns. Excel header must contain: 'Station Name', 'Country', 'Latitude', and 'Longitude'.")
                        .build();
            }

            // Iterate data rows (Row 1 to last)
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                totalRows++;
                int displayRow = r + 1; // 1-indexed for user-friendly error messages

                // 1. Station Name
                String name = getCellValueAsString(row.getCell(colName)).trim();
                if (name.isEmpty()) {
                    errors.add("Row " + displayRow + ": Station Name is blank. Skipped.");
                    skipped++;
                    continue;
                }

                // 2. Country
                String country = (colCountry != -1) ? getCellValueAsString(row.getCell(colCountry)).trim() : "International";
                if (country.isEmpty()) {
                    country = "International";
                }

                // 3. Latitude
                Double lat = getCellValueAsDouble(row.getCell(colLat));
                if (lat == null || lat < -90.0 || lat > 90.0) {
                    errors.add("Row " + displayRow + " ('" + name + "'): Invalid Latitude. Must be a decimal between -90.0 and 90.0.");
                    skipped++;
                    continue;
                }

                // 4. Longitude
                Double lon = getCellValueAsDouble(row.getCell(colLon));
                if (lon == null || lon < -180.0 || lon > 180.0) {
                    errors.add("Row " + displayRow + " ('" + name + "'): Invalid Longitude. Must be a decimal between -180.0 and 180.0.");
                    skipped++;
                    continue;
                }

                // Upsert: check if station already exists
                Optional<GroundStation> existingOpt = groundStJpaRepo.findByNameIgnoreCase(name);
                GroundStation station;
                if (existingOpt.isPresent()) {
                    station = existingOpt.get();
                    station.setCountry(country);
                    station.setLatitude(lat);
                    station.setLongitude(lon);
                    updated++;
                } else {
                    station = new GroundStation(name, country, lat, lon);
                    inserted++;
                }

                groundStJpaRepo.save(station);
                importedNames.add(name);
            }

        } catch (Exception e) {
            return ImportResultDto.builder()
                    .success(false)
                    .message("Failed to process Excel file: " + e.getMessage())
                    .errors(List.of(e.getMessage()))
                    .build();
        }

        String msg = String.format("Processed %d rows: %d inserted, %d updated, %d skipped.",
                totalRows, inserted, updated, skipped);

        return ImportResultDto.builder()
                .success(inserted > 0 || updated > 0 || totalRows == 0)
                .message(msg)
                .totalRowsRead(totalRows)
                .insertedCount(inserted)
                .updatedCount(updated)
                .skippedCount(skipped)
                .errors(errors)
                .importedStationNames(importedNames)
                .build();
    }

    /**
     * Generates an official, pre-formatted Excel template (.xlsx)
     * containing real-world space agency ground tracking networks.
     */
    public byte[] generateSampleTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Global Ground Stations");

            // Header styling
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Data styling
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle coordStyle = workbook.createCellStyle();
            coordStyle.setDataFormat(workbook.createDataFormat().getFormat("0.0000"));
            coordStyle.setAlignment(HorizontalAlignment.RIGHT);

            // Create Header Row
            Row header = sheet.createRow(0);
            header.setHeightInPoints(24);

            String[] columns = {"Station Name", "Country", "Latitude", "Longitude", "Network / Agency", "Geofence Radius (km)"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Real-world comprehensive catalog of 46 global space tracking stations
            Object[][] sampleData = {
                    // ISRO (India & Global Stations)
                    {"ISRO ISTRAC Peenya Bengaluru Hub", "India", 13.0343, 77.5115, "ISRO ISTRAC", 2000},
                    {"ISRO Indian Deep Space Network (IDSN) Bylalu", "India", 12.8700, 77.3600, "ISRO DSN", 2000},
                    {"Satish Dhawan Space Centre (SDSC SHAR) Sriharikota", "India", 13.7196, 80.2304, "ISRO SHAR", 2000},
                    {"Thiruvananthapuram Down Range Station (VSSC)", "India", 8.5364, 76.8639, "ISRO VSSC", 2000},
                    {"Space Applications Centre (SAC) Ahmedabad Hub", "India", 23.0366, 72.5458, "ISRO SAC", 2000},
                    {"Port Blair Satellite Tracking Station (Andaman)", "India", 11.6234, 92.7265, "ISRO ISTRAC", 2000},
                    {"ISTRAC Lucknow Ground Station", "India", 26.8467, 80.9462, "ISRO ISTRAC", 2000},
                    {"ISTRAC Mauritius Ground Station", "Mauritius", -20.2540, 57.5750, "ISRO International", 2000},
                    {"ISTRAC Brunei Tracking Station", "Brunei", 4.9031, 114.9398, "ISRO International", 2000},
                    {"ISTRAC Biak Ground Station", "Indonesia", -1.1822, 136.0828, "ISRO International", 2000},
                    {"Bharati Research Station Ground Terminal", "Antarctica", -69.4075, 76.1872, "ISRO Polar", 2000},

                    // NASA (Deep Space Network & Near-Earth Space Network)
                    {"NASA Goldstone Deep Space Communications Complex", "United States", 35.4267, -116.8890, "NASA DSN", 2000},
                    {"NASA Madrid Deep Space Communications Complex", "Spain", 40.4314, -4.2480, "NASA DSN", 2000},
                    {"NASA Canberra Deep Space Communication Complex", "Australia", -35.4014, 148.9817, "NASA DSN", 2000},
                    {"NASA White Sands Ground Terminal", "United States", 32.5407, -106.6119, "NASA Space Network", 2000},
                    {"Merritt Island Launch Tracking Annex (KSC)", "United States", 28.5326, -80.6404, "NASA KSC", 2000},
                    {"NASA Guam Tracking Station", "Guam", 13.5975, 144.8540, "NASA Space Network", 2000},
                    {"NASA Wallops Flight Facility Tracking Station", "United States", 37.9402, -75.4664, "NASA WFF", 2000},
                    {"Fairbanks Command & Data Acquisition Station", "United States", 64.9744, -147.5186, "NOAA / NASA", 2000},
                    {"McMurdo Ground Station", "Antarctica", -77.8402, 166.6874, "NASA / NSF", 2000},

                    // ESA ESTRACK (European Space Tracking Network)
                    {"ESA Cebreros Deep Space Station", "Spain", 40.4528, -4.3675, "ESA ESTRACK", 2000},
                    {"ESA New Norcia Deep Space Station", "Australia", -31.0483, 116.1914, "ESA ESTRACK", 2000},
                    {"ESA Malargüe Deep Space Station", "Argentina", -35.7760, -69.3980, "ESA ESTRACK", 2000},
                    {"ESA Santa Maria Tracking Station", "Portugal", 36.9972, -25.1361, "ESA ESTRACK", 2000},
                    {"ESA Operations Centre (ESOC) Darmstadt", "Germany", 49.8787, 8.6482, "ESA ESOC", 2000},
                    {"Kourou Guiana Space Centre Station", "French Guiana", 5.2514, -52.7444, "ESA / CNES", 2000},
                    {"Malindi Tracking Station", "Kenya", -2.9956, 40.1945, "ESA / ASI", 2000},
                    {"Redu European Space Security and Education Centre", "Belgium", 50.0016, 5.1467, "ESA ESTRACK", 2000},
                    {"SSC Kiruna Satellite Station", "Sweden", 67.8557, 20.9639, "Swedish Space Corp", 2000},
                    {"KSAT Svalbard Satellite Station (SvalSat)", "Norway", 78.2298, 15.4078, "KSAT Polar Network", 2000},
                    {"Troll Satellite Station", "Antarctica", -72.0114, 2.5350, "KSAT Polar Network", 2000},

                    // JAXA (Japan Aerospace Exploration Agency)
                    {"JAXA Tsukuba Space Center", "Japan", 36.0650, 140.1270, "JAXA Tracking", 2000},
                    {"JAXA Katsuura Tracking Station", "Japan", 35.1583, 140.3017, "JAXA Tracking", 2000},
                    {"JAXA Tanegashima Space Center Tracking Station", "Japan", 30.3750, 130.9581, "JAXA Tracking", 2000},
                    {"JAXA Masuda Tracking and Communication Station", "Japan", 30.5567, 131.0150, "JAXA Tracking", 2000},
                    {"JAXA Okinawa Tracking Station", "Japan", 26.5008, 127.9556, "JAXA Tracking", 2000},

                    // Major Global Tracking Observatories & Networks
                    {"Hartebeesthoek Radio Astronomy Observatory", "South Africa", -25.8897, 27.7072, "SANSA / HartRAO", 2000},
                    {"Awarua Satellite Tracking Ground Station", "New Zealand", -46.5292, 168.3792, "SpaceOps NZ", 2000},
                    {"Inuvik Satellite Station Facility", "Canada", 68.3060, -133.5350, "CCRS / SSC Canada", 2000},
                    {"Gatineau Satellite Station", "Canada", 45.5861, -75.8089, "CCRS Canada", 2000},
                    {"Prince Albert Satellite Station", "Canada", 53.2125, -105.9339, "CCRS Canada", 2000},
                    {"Weilheim Ground Station (DLR)", "Germany", 47.8817, 11.0847, "DLR Germany", 2000},
                    {"INPE Cuiabá Satellite Tracking Station", "Brazil", -15.5550, -56.0700, "INPE Brazil", 2000},
                    {"Perth Satellite Ground Station", "Australia", -31.8105, 115.8950, "SSC Australia", 2000},
                    {"Santiago Satellite Tracking Station", "Chile", -33.1500, -70.6667, "SSC Chile", 2000},
                    {"Singapore Satellite Ground Station", "Singapore", 1.3521, 103.8198, "CRISP Singapore", 2000}
            };

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.setHeightInPoints(20);

                Object[] data = sampleData[r];

                Cell cName = row.createCell(0);
                cName.setCellValue((String) data[0]);
                cName.setCellStyle(dataStyle);

                Cell cCountry = row.createCell(1);
                cCountry.setCellValue((String) data[1]);
                cCountry.setCellStyle(dataStyle);

                Cell cLat = row.createCell(2);
                cLat.setCellValue(((Number) data[2]).doubleValue());
                cLat.setCellStyle(coordStyle);

                Cell cLon = row.createCell(3);
                cLon.setCellValue(((Number) data[3]).doubleValue());
                cLon.setCellStyle(coordStyle);

                Cell cNet = row.createCell(4);
                cNet.setCellValue((String) data[4]);
                cNet.setCellStyle(dataStyle);

                Cell cRad = row.createCell(5);
                cRad.setCellValue(((Number) data[5]).intValue());
                cRad.setCellStyle(dataStyle);
            }

            // Auto-size columns for readability
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i), 3500));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate ground station Excel template: " + e.getMessage(), e);
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    yield String.valueOf((long) num);
                }
                yield String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case FORMULA -> {
                try {
                    yield cell.getNumericCellValue();
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellValueAsString(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
