ALTER TABLE credit_scores
    ADD COLUMN risk_explanation VARCHAR(1000);

ALTER TABLE credit_scores
    ADD COLUMN model_confidence_score NUMERIC(38, 2);

ALTER TABLE credit_scores
    ADD COLUMN default_risk NUMERIC(38, 2);

ALTER TABLE credit_scores
    ADD COLUMN lending_recommendation VARCHAR(255);
