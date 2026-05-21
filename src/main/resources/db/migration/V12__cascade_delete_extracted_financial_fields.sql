ALTER TABLE extracted_financial_fields
    DROP CONSTRAINT fk_extracted_financial_fields_document;

ALTER TABLE extracted_financial_fields
    ADD CONSTRAINT fk_extracted_financial_fields_document
        FOREIGN KEY (document_id)
        REFERENCES documents (id)
        ON DELETE CASCADE;
