package com.ownSpaceProject.IssTelemetryTracker.Jpa;

import com.ownSpaceProject.IssTelemetryTracker.TelParams.FlyoverNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlyoverNotificationLogJpaRepo extends JpaRepository<FlyoverNotificationLog, Long> {

    List<FlyoverNotificationLog> findTop20ByOrderByDispatchedAtDesc();

    List<FlyoverNotificationLog> findByRecipientEmailIgnoreCaseOrderByDispatchedAtDesc(String email);
}
