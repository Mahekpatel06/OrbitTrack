package com.ownSpaceProject.IssTelemetryTracker.TelParams;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class IssTle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer satId;

    private String satelliteName;

    @Column(length = 100)
    private String line1;

    @Column(length = 100)
    private String line2;

    private LocalDateTime fetchedAt;

    public IssTle() {
        super();
    }

    public IssTle(Integer satId, String satelliteName, String line1, String line2, LocalDateTime fetchedAt) {
        this.satId = satId;
        this.satelliteName = satelliteName;
        this.line1 = line1;
        this.line2 = line2;
        this.fetchedAt = fetchedAt;
    }
}
