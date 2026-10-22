-- Store the canonical model value once so case-insensitive prefix searches do not wrap the
-- searched column in LOWER(), which would prevent normal index use.
ALTER TABLE vehicles ADD COLUMN model_key varchar(60);

UPDATE vehicles
SET model_key = LOWER(TRIM(model));

ALTER TABLE vehicles ALTER COLUMN model_key SET NOT NULL;

CREATE INDEX idx_vehicles_model_key ON vehicles (model_key);
