package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.Dto.StatsDto;
import com.ownSpaceProject.IssTelemetryTracker.Jpa.GroundStJpaRepo;
import com.ownSpaceProject.IssTelemetryTracker.Jpa.IssAlertJpaRepository;
import com.ownSpaceProject.IssTelemetryTracker.Jpa.IssTelJpaRepository;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.GroundStation;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssAlert;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTelemetry;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TelemetryAnalyticsService {

    @Autowired
    private IssTelJpaRepository issTelJpaRepository;

    @Autowired
    private IssAlertJpaRepository issAlertJpaRepository;

    @Autowired
    private GroundStJpaRepo groundStJpaRepo;

    @Autowired
    private IssTleService issTleService;

    @Value("${app.satellite.norad-id:25544}")
    private int satId;

    public Optional<IssTelemetry> getLatestTelemetry() {
        Optional<IssTelemetry> opt = issTelJpaRepository.findTopByOrderByIndexDesc();
        opt.ifPresent(tel -> {
            if (tel.getVelocity() == null || tel.getVelocity() <= 0) {
                tel.setVelocity(7.66); // Average ISS orbital speed (km/s)
            }
            if (tel.getAltitude() == null || tel.getAltitude() <= 0) {
                tel.setAltitude(420.0); // Nominal ISS altitude (km)
            }
        });
        return opt;
    }

    public List<IssTelemetry> getTelemetryHistory(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500)); // Clamp between 1 and 500
        return issTelJpaRepository.findRecentTelemetry(PageRequest.of(0, boundedLimit));
    }

    public List<IssAlert> getAllAlerts() {
        return issAlertJpaRepository.findAllByOrderByStartTimestampDesc();
    }

    public Optional<IssAlert> getLatestAlert() {
        return issAlertJpaRepository.findTopByOrderByStartTimestampDesc();
    }

    public List<GroundStation> getAllGroundStations() {
        return groundStJpaRepo.findAll();
    }

    public IssTle getLatestTle() {
        return issTleService.getActiveTle(satId);
    }

    public StatsDto getStatistics() {
        Double totalDistance = issTelJpaRepository.getTotalDistanceTravelled();
        if (totalDistance == null) {
            totalDistance = 0.0;
        }

        long totalRecords = issTelJpaRepository.count();
        long daylightCount = issTelJpaRepository.countByVisibilityIgnoreCase("daylight");
        long eclipsedCount = issTelJpaRepository.countByVisibilityIgnoreCase("eclipsed");

        double daylightPercentage = 0.0;
        if (totalRecords > 0) {
            daylightPercentage = (double) daylightCount / totalRecords * 100.0;
        }

        long totalAlerts = issAlertJpaRepository.count();
        Long totalDuration = issAlertJpaRepository.getTotalAlertDurationSeconds();
        if (totalDuration == null) {
            totalDuration = 0L;
        }

        String mostVisitedStation = "None";
        long mostVisitedPassCount = 0L;

        List<Object[]> stationCounts = issAlertJpaRepository.findStationPassCounts();
        if (stationCounts != null && !stationCounts.isEmpty()) {
            Object[] topStation = stationCounts.get(0);
            mostVisitedStation = (String) topStation[0];
            mostVisitedPassCount = ((Number) topStation[1]).longValue();
        }

        Optional<IssTelemetry> latest = getLatestTelemetry();
        Double latestAlt = latest.map(IssTelemetry::getAltitude).orElse(null);
        Double latestVel = latest.map(IssTelemetry::getVelocity).orElse(null);
        Long latestTs = latest.map(IssTelemetry::getTimestamp).orElse(null);

        return StatsDto.builder()
                .totalDistanceTravelledKm(Math.round(totalDistance * 100.0) / 100.0)
                .totalTelemetryRecords(totalRecords)
                .daylightCount(daylightCount)
                .eclipsedCount(eclipsedCount)
                .daylightPercentage(Math.round(daylightPercentage * 10.0) / 10.0)
                .totalAlertsLogged(totalAlerts)
                .totalPassDurationSeconds(totalDuration)
                .mostVisitedGroundStation(mostVisitedStation)
                .mostVisitedGroundStationPassCount(mostVisitedPassCount)
                .latestAltitudeKm(latestAlt)
                .latestVelocityKmS(latestVel)
                .latestTimestamp(latestTs)
                .build();
    }
}
