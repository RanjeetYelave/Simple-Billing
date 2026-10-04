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
				try {
					stmt.execute("ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS hide_prices_on_po BOOLEAN DEFAULT FALSE");
				} catch (Exception ignored) {
				}
				try {
					stmt.execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS preferred_party_id BIGINT");
				} catch (Exception ignored) {
				}
				try {
					stmt.execute("ALTER TABLE invoice_payments ALTER COLUMN invoice_id DROP NOT NULL");
				} catch (Exception ignored) {
				}
				try {
					stmt.execute("ALTER TABLE invoice_payments ALTER COLUMN invoice_id SET NULL");
				} catch (Exception ignored) {
				}

				// Enforce Required Unique Constraints
				try {
					stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_invoices_firm_invoice_num ON invoices(firm_id, invoice_number)");
				} catch (Exception e) {
					System.err.println("Note: uq_invoices_firm_invoice_num: " + e.getMessage());
				}
				try {
					stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_orders_firm_po_num ON purchase_orders(firm_id, po_number)");
				} catch (Exception e) {
					System.err.println("Note: uq_purchase_orders_firm_po_num: " + e.getMessage());
				}
				try {
					stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_returns_firm_return_num ON sales_returns(firm_id, return_number)");
				} catch (Exception e) {
					System.err.println("Note: uq_sales_returns_firm_return_num: " + e.getMessage());
				}
			} catch (Exception e) {
				System.err.println("Database schema migration notice: " + e.getMessage());
			}
		};
	}
}
