package com.billing.simple.billsoft.service.pdf;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceStatus;

/**
 * Single source of truth for resolving effective Master Data on Invoices (AUDIT-036).
 * Prioritizes immutable historical snapshots captured at document issuance,
 * and seamlessly falls back to live records for legacy documents lacking snapshots.
 */
public final class InvoiceMasterDataResolver {

    private InvoiceMasterDataResolver() {}

    public static String getCustomerName(Invoice inv) {
        if (inv != null && inv.getCustomerNameSnapshot() != null && !inv.getCustomerNameSnapshot().isBlank()) {
            return inv.getCustomerNameSnapshot();
        }
        return (inv != null && inv.getCustomer() != null && inv.getCustomer().getName() != null && !inv.getCustomer().getName().isBlank())
                ? inv.getCustomer().getName() : "CASH CUSTOMER";
    }

    public static String getCustomerGstin(Invoice inv) {
        if (inv != null && inv.getCustomerGstinSnapshot() != null && !inv.getCustomerGstinSnapshot().isBlank()) {
            return inv.getCustomerGstinSnapshot();
        }
        return (inv != null && inv.getCustomer() != null && inv.getCustomer().getGstin() != null && !inv.getCustomer().getGstin().isBlank())
                ? inv.getCustomer().getGstin() : null;
    }

    public static String getCustomerAddress(Invoice inv) {
        if (inv != null && inv.getCustomerAddressSnapshot() != null && !inv.getCustomerAddressSnapshot().isBlank()) {
            return inv.getCustomerAddressSnapshot();
        }
        return (inv != null && inv.getCustomer() != null && inv.getCustomer().getAddress() != null && !inv.getCustomer().getAddress().isBlank())
                ? inv.getCustomer().getAddress() : null;
    }

    public static String getCustomerPhone(Invoice inv) {
        if (inv != null && inv.getCustomerPhoneSnapshot() != null && !inv.getCustomerPhoneSnapshot().isBlank()) {
            return inv.getCustomerPhoneSnapshot();
        }
        return (inv != null && inv.getCustomer() != null && inv.getCustomer().getPhone() != null && !inv.getCustomer().getPhone().isBlank())
                ? inv.getCustomer().getPhone() : null;
    }

    public static String getFirmName(Invoice inv, FirmDetails liveFirm) {
        if (inv != null && inv.getFirmNameSnapshot() != null && !inv.getFirmNameSnapshot().isBlank()) {
            return inv.getFirmNameSnapshot();
        }
        return (liveFirm != null && liveFirm.getFirmName() != null && !liveFirm.getFirmName().isBlank())
                ? liveFirm.getFirmName() : "RUPEECRM STORE";
    }

    public static String getFirmGstin(Invoice inv, FirmDetails liveFirm) {
        if (inv != null && inv.getFirmGstinSnapshot() != null && !inv.getFirmGstinSnapshot().isBlank()) {
            return inv.getFirmGstinSnapshot();
        }
        return (liveFirm != null && liveFirm.getGstin() != null && !liveFirm.getGstin().isBlank())
                ? liveFirm.getGstin() : null;
    }

    public static String getFirmAddress(Invoice inv, FirmDetails liveFirm) {
        if (inv != null && inv.getFirmAddressSnapshot() != null && !inv.getFirmAddressSnapshot().isBlank()) {
            return inv.getFirmAddressSnapshot();
        }
        return formatLiveFirmAddress(liveFirm);
    }

    public static String getFirmPhone(Invoice inv, FirmDetails liveFirm) {
        if (inv != null && inv.getFirmPhoneSnapshot() != null && !inv.getFirmPhoneSnapshot().isBlank()) {
            return inv.getFirmPhoneSnapshot();
        }
        return (liveFirm != null && liveFirm.getPhone() != null && !liveFirm.getPhone().isBlank())
                ? liveFirm.getPhone() : null;
    }

    public static String getFirmEmail(Invoice inv, FirmDetails liveFirm) {
        if (inv != null && inv.getFirmEmailSnapshot() != null && !inv.getFirmEmailSnapshot().isBlank()) {
            return inv.getFirmEmailSnapshot();
        }
        return (liveFirm != null && liveFirm.getEmail() != null && !liveFirm.getEmail().isBlank())
                ? liveFirm.getEmail() : null;
    }

    public static String formatLiveFirmAddress(FirmDetails firm) {
        if (firm == null) return "";
        StringBuilder addrSb = new StringBuilder();
        if (firm.getAddressLine1() != null && !firm.getAddressLine1().isBlank()) addrSb.append(firm.getAddressLine1());
        if (firm.getAddressLine2() != null && !firm.getAddressLine2().isBlank()) {
            if (addrSb.length() > 0) addrSb.append(", ");
            addrSb.append(firm.getAddressLine2());
        }
        if (firm.getCity() != null && !firm.getCity().isBlank()) {
            if (addrSb.length() > 0) addrSb.append(", ");
            addrSb.append(firm.getCity());
        }
        if (firm.getState() != null && !firm.getState().isBlank()) {
            if (addrSb.length() > 0) addrSb.append(" - ");
            addrSb.append(firm.getState());
        }
        if (firm.getPincode() != null && !firm.getPincode().isBlank()) {
            addrSb.append(" ").append(firm.getPincode());
        }
        return addrSb.toString();
    }

    /**
     * Capture immutable master data snapshots at invoice issuance/finalization time.
     * Draft and Estimate documents remain mutable until issued.
     */
    public static void captureSnapshotsAtIssuance(Invoice invoice, Customer customer, FirmDetails firm) {
        if (invoice == null) return;
        if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.ESTIMATE) {
            return;
        }

        if (customer == null && invoice.getCustomer() != null) {
            customer = invoice.getCustomer();
        }

        if (customer != null) {
            if (invoice.getCustomerNameSnapshot() == null && customer.getName() != null) {
                invoice.setCustomerNameSnapshot(customer.getName());
            }
            if (invoice.getCustomerGstinSnapshot() == null && customer.getGstin() != null) {
                invoice.setCustomerGstinSnapshot(customer.getGstin());
            }
            if (invoice.getCustomerAddressSnapshot() == null && customer.getAddress() != null) {
                invoice.setCustomerAddressSnapshot(customer.getAddress());
            }
            if (invoice.getCustomerPhoneSnapshot() == null && customer.getPhone() != null) {
                invoice.setCustomerPhoneSnapshot(customer.getPhone());
            }
        } else if (invoice.getCustomerNameSnapshot() == null) {
            invoice.setCustomerNameSnapshot("CASH CUSTOMER");
        }

        if (firm != null) {
            if (invoice.getFirmNameSnapshot() == null && firm.getFirmName() != null) {
                invoice.setFirmNameSnapshot(firm.getFirmName());
            }
            if (invoice.getFirmGstinSnapshot() == null && firm.getGstin() != null) {
                invoice.setFirmGstinSnapshot(firm.getGstin());
            }
            if (invoice.getFirmAddressSnapshot() == null) {
                String formatted = formatLiveFirmAddress(firm);
                if (!formatted.isBlank()) {
                    invoice.setFirmAddressSnapshot(formatted);
                }
            }
            if (invoice.getFirmPhoneSnapshot() == null && firm.getPhone() != null) {
                invoice.setFirmPhoneSnapshot(firm.getPhone());
            }
            if (invoice.getFirmEmailSnapshot() == null && firm.getEmail() != null) {
                invoice.setFirmEmailSnapshot(firm.getEmail());
            }
        }
    }
}
