package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.Jpa.IssAlertJpaRepository;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.GroundStation;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssAlert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PassOverService {

    @Autowired
    private IssAlertJpaRepository issAlertRepository;

    @Autowired
    private TelemetrySseService sseService;

    @Value("${app.geofence.max-distance-km:2000.0}")
    private double maxDistanceKm;

    private static final double EARTH_RADIUS_KM = 6371.0;

    // Map keeps track of [Station Name -> Full Entry Details]
    private final Map<String, ActivePassDetails> activePasses = new ConcurrentHashMap<>();

    public void passOver(List<GroundStation> stations, double currentLat, double currentLon) {

        // 1. Find ALL stations currently within maxDistanceKm geofence
        java.util.Set<String> currentlyOverStations = stations.stream()
                .filter(station -> {
                    double lat1Rad = Math.toRadians(currentLat);
                    double lat2Rad = Math.toRadians(station.getLatitude());
                    double deltaLat = Math.toRadians(station.getLatitude() - currentLat);
                    double deltaLon = Math.toRadians(station.getLongitude() - currentLon);

                    double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                            Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                                    Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

                    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                    double distance = EARTH_RADIUS_KM * c;

                    return distance <= maxDistanceKm;
                })
                .map(GroundStation::getName)
                .collect(java.util.stream.Collectors.toSet());

        // 2. Handle ENTRY State for all newly entered stations
        for (String stationName : currentlyOverStations) {
            if (!activePasses.containsKey(stationName)) {
                LocalDateTime startTime = LocalDateTime.now();
                ActivePassDetails details = new ActivePassDetails(startTime, currentLat, currentLon);
                activePasses.put(stationName, details);

                System.out.printf("ALERT START: ISS entered %s airspace at %s (Lat: %.4f, Lon: %.4f)%n",
                        stationName, startTime, currentLat, currentLon);
            }
        }

        // 3. Handle EXIT State: Collect entry details, pair with exit data, compute runtime metrics
        Iterator<Map.Entry<String, ActivePassDetails>> iterator = activePasses.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ActivePassDetails> activePass = iterator.next();
            String trackedStationName = activePass.getKey();
            ActivePassDetails entryDetails = activePass.getValue();

            // If the ISS is no longer over this tracked station, it has officially exited
            if (!currentlyOverStations.contains(trackedStationName)) {
                LocalDateTime endTime = LocalDateTime.now();
                long calculatedDuration = Duration.between(entryDetails.startTime(), endTime).toSeconds();

                System.out.printf("ALERT END: ISS left %s. Duration: %d seconds. Exit Position (Lat: %.4f, Lon: %.4f)%n",
                        trackedStationName, calculatedDuration, currentLat, currentLon);

                // Construct a complete historical record mapping tracking endpoints cleanly
                IssAlert alert = new IssAlert(
                        trackedStationName,
                        entryDetails.startLat(),
                        entryDetails.startLon(),
                        entryDetails.startTime(),
                        currentLat,
                        currentLon,
                        endTime,
                        calculatedDuration
                );

                issAlertRepository.save(alert);

                // Broadcast live pass alert to all connected SSE clients
                sseService.broadcastAlert(alert);

                iterator.remove(); // Evict from active state
            }
        }
    }
}

record ActivePassDetails(LocalDateTime startTime, double startLat, double startLon) {
}
