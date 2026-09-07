package com.ownSpaceProject.IssTelemetryTracker.Scheduler;

import com.ownSpaceProject.IssTelemetryTracker.Dto.DisCoordDto;
import com.ownSpaceProject.IssTelemetryTracker.Dto.IssTelDto;
import com.ownSpaceProject.IssTelemetryTracker.Jpa.IssTelJpaRepository;
import com.ownSpaceProject.IssTelemetryTracker.Service.*;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.GroundStation;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTelemetry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TelScheduler {

    @Autowired
    private GroundStService groundStService;

    @Autowired
    private IssTrackerService issTrackerService;

    @Autowired
    private DistanceService distanceService;

    @Autowired
    private PassOverService passOverService;

    @Autowired
    private IssVelService issVelService;

    @Autowired
    private IssTleService issTleService;

    @Autowired
    private IssTelJpaRepository issTelJpaRepository;

    @Autowired
    private TelemetrySseService sseService;

    //    Imagine this list represents multiple stations across world
    private List<GroundStation> stations;

    DisCoordDto prevCoordinate = null;
    String lastVis = null;
    Integer daylightPassCount = 0;


    @EventListener(ApplicationReadyEvent.class)
    public void initGroundStations() {

        System.out.println("Initializing global tracking network stations into PostgreSQL...");

        if (this.stations == null || this.stations.isEmpty()) {
            this.stations = groundStService.saveGrndSt();
            System.out.println("Successfully registered " + this.stations.size() + " ground control stations.");
        } else {
            System.out.println("Ground stations already initialized. Skipping setup.");
        }

        // Initialize latest TLE for orbital propagator
        System.out.println("Fetching latest satellite Two-Line Element (TLE) ephemeris data...");
        issTleService.fetchAndSaveLatestTle(25544);
    }


    //  Recurring schedule to fetch updated TLE from Celestrak every 12 hours
    @Scheduled(cron = "0 0 */12 * * *")
    public void refreshTleData() {
        System.out.println("[SCHEDULED] Executing 12-hour automated TLE refresh from Celestrak...");
        issTleService.fetchAndSaveLatestTle(25544);
    }

    // Track satellite every 10 seconds for responsive telemetry updates
    @Scheduled(fixedRate = 10000)
    public void trackSatellite() {

        IssTelDto data = issTrackerService.fetchCurrentLocation();

        if (data == null) {
            System.out.println("Skipping insertion: Telemetry data unavailable from both API and Orekit propagator.");
            return;
        }

        Double distance = 0.0;

        IssTelemetry tel = new IssTelemetry();

        tel.setIssId(data.getIssId());
        tel.setLatitude(data.getLatitude());
        tel.setLongitude(data.getLongitude());
        tel.setVisibility(Boolean.parseBoolean(data.getVisibility()) ? "eclipsed" : (data.getVisibility() != null && data.getVisibility().equalsIgnoreCase("eclipsed") ? "eclipsed" : "daylight"));
        tel.setTimestamp(data.getTimestamp());

        // Use propagated velocity if already calculated, otherwise evaluate from Orekit
        double vel = (data.getVelocity() != null && data.getVelocity() > 0) ? data.getVelocity() : issVelService.issVelocity();
        tel.setVelocity(vel);

        tel.setAltitude(data.getAltitude());
//        tel.setUnits(data.getUnits());


        DisCoordDto curCoordinate = new DisCoordDto(tel.getLatitude(), tel.getLongitude(), tel.getAltitude());

        if (this.prevCoordinate != null) {
            distance = distanceService.calDis(prevCoordinate, curCoordinate);
        } else {
            System.out.println("Initializing orbital tracking tracker... Calculating distance on next cycle.");
        }

        tel.setTravelledDis(distance);

        this.prevCoordinate = curCoordinate;

        if (lastVis != null) {
            if (!lastVis.equals(tel.getVisibility()))
                daylightPassCount++;
        }

        lastVis = tel.getVisibility();
        issTelJpaRepository.save(tel);

        // Broadcast live telemetry update to all connected SSE clients
        sseService.broadcastTelemetry(tel);

        System.out.println("ISS current position : ");
        System.out.println("Latitude : " + tel.getLatitude() +
                        " \t " + "Longitude: " + tel.getLongitude() +
                        " \t " + "Altitude: " + tel.getAltitude() +
//                " \t" + "Visibility: " + data.getVisibility() +
                        "\t" + "TimeStamp: " + tel.getTimestamp()
//                "\t" + "Velocity: " + data.getVelocity() +
//                "\t" + "Units: " + data.getVelocity()
        );
        System.out.printf("ISS traveled %.4f km in the last 10 seconds.%n", distance);

        double currentLat = tel.getLatitude();
        double currentLon = tel.getLongitude();

//        // STREAM LOGIC: Check if the ISS is over ANY of our ground stations

        passOverService.passOver(stations, currentLat, currentLon);

    }

    @PreDestroy
    public void CountdaylightPass() {
        System.out.println("Final Total Daylight Count for Satellite : " + daylightPassCount);
    }
}

