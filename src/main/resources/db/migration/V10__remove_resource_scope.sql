ALTER TABLE resource DROP CONSTRAINT resource_shape_ck;
ALTER TABLE resource DROP COLUMN scope;
ALTER TABLE resource ADD CONSTRAINT resource_shape_ck CHECK (type <> 'ACTION' OR route IS NULL);
