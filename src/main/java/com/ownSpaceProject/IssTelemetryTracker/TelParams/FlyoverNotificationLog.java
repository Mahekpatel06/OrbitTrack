package com.ownSpaceProject.IssTelemetryTracker.TelParams;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "flyover_notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlyoverNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private String targetCity;

    private String passAosIst;

    private Double maxElevationDeg;

    private String passType;

    private LocalDateTime dispatchedAt;

    private String status; // "DELIVERED_SMTP", "SIMULATED_LOG", "FAILED"

    private String subject;
}
