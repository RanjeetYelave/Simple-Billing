package com.billing.simple.billsoft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class BillsoftApplication {

	public static void main(String[] args) {
		String dataDir = com.billing.simple.billsoft.util.DataDirectoryResolver.resolveDataDirectoryPath();
		System.setProperty("BILLSOFT_DATA_DIR", dataDir);
		SpringApplication.run(BillsoftApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
	public org.springframework.boot.CommandLineRunner databaseSchemaMigration(javax.sql.DataSource dataSource) {
		return args -> {
			try (java.sql.Connection conn = dataSource.getConnection();
				 java.sql.Statement stmt = conn.createStatement()) {
				try {
					stmt.execute("ALTER TABLE invoices ALTER COLUMN status VARCHAR(50)");
				} catch (Exception ignored) {
				}
				try {
					stmt.execute("ALTER TABLE notes ALTER COLUMN content CLOB");
				} catch (Exception ignored) {
				}
				try {
					stmt.execute("ALTER TABLE notes ALTER COLUMN tags VARCHAR(1000)");
				} catch (Exception ignored) {
				}
			} catch (Exception e) {
				System.err.println("Database migration note: " + e.getMessage());
			}
		};
	}
}
