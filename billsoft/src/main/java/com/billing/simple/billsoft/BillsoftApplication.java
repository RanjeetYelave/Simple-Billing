package com.billing.simple.billsoft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class BillsoftApplication {

	public static void main(String[] args) {
		if (System.getProperty("BILLSOFT_DATA_DIR") == null && System.getenv("BILLSOFT_DATA_DIR") == null) {
			String os = System.getProperty("os.name").toLowerCase();
			String dataDir;
			if (os.contains("win")) {
				String appData = System.getenv("APPDATA");
				if (appData != null && !appData.isEmpty()) {
					dataDir = appData + java.io.File.separator + "SimpleBilling";
				} else {
					dataDir = System.getProperty("user.home") + java.io.File.separator + ".simplebilling";
				}
			} else if (os.contains("mac")) {
				dataDir = System.getProperty("user.home") + "/Library/Application Support/SimpleBilling";
			} else {
				dataDir = System.getProperty("user.home") + java.io.File.separator + ".simplebilling";
			}
			System.setProperty("BILLSOFT_DATA_DIR", dataDir);
		}
		SpringApplication.run(BillsoftApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
