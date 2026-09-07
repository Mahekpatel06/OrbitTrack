package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.Dto.PassPredictionDto;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTle;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class PassPredictionService {

    @Value("${app.satellite.norad-id:25544}")
    private int issNoradId;

    @Autowired
    private IssTleService issTleService;

    private static final double MIN_ELEVATION_DEG = 1.0; // 1.0° Radio horizon (matches ~2,000 km geofence range)
    private static final DateTimeFormatter IST_FORMATTER = DateTimeFormatter
            .ofPattern("dd MMM, hh:mm:ss a")
            .withZone(ZoneId.of("Asia/Kolkata"));

    @jakarta.annotation.PostConstruct
    public void initOrekitData() {
        try {
            org.orekit.data.DataProvidersManager manager = org.orekit.data.DataContext.getDefault().getDataProvidersManager();
            if (manager.getProviders().isEmpty()) {
                manager.addProvider(new org.orekit.data.ClasspathCrawler(getClass().getClassLoader(), "orekit-data.zip"));
            }
        } catch (Exception e) {
            System.err.println("[PassPredictionService] Orekit data initialization: " + e.getMessage());
        }
    }

    /**
     * Predicts upcoming ISS visible and radio flyovers over an observer's coordinates
     * for a given number of days (default 2 days / 48 hours).
     */
    public PassPredictionDto predictPasses(double observerLat, double observerLon, String targetName, int days) {
        int boundedDays = Math.max(1, Math.min(days, 5)); // Clamp between 1 and 5 days
        List<PassPredictionDto.PassDetailDto> passes = new ArrayList<>();

        try {
            IssTle activeTle = issTleService.getActiveTle(issNoradId);
            TLE issTLE = new TLE(activeTle.getLine1(), activeTle.getLine2());
            TLEPropagator propagator = TLEPropagator.selectExtrapolator(issTLE);

            // Configure Earth WGS-84 model in ITRF frame
            Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
            OneAxisEllipsoid earth = new OneAxisEllipsoid(
                    Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                    Constants.WGS84_EARTH_FLATTENING,
                    itrf);

            // Observer Topocentric Frame
            GeodeticPoint observerPoint = new GeodeticPoint(
                    FastMath.toRadians(observerLat),
                    FastMath.toRadians(observerLon),
                    0.0);
            TopocentricFrame topoFrame = new TopocentricFrame(earth, observerPoint, "Observer-" + targetName);

            Date startDate = new Date();
            long nowMs = startDate.getTime();
            AbsoluteDate epoch = new AbsoluteDate(startDate, TimeScalesFactory.getUTC());
            double totalDurationSeconds = boundedDays * 86400.0;
            double stepSeconds = 15.0; // 15-second step for higher precision

            boolean inPass = false;
            double passStartOffset = 0.0;
            double maxElevInPass = 0.0;
            double timeOfMaxElev = 0.0;

            // Start 10 minutes in the past to catch any pass currently entering or in progress
            for (double offset = -600.0; offset <= totalDurationSeconds; offset += stepSeconds) {
                AbsoluteDate currentDate = epoch.shiftedBy(offset);
                PVCoordinates pvItrf = propagator.getPVCoordinates(currentDate, itrf);

                double elevationRad = topoFrame.getElevation(pvItrf.getPosition(), itrf, currentDate);
                double elevationDeg = FastMath.toDegrees(elevationRad);

                if (elevationDeg >= MIN_ELEVATION_DEG) {
                    if (!inPass) {
                        // Pass beginning: Acquisition of Signal (AOS)
                        inPass = true;
                        passStartOffset = offset;
                        maxElevInPass = elevationDeg;
                        timeOfMaxElev = offset;
                    } else {
                        if (elevationDeg > maxElevInPass) {
                            maxElevInPass = elevationDeg;
                            timeOfMaxElev = offset;
                        }
                    }
                } else {
                    if (inPass) {
                        // Pass ending: Loss of Signal (LOS)
                        inPass = false;
                        double passEndOffset = offset;

                        long aosEpochMs = startDate.getTime() + (long) (passStartOffset * 1000.0);
                        long losEpochMs = startDate.getTime() + (long) (passEndOffset * 1000.0);
                        int durationSec = (int) (passEndOffset - passStartOffset);

                        // Only include passes that have not yet completely finished
                        if (losEpochMs >= nowMs) {
                            // Classify pass quality
                            String quality = "LOW GRAZING";
                            if (maxElevInPass >= 40.0) {
                                quality = "HIGH OVERHEAD ★";
                            } else if (maxElevInPass >= 20.0) {
                                quality = "MEDIUM";
                            }

                            // Determine lighting at observer (Visible vs Daylight vs Eclipsed)
                            String passType = classifyPassLighting(observerLat, observerLon, aosEpochMs);

                            // Check if pass is happening RIGHT NOW
                            if (aosEpochMs <= nowMs && losEpochMs >= nowMs) {
                                passType = "IN PROGRESS 🔴 (" + passType + ")";
                                quality = "ACTIVE NOW";
                            }

                            passes.add(PassPredictionDto.PassDetailDto.builder()
                                    .aosTimestamp(aosEpochMs)
                                    .losTimestamp(losEpochMs)
                                    .formattedAosIst(IST_FORMATTER.format(Instant.ofEpochMilli(aosEpochMs)) + " IST")
                                    .formattedLosIst(IST_FORMATTER.format(Instant.ofEpochMilli(losEpochMs)) + " IST")
                                    .durationSeconds(durationSec)
                                    .durationFormatted(formatDuration(durationSec))
                                    .maxElevationDeg(Math.round(maxElevInPass * 10.0) / 10.0)
                                    .passType(passType)
                                    .quality(quality)
                                    .build());
                        }

                        maxElevInPass = 0.0;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Pass prediction calculation failed: " + e.getMessage());
        }

        return PassPredictionDto.builder()
                .targetName(targetName)
                .targetLatitude(observerLat)
                .targetLongitude(observerLon)
                .generatedAt(System.currentTimeMillis())
                .passes(passes)
                .build();
    }

    /**
     * Estimates whether the flyover is a naked-eye visible sighting (during twilight/evening/morning),
     * a daylight pass, or deep night radio pass.
     */
    private String classifyPassLighting(double lat, double lon, long epochMs) {
        Instant instant = Instant.ofEpochMilli(epochMs);
        int hourUtc = instant.atZone(ZoneId.of("UTC")).getHour();

        // Local Solar Hour approximation = UTC + (lon / 15)
        double localHour = (hourUtc + (lon / 15.0)) % 24.0;
        if (localHour < 0) localHour += 24.0;

        // Twilight / Evening / Dawn window for visible naked-eye sightings
        if ((localHour >= 5.0 && localHour <= 7.0) || (localHour >= 18.5 && localHour <= 21.5)) {
            return "VISIBLE SIGHTING 🌟";
        } else if (localHour > 7.0 && localHour < 18.5) {
            return "DAYLIGHT PASS ☀️";
        } else {
            return "NIGHT RADIO PASS 📡";
        }
    }

    private String formatDuration(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%dm %02ds", m, s);
    }
}
