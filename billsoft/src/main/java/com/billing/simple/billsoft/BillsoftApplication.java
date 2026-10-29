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
		org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(4000);
		factory.setReadTimeout(6000);
		return new RestTemplate(factory);
	}

	@Bean
	public org.springframework.boot.CommandLineRunner databaseSchemaMigration(javax.sql.DataSource dataSource) {
		return args -> {
			com.billing.simple.billsoft.security.DatabaseSafetyGuard.assertSafeDataSource(dataSource);
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
					stmt.execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS in_inventory BOOLEAN DEFAULT TRUE NOT NULL");
				} catch (Exception ignored) {
				}
				try {
					stmt.execute("UPDATE products SET in_inventory = TRUE WHERE in_inventory IS NULL");
				} catch (Exception ignored) {
				}
				try {
					stmt.execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS is_archived BOOLEAN DEFAULT FALSE NOT NULL");
				} catch (Exception ignored) {
				}
				try {
					stmt.execute("UPDATE products SET is_archived = FALSE WHERE is_archived IS NULL");
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
				try {
					stmt.execute("ALTER TABLE invoices ALTER COLUMN version SET DEFAULT 0");
				} catch (Exception ignored) {
				}
				try {
					stmt.execute("UPDATE invoices SET version = 0 WHERE version IS NULL");
				} catch (Exception ignored) {
				}

				// AUDIT-032 & AUDIT-036: Add GST breakdown and master data snapshot columns
				try {
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS place_of_supply VARCHAR(100)");
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS cgst_amount DECIMAL(15, 2) DEFAULT 0.00");
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS sgst_amount DECIMAL(15, 2) DEFAULT 0.00");
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS igst_amount DECIMAL(15, 2) DEFAULT 0.00");

					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS firm_name_snapshot VARCHAR(255)");
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS firm_gstin_snapshot VARCHAR(50)");
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS firm_address_snapshot VARCHAR(1000)");
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS firm_phone_snapshot VARCHAR(50)");
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS firm_email_snapshot VARCHAR(100)");

					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS customer_name_snapshot VARCHAR(255)");
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS customer_gstin_snapshot VARCHAR(50)");
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS customer_address_snapshot VARCHAR(1000)");
					stmt.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS customer_phone_snapshot VARCHAR(50)");

					stmt.execute("ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS cgst_amount DECIMAL(15, 2) DEFAULT 0.00");
					stmt.execute("ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS sgst_amount DECIMAL(15, 2) DEFAULT 0.00");
					stmt.execute("ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS igst_amount DECIMAL(15, 2) DEFAULT 0.00");

					stmt.execute("ALTER TABLE sales_returns ADD COLUMN IF NOT EXISTS cgst_amount DECIMAL(15, 2) DEFAULT 0.00");
					stmt.execute("ALTER TABLE sales_returns ADD COLUMN IF NOT EXISTS sgst_amount DECIMAL(15, 2) DEFAULT 0.00");
					stmt.execute("ALTER TABLE sales_returns ADD COLUMN IF NOT EXISTS igst_amount DECIMAL(15, 2) DEFAULT 0.00");

					stmt.execute("ALTER TABLE sales_return_items ADD COLUMN IF NOT EXISTS cgst_amount DECIMAL(15, 2) DEFAULT 0.00");
					stmt.execute("ALTER TABLE sales_return_items ADD COLUMN IF NOT EXISTS sgst_amount DECIMAL(15, 2) DEFAULT 0.00");
					stmt.execute("ALTER TABLE sales_return_items ADD COLUMN IF NOT EXISTS igst_amount DECIMAL(15, 2) DEFAULT 0.00");
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

				// Phase 1: BusinessPartyLink shared Customer + Supplier identity table & unique constraints
				try {
					stmt.execute("CREATE TABLE IF NOT EXISTS business_party_links (" +
							"id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
							"firm_id BIGINT NOT NULL, " +
							"customer_id BIGINT NOT NULL, " +
							"party_id BIGINT NOT NULL, " +
							"linked_at TIMESTAMP NOT NULL, " +
							"linked_by VARCHAR(100), " +
							"notes VARCHAR(500)" +
							")");
					stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_bpl_firm_customer ON business_party_links(firm_id, customer_id)");
					stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_bpl_firm_party ON business_party_links(firm_id, party_id)");
				} catch (Exception e) {
					System.err.println("Note: business_party_links init: " + e.getMessage());
				}
			} catch (Exception e) {
				System.err.println("Database schema migration notice: " + e.getMessage());
			}
		};
	}
}
