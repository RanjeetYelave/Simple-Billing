# Invoice + Quotation Management System - Build & Fix Checklist

## Backend Changes
- [x] Add findByConvertedInvoiceId to InvoiceRepository
- [ ] Add endpoint to get linked invoice for a quotation
- [ ] Fix getAll invoices query to properly include firmId
- [ ] Ensure quotation conversion returns linked invoice data

## Frontend - Invoice System
- [ ] Fix InvoiceList - add sort dropdown (newest, oldest, number, customer, amount, status)
- [ ] Fix InvoiceList - proper empty states
- [ ] Fix InvoiceForm - calculations (no NaN, no duplicate)
- [ ] Fix InvoiceForm - item autocomplete suggestions
- [ ] Fix InvoiceForm - inline item creation
- [ ] Fix InvoiceForm - preview flow
- [ ] Fix InvoiceForm - edit flow
- [ ] Fix InvoiceForm - save flow

## Frontend - Quotation System
- [ ] Add "Quotations" nav item to sidebar
- [ ] Create QuotationList component (same as InvoiceList but for quotations)
- [ ] Create QuotationForm component (reuse InvoiceForm logic)
- [ ] Add convert to invoice flow with edit mode
- [ ] Show "converted to Invoice" note with link
- [ ] Prevent duplicate conversions

## Frontend - UX
- [ ] Proper loading states
- [ ] Error handling
- [ ] Success toasts
- [ ] Form validation
- [ ] Prevent duplicate submissions

## State Management & Data Integrity
- [ ] Invoice list refreshes correctly after save
- [ ] Quotation list refreshes correctly after save
- [ ] Autocomplete updates after new items added
- [ ] No stale state issues