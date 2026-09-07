package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.Dto.IssTelDto;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTle;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
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
import java.util.Date;

@Service
public class IssVelService {

    @Value("${app.satellite.norad-id:25544}")
    private int issNoradId;

    @Autowired
    private IssTleService issTleService;

    /**
     * Calculates the scalar speed (km/s) of the ISS in the inertial reference frame (EME2000)
     * using the dynamically ingested TLE.
     */
    public double issVelocity() {
        PVCoordinates pv = getPVCoordinatesInertial(new Date());
        if (pv == null) {
            return 7.66; // Fallback average ISS orbital speed in km/s
        }
        double speed = pv.getVelocity().getNorm(); // Magnitude in m/s
        return speed / 1000.0; // Return in km/s
    }

    /**
     * Propagates full telemetry (Latitude, Longitude, Altitude, Velocity, Timestamp)
     * using the dynamic SGP4 orbit model via Orekit when external APIs are unavailable.
     */
    public IssTelDto propagateCurrentTelemetry() {
        try {
            Date now = new Date();
            AbsoluteDate targetDate = new AbsoluteDate(now, TimeScalesFactory.getUTC());

            // 1. Obtain current dynamic TLE from service
            IssTle activeTle = issTleService.getActiveTle(issNoradId);
            TLE issTLE = new TLE(activeTle.getLine1(), activeTle.getLine2());
            TLEPropagator propagator = TLEPropagator.selectExtrapolator(issTLE);

            // 2. Earth-Fixed frame (ITRF) to calculate geodetic coordinates (Lat, Lon, Alt)
            Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
            BodyShape earth = new OneAxisEllipsoid(
                    Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                    Constants.WGS84_EARTH_FLATTENING,
                    itrf
            );

            // 3. Propagate position in Earth-Centered, Earth-Fixed (ITRF) frame
            PVCoordinates pvITRF = propagator.getPVCoordinates(targetDate, itrf);
            GeodeticPoint geodeticPoint = earth.transform(pvITRF.getPosition(), itrf, targetDate);

            // 4. Propagate velocity in Inertial frame (EME2000) for orbital speed
            PVCoordinates pvInertial = propagator.getPVCoordinates(targetDate, FramesFactory.getEME2000());
            double speedKmS = pvInertial.getVelocity().getNorm() / 1000.0;

            double latitude = FastMath.toDegrees(geodeticPoint.getLatitude());
            double longitude = FastMath.toDegrees(geodeticPoint.getLongitude());
            double altitude = geodeticPoint.getAltitude() / 1000.0; // convert meters to km

            IssTelDto dto = new IssTelDto();
            dto.setIssId(issNoradId);
            dto.setLatitude(latitude);
            dto.setLongitude(longitude);
            dto.setAltitude(altitude);
            dto.setVelocity(speedKmS);
            dto.setVisibility("daylight");
            dto.setTimestamp(Instant.now().getEpochSecond());

            System.out.printf("[OREKIT PROPAGATION FALLBACK] (Using TLE from %s) Lat: %.4f°, Lon: %.4f°, Alt: %.2f km, Speed: %.2f km/s%n",
                    activeTle.getFetchedAt(), latitude, longitude, altitude, speedKmS);

            return dto;
        } catch (Exception e) {
            System.err.println("Error during Orekit orbital propagation: " + e.getMessage());
            return null;
        }
    }

    private PVCoordinates getPVCoordinatesInertial(Date date) {
        try {
            IssTle activeTle = issTleService.getActiveTle(issNoradId);
            TLE issTLE = new TLE(activeTle.getLine1(), activeTle.getLine2());
            TLEPropagator propagator = TLEPropagator.selectExtrapolator(issTLE);
            AbsoluteDate targetDate = new AbsoluteDate(date, TimeScalesFactory.getUTC());
            return propagator.getPVCoordinates(targetDate, FramesFactory.getEME2000());
        } catch (Exception e) {
            System.err.println("Error calculating inertial PV coordinates: " + e.getMessage());
            return null;
        }
    }
}
