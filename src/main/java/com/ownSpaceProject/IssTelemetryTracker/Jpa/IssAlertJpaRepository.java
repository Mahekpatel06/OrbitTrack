package com.ownSpaceProject.IssTelemetryTracker.Jpa;

import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface IssAlertJpaRepository extends JpaRepository<IssAlert, Long> {

    List<IssAlert> findAllByOrderByStartTimestampDesc();

    Optional<IssAlert> findTopByOrderByStartTimestampDesc();

    @Query("SELECT a.stationName, COUNT(a) as passCount FROM IssAlert a GROUP BY a.stationName ORDER BY COUNT(a) DESC")
    List<Object[]> findStationPassCounts();

    @Query("SELECT SUM(a.duration) FROM IssAlert a")
    Long getTotalAlertDurationSeconds();
}
