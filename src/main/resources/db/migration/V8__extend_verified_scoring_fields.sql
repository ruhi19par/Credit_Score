ALTER TABLE extracted_financial_fields
    ADD COLUMN average_monthly_deposits NUMERIC(38, 2);

ALTER TABLE credit_scores
    ADD COLUMN cash_flow_stability_score NUMERIC(38, 2);

ALTER TABLE extracted_financial_fields
    ADD COLUMN average_monthly_withdrawals NUMERIC(38, 2);

ALTER TABLE extracted_financial_fields
    ADD COLUMN revenue_consistency_score NUMERIC(38, 2);

ALTER TABLE credit_scores
    ADD COLUMN business_health_score NUMERIC(38, 2);

ALTER TABLE credit_scores
    ADD COLUMN fraud_indicators VARCHAR(1000);
