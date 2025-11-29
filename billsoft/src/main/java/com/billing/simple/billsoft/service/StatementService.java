package com.billing.simple.billsoft.service;

import java.time.LocalDate;

import com.billing.simple.billsoft.dtos.CustomerStatementResponse;
import com.billing.simple.billsoft.dtos.FirmStatementResponse;

public interface StatementService {

    CustomerStatementResponse getCustomerStatement(Long customerId, LocalDate from, LocalDate to);

    byte[] generateCustomerStatementPdf(Long customerId, LocalDate from, LocalDate to) throws Exception;

    FirmStatementResponse getFirmStatement(LocalDate from, LocalDate to);

    byte[] generateFirmStatementPdf(LocalDate from, LocalDate to) throws Exception;
}
