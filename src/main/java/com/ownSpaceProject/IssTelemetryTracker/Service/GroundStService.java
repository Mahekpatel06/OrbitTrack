package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.Jpa.GroundStJpaRepo;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.GroundStation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroundStService {

    @Autowired
    private GroundStJpaRepo groundStJpaRepo;

    /**
     * Initializes a minimal starter seed of 3 ground stations only if the database is completely empty.
     * All other global tracking stations are ingested dynamically via the Excel upload pipeline.
     */
    public List<GroundStation> saveGrndSt() {

        List<GroundStation> existingGs = groundStJpaRepo.findAll();

        if (!existingGs.isEmpty()) {
            return existingGs;
        }

        // Minimal starter sample (3 stations across Asia, America, and Europe)
        List<GroundStation> starterSampleStations = List.of(
                new GroundStation("Bylalu Indian Deep Space Network", "India", 12.8700, 77.3600),
                new GroundStation("Goldstone Deep Space Complex", "USA", 35.4267, -116.8900),
                new GroundStation("Madrid Deep Space Complex", "Spain", 40.4314, -4.2496)
        );

        groundStJpaRepo.saveAll(starterSampleStations);
        return starterSampleStations;
    }
}
