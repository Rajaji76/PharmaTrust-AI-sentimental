-- V3: Add government ID fields for patient identity verification
ALTER TABLE users ADD COLUMN IF NOT EXISTS govt_id_type VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS govt_id_number VARCHAR(100);
