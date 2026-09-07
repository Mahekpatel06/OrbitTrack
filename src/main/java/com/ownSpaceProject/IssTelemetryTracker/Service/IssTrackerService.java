package com.ownSpaceProject.IssTelemetryTracker.Service;

import com.ownSpaceProject.IssTelemetryTracker.Dto.IssTelDto;
import com.ownSpaceProject.IssTelemetryTracker.TelParams.N2yoRootClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Locale;

@Service
public class IssTrackerService {

    @Autowired
    private IssVelService issVelService;

    @Value("${app.n2yo.api-key:GDRK25-KYTYXB-A9ZAYL-5SW7}")
    private String apiKey;

    @Value("${app.n2yo.base-url:https://api.n2yo.com/rest/v1/satellite/positions}")
    private String baseUrl;

    @Value("${app.n2yo.observer-lat:22.55896}")
    private double observerLat;

    @Value("${app.n2yo.observer-lon:72.91993}")
    private double observerLon;

    @Value("${app.n2yo.observer-alt:0}")
    private double observerAlt;

    @Value("${app.satellite.norad-id:25544}")
    private int satId;

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("User-Agent", "Spring Boot Satellite Tracker Application")
            .build();

    public IssTelDto fetchCurrentLocation() {
        // Construct dynamic N2YO API URL: /positions/{id}/{lat}/{lon}/{alt}/{seconds}
        String url = String.format(Locale.US, "%s/%d/%.5f/%.5f/%.0f/1/&apiKey=%s",
                baseUrl, satId, observerLat, observerLon, observerAlt, apiKey);

        try {
            N2yoRootClass response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(N2yoRootClass.class);

            // Extract the first position element from the array
            if (response != null && response.getPositions() != null && !response.getPositions().isEmpty()) {
                IssTelDto dto = response.getPositions().get(0);

                // Map the satellite ID from the info block
                if (response.getInfo() != null) {
                    dto.setIssId(response.getInfo().getSatid());
                }

                return dto;
            }

            System.out.println("[WARN] N2YO API returned empty position data. Triggering self-healing Orekit fallback...");
            return issVelService.propagateCurrentTelemetry();

        } catch (Exception e) {
            System.out.println("[WARN] Failed to fetch data from N2YO API (" + e.getMessage() + "). Triggering self-healing Orekit fallback...");
            return issVelService.propagateCurrentTelemetry();
        }
    }
}
