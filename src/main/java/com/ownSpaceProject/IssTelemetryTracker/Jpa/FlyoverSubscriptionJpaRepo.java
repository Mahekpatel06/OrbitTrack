package com.ownSpaceProject.IssTelemetryTracker.Jpa;

import com.ownSpaceProject.IssTelemetryTracker.TelParams.FlyoverSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlyoverSubscriptionJpaRepo extends JpaRepository<FlyoverSubscription, Long> {

    List<FlyoverSubscription> findByActiveTrue();

    Optional<FlyoverSubscription> findByEmailIgnoreCaseAndTargetCityIgnoreCase(String email, String targetCity);

    List<FlyoverSubscription> findByEmailIgnoreCase(String email);
}
