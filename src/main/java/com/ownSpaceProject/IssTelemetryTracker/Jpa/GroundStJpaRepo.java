package com.ownSpaceProject.IssTelemetryTracker.Jpa;

import com.ownSpaceProject.IssTelemetryTracker.TelParams.GroundStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroundStJpaRepo extends JpaRepository<GroundStation, Integer> {

    Optional<GroundStation> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
