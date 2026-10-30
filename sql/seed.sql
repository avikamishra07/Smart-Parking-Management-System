-- Sample slots. Run after schema.sql:
--   psql -U postgres -d parkingdb -f sql/seed.sql
INSERT INTO parking_slot (slot_number) VALUES
 ('A-01'), ('A-02'), ('A-03'), ('A-04'), ('A-05'),
 ('B-01'), ('B-02'), ('B-03'), ('B-04'), ('B-05');
