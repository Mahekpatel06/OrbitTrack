package com.ownSpaceProject.IssTelemetryTracker;

import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

@SpringBootApplication
@EnableScheduling
public class IssTelemetryTrackerApplication {

	public static void main(String[] args) {

		// Initialize Orekit data provider prior to starting Spring application context
		File orekitData = new File("D:/My Projects/Space Projects/orekit-data-main");
		if (orekitData.exists()) {
			DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
			manager.addProvider(new DirectoryCrawler(orekitData));
		} else {
			System.err.println("[WARN] Orekit data directory not found at: " + orekitData.getAbsolutePath());
		}

		SpringApplication.run(IssTelemetryTrackerApplication.class, args);
	}

}
