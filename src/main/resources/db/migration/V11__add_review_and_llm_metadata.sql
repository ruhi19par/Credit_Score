ALTER TABLE loan_applications
    ADD COLUMN review_notes VARCHAR(2000);

ALTER TABLE loan_applications
    ADD COLUMN reviewed_by_user_id BIGINT;

ALTER TABLE loan_applications
    ADD COLUMN reviewed_at TIMESTAMP;

ALTER TABLE credit_scores
    ADD COLUMN verified_document_count INTEGER;

ALTER TABLE credit_scores
    ADD COLUMN llm_model VARCHAR(255);

ALTER TABLE credit_scores
    ADD COLUMN llm_prompt_version VARCHAR(100);

ALTER TABLE credit_scores
    ADD COLUMN llm_raw_response TEXT;

ALTER TABLE credit_scores
    ADD COLUMN llm_reasoning_summary VARCHAR(1000);
