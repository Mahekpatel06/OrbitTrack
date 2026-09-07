package com.ownSpaceProject.IssTelemetryTracker.Jpa;

import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IssTleJpaRepository extends JpaRepository<IssTle, Integer> {

    Optional<IssTle> findTopBySatIdOrderByFetchedAtDesc(Integer satId);

}
