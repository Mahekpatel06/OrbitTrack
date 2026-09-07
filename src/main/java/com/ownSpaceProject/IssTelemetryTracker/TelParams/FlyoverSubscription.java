package com.ownSpaceProject.IssTelemetryTracker.TelParams;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "flyover_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlyoverSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String targetCity;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Builder.Default
    private Double minElevation = 15.0; // Observer horizon threshold in degrees

    @Builder.Default
    private boolean active = true;

    private LocalDateTime subscribedAt;

    private Long lastNotifiedAosEpoch; // Timestamp of the last notified pass to prevent duplicate emails
}
