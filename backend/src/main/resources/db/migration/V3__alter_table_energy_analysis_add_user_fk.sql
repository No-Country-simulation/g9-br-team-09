ALTER TABLE energy_analysis
    ADD user_id NUMBER(19) NULL;

ALTER TABLE energy_analysis
    ADD CONSTRAINT fk_energy_analysis_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id);

CREATE INDEX idx_energy_analysis_user_id
    ON energy_analysis(user_id);
