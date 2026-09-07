package com.ownSpaceProject.IssTelemetryTracker;

import org.orekit.data.ClasspathCrawler;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IssTelemetryTrackerApplication {

	public static void main(String[] args) {

		// Initialize Orekit data provider prior to starting Spring application context
		DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
		manager.addProvider(new ClasspathCrawler(IssTelemetryTrackerApplication.class.getClassLoader(), "orekit-data.zip"));

		SpringApplication.run(IssTelemetryTrackerApplication.class, args);
	}

}
