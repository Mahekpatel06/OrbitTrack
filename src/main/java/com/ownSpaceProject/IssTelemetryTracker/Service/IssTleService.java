package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.Jpa.IssTleJpaRepository;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.IssTle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IssTleService {

    // Default hardcoded fallback in case both Celestrak API and Database are unpopulated
    private static final String DEFAULT_LINE1 = "1 25544U 98067A   26205.47557870  .00016717  00000+0  30125-3 0  9993";
    private static final String DEFAULT_LINE2 = "2 25544  51.6416 156.1601 0006248 100.2222 260.4444 15.49876543262054";

    @Autowired
    private IssTleJpaRepository issTleJpaRepository;

    @Value("${app.celestrak.tle-url:https://celestrak.org/NORAD/elements/gp.php}")
    private String celestrakTleUrl;

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("User-Agent", "Spring Boot Space Telemetry Tracker")
            .build();

    // In-memory cache for fast access during 30s scheduler loops
    private final Map<Integer, IssTle> activeTleCache = new ConcurrentHashMap<>();

    /**
     * Ingests fresh TLE data for the specified satellite from Celestrak and persists it in PostgreSQL.
     */
    public IssTle fetchAndSaveLatestTle(int satId) {
        String url = celestrakTleUrl + "?CATNR=" + satId + "&FORMAT=TLE";

        try {
            System.out.println("[TLE INGESTION] Fetching latest TLE from Celestrak for NORAD ID: " + satId + "...");
            String rawResponse = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) {
                System.err.println("[WARN] Celestrak returned empty TLE response for satId: " + satId);
                return getActiveTle(satId);
            }

            String[] lines = rawResponse.split("\\r?\\n");
            String satName = "ISS (ZARYA)";
            String line1 = null;
            String line2 = null;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("1 ")) {
                    line1 = trimmed;
                } else if (trimmed.startsWith("2 ")) {
                    line2 = trimmed;
                } else if (!trimmed.isEmpty() && line1 == null) {
                    satName = trimmed;
                }
            }

            if (line1 != null && line2 != null) {
                IssTle tle = new IssTle(satId, satName, line1, line2, LocalDateTime.now());
                IssTle savedTle = issTleJpaRepository.save(tle);
                activeTleCache.put(satId, savedTle);

                System.out.println("[TLE INGESTION SUCCESS] Persisted new TLE into DB:");
                System.out.println("  Name:  " + satName);
                System.out.println("  Line1: " + line1);
                System.out.println("  Line2: " + line2);
                return savedTle;
            } else {
                System.err.println("[WARN] Unable to parse TLE format from Celestrak response: " + rawResponse);
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to fetch TLE from Celestrak: " + e.getMessage() + ". Falling back to database/cached TLE.");
        }

        return getActiveTle(satId);
    }

    /**
     * Retrieves the most up-to-date TLE available (Cache -> Database -> Default Fallback).
     */
    public IssTle getActiveTle(int satId) {
        // 1. Check in-memory cache
        if (activeTleCache.containsKey(satId)) {
            return activeTleCache.get(satId);
        }

        // 2. Query latest from PostgreSQL
        Optional<IssTle> dbTle = issTleJpaRepository.findTopBySatIdOrderByFetchedAtDesc(satId);
        if (dbTle.isPresent()) {
            activeTleCache.put(satId, dbTle.get());
            return dbTle.get();
        }

        // 3. Fallback to default hardcoded TLE
        IssTle fallback = new IssTle(satId, "ISS (ZARYA) [Default]", DEFAULT_LINE1, DEFAULT_LINE2, LocalDateTime.now());
        activeTleCache.put(satId, fallback);
        return fallback;
    }
}
