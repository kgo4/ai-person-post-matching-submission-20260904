-- PMS project-data extraction is an independent personnel-ability path. A user-selected,
-- evidence-backed PMS result writes emp_ability directly and must not create a Harness admission.
-- Other AI-originated writes remain protected by the existing admission guard.

DROP TRIGGER IF EXISTS trg_emp_ability_ai_guard_insert;
DROP TRIGGER IF EXISTS trg_emp_ability_ai_guard_update;

DELIMITER $$

CREATE TRIGGER trg_emp_ability_ai_guard_insert
BEFORE INSERT ON emp_ability
FOR EACH ROW
BEGIN
    IF NEW.evaluation_source IN ('RESUME_PARSE','AI_INTERVIEW','JD_IMPORT','PMS_IMPORT','AI_ASSESSMENT','VIDEO_INTERVIEW','INTERVIEW_OBSERVATION','POST_EVOLUTION','AI_CANDIDATE','AI_GENERATED') THEN
        IF NEW.governance_admission_id IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AI-sourced emp_ability write requires governance_admission_id';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM governance_admission WHERE id = NEW.governance_admission_id AND final_decision = 'PASS') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'governance_admission_id must reference a PASS admission';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_emp_ability_ai_guard_update
BEFORE UPDATE ON emp_ability
FOR EACH ROW
BEGIN
    IF NEW.evaluation_source IN ('RESUME_PARSE','AI_INTERVIEW','JD_IMPORT','PMS_IMPORT','AI_ASSESSMENT','VIDEO_INTERVIEW','INTERVIEW_OBSERVATION','POST_EVOLUTION','AI_CANDIDATE','AI_GENERATED') THEN
        IF NEW.governance_admission_id IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AI-sourced emp_ability write requires governance_admission_id';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM governance_admission WHERE id = NEW.governance_admission_id AND final_decision = 'PASS') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'governance_admission_id must reference a PASS admission';
        END IF;
    END IF;
END$$

DELIMITER ;
