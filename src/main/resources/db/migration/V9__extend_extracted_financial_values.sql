ALTER TABLE extracted_financial_fields
    ADD COLUMN business_revenue NUMERIC(38, 2);

ALTER TABLE extracted_financial_fields
    ADD COLUMN tax_value NUMERIC(38, 2);

ALTER TABLE extracted_financial_fields
    ADD COLUMN invoice_total NUMERIC(38, 2);
