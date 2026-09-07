package com.ownSpaceProject.IssTelemetryTracker.Jpa;

import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTelemetry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface IssTelJpaRepository extends JpaRepository<IssTelemetry, Integer> {

    Optional<IssTelemetry> findTopByOrderByIndexDesc();

    List<IssTelemetry> findTop100ByOrderByIndexDesc();

    @Query("SELECT t FROM IssTelemetry t ORDER BY t.index DESC")
    List<IssTelemetry> findRecentTelemetry(Pageable pageable);

    @Query("SELECT SUM(t.travelledDis) FROM IssTelemetry t")
    Double getTotalDistanceTravelled();

    long countByVisibilityIgnoreCase(String visibility);
}
