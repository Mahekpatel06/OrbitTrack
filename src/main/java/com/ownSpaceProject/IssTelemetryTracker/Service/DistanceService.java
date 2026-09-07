package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.Dto.DisCoordDto;
import org.springframework.stereotype.Service;

@Service
public class DistanceService {

   private static final double EARTH_RADIUS_KM = 6371.0;

   public Double calDis(DisCoordDto pC, DisCoordDto cC) {

//        convert from degree to radian
        double lat1Rad = Math.toRadians(pC.getLatitude());
        double lon1Rad = Math.toRadians(pC.getLongitude());
        double lat2Rad = Math.toRadians(cC.getLatitude());
        double lon2Rad = Math.toRadians(cC.getLongitude());

//        account altitude in total orbital radius
        double r1 = EARTH_RADIUS_KM + pC.getAltitude();
        double r2 = EARTH_RADIUS_KM + cC.getAltitude();

//       convert prevCoordinates to 3D cartesian coordinates
       double x1 = r1 * Math.cos(lat1Rad) * Math.cos(lon1Rad);
       double y1 = r1 * Math.cos(lat1Rad) * Math.sin(lon1Rad);
       double z1 = r1 * Math.sin(lat1Rad);

//       convert prevCoordinates to 3D cartesian coordinates
       double x2 = r2 * Math.cos(lat2Rad) * Math.cos(lon2Rad);
       double y2 = r2 * Math.cos(lat2Rad) * Math.sin(lon2Rad);
       double z2 = r2 * Math.sin(lat2Rad);

//       calculate 3D Euclidean distance
       return Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2) + Math.pow(z2-z1, 2));

   }
}
