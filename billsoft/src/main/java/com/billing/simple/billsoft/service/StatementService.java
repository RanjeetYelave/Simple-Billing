package com.billing.simple.billsoft.service;

import java.time.LocalDate;

import com.billing.simple.billsoft.dtos.CustomerStatementResponse;
import com.billing.simple.billsoft.dtos.FirmStatementResponse;

public interface StatementService {

    CustomerStatementResponse getCustomerStatement(Long firmId, Long customerId, LocalDate from, LocalDate to);

    byte[] generateCustomerStatementPdf(Long firmId, Long customerId, LocalDate from, LocalDate to) throws Exception;

    FirmStatementResponse getFirmStatement(Long firmId, LocalDate from, LocalDate to);

    byte[] generateFirmStatementPdf(Long firmId, LocalDate from, LocalDate to) throws Exception;
}
