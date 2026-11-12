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

				// Phase 1 & 2: BusinessPartyLink shared Customer + Supplier identity table & unique constraints
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

					// Drop legacy unconditional unique indexes so multiple historical unlinked rows can exist safely
					try {
						stmt.execute("DROP INDEX IF EXISTS uk_bpl_firm_customer");
					} catch (Exception ignored) {}
					try {
						stmt.execute("DROP INDEX IF EXISTS uk_bpl_firm_party");
					} catch (Exception ignored) {}

					String[] bplCols = {
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'ACTIVE'",
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS customer_role_active BOOLEAN DEFAULT TRUE",
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS party_role_active BOOLEAN DEFAULT TRUE",
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMP",
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS disabled_reason VARCHAR(300)",
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS active_customer_id BIGINT",
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS active_party_id BIGINT",
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS unlinked_at TIMESTAMP",
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS unlinked_by VARCHAR(100)",
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS unlink_reason VARCHAR(500)",
						"ALTER TABLE business_party_links ADD COLUMN IF NOT EXISTS relationship_version INT DEFAULT 1"
					};
					for (String sql : bplCols) {
						try {
							stmt.execute(sql);
						} catch (Exception ignored) {
						}
					}

					// Backfill active columns for existing active links
					try {
						stmt.execute("UPDATE business_party_links SET active_customer_id = customer_id WHERE active_customer_id IS NULL AND (status IS NULL OR status = 'ACTIVE' OR status = 'CUSTOMER_DISABLED' OR status = 'PARTY_DISABLED' OR status = 'BOTH_DISABLED')");
					} catch (Exception ignored) {}
					try {
						stmt.execute("UPDATE business_party_links SET active_party_id = party_id WHERE active_party_id IS NULL AND (status IS NULL OR status = 'ACTIVE' OR status = 'CUSTOMER_DISABLED' OR status = 'PARTY_DISABLED' OR status = 'BOTH_DISABLED')");
					} catch (Exception ignored) {}
					try {
						stmt.execute("UPDATE business_party_links SET relationship_version = 1 WHERE relationship_version IS NULL");
					} catch (Exception ignored) {}

					// Active-only unique indexes enforcing strict 1:1 active relationship while allowing historical unlinked version rows
					try {
						stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_bpl_firm_act_cust ON business_party_links(firm_id, active_customer_id)");
					} catch (Exception ignored) {}
					try {
						stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_bpl_firm_act_party ON business_party_links(firm_id, active_party_id)");
					} catch (Exception ignored) {}
					try {
						stmt.execute("CREATE INDEX IF NOT EXISTS idx_bpl_firm_status ON business_party_links(firm_id, status)");
					} catch (Exception ignored) {}
				} catch (Exception e) {
					System.err.println("Note: business_party_links init: " + e.getMessage());
				}

				// Phase 2: Persistent Business Identity Aliases table
				try {
					stmt.execute("CREATE TABLE IF NOT EXISTS business_identity_aliases (" +
							"id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
							"firm_id BIGINT NOT NULL, " +
							"alias_value VARCHAR(255) NOT NULL, " +
							"normalized_value VARCHAR(255) NOT NULL, " +
							"alias_type VARCHAR(50) NOT NULL, " +
							"link_id BIGINT, " +
							"customer_id BIGINT, " +
							"party_id BIGINT, " +
							"owner_role VARCHAR(30), " +
							"active BOOLEAN DEFAULT TRUE NOT NULL, " +
							"created_at TIMESTAMP NOT NULL, " +
							"created_by VARCHAR(100), " +
							"resolution_notes VARCHAR(500)" +
							")");
					stmt.execute("CREATE INDEX IF NOT EXISTS idx_bia_firm_norm ON business_identity_aliases(firm_id, normalized_value)");
					stmt.execute("CREATE INDEX IF NOT EXISTS idx_bia_firm_link ON business_identity_aliases(firm_id, link_id)");
					stmt.execute("CREATE INDEX IF NOT EXISTS idx_bia_firm_cust ON business_identity_aliases(firm_id, customer_id)");
					stmt.execute("CREATE INDEX IF NOT EXISTS idx_bia_firm_party ON business_identity_aliases(firm_id, party_id)");
				} catch (Exception e) {
					System.err.println("Note: business_identity_aliases init: " + e.getMessage());
				}

				// Phase 2: Business Party Link Lifecycle Audit Events table
				try {
					stmt.execute("CREATE TABLE IF NOT EXISTS business_party_link_events (" +
							"id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
							"firm_id BIGINT NOT NULL, " +
							"link_id BIGINT, " +
							"relationship_version INT DEFAULT 1, " +
							"customer_id BIGINT, " +
							"party_id BIGINT, " +
							"action VARCHAR(60) NOT NULL, " +
							"actor VARCHAR(100), " +
							"timestamp TIMESTAMP NOT NULL, " +
							"before_value CLOB, " +
							"after_value CLOB, " +
							"reason VARCHAR(500)" +
							")");
					stmt.execute("CREATE INDEX IF NOT EXISTS idx_bple_firm_link ON business_party_link_events(firm_id, link_id)");
					stmt.execute("CREATE INDEX IF NOT EXISTS idx_bple_firm_time ON business_party_link_events(firm_id, timestamp)");
				} catch (Exception e) {
					System.err.println("Note: business_party_link_events init: " + e.getMessage());
				}

				// Phase 2: Sales Return Historical Customer Snapshot
				try {
					stmt.execute("ALTER TABLE sales_returns ADD COLUMN IF NOT EXISTS customer_name_snapshot VARCHAR(255)");
				} catch (Exception ignored) {}
			} catch (Exception e) {
				System.err.println("Database schema migration notice: " + e.getMessage());
			}
		};
	}
}
