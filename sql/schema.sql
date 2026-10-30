-- Parking system schema (PostgreSQL). Run once on an empty database:
--   psql -U postgres -d parkingdb -f sql/schema.sql

DROP TABLE IF EXISTS vehicle_booking;
DROP TABLE IF EXISTS parking_slot;

CREATE TABLE parking_slot (
    id           BIGSERIAL PRIMARY KEY,
    slot_number  VARCHAR(20) NOT NULL UNIQUE,
    status       VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                 CHECK (status IN ('AVAILABLE', 'OCCUPIED')),
    version      BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE vehicle_booking (
    id              BIGSERIAL PRIMARY KEY,
    slot_id         BIGINT      NOT NULL REFERENCES parking_slot (id),
    vehicle_number  VARCHAR(20) NOT NULL,
    entry_time      TIMESTAMP   NOT NULL,
    exit_time       TIMESTAMP,
    fee             NUMERIC(10, 2) CHECK (fee IS NULL OR fee >= 0),
    booking_status  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (booking_status IN ('ACTIVE', 'COMPLETED')),
    payment_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (payment_status IN ('PENDING', 'PAID')),
    CONSTRAINT chk_exit_after_entry CHECK (exit_time IS NULL OR exit_time >= entry_time),
    -- a completed booking must have an exit time and fee
    CONSTRAINT chk_completed_has_exit CHECK (
        booking_status = 'ACTIVE' OR (exit_time IS NOT NULL AND fee IS NOT NULL))
);

-- Database-level double-booking guards (last line of defence under concurrency)
CREATE UNIQUE INDEX uq_active_booking_per_slot
    ON vehicle_booking (slot_id) WHERE booking_status = 'ACTIVE';
CREATE UNIQUE INDEX uq_active_booking_per_vehicle
    ON vehicle_booking (vehicle_number) WHERE booking_status = 'ACTIVE';

CREATE INDEX idx_booking_exit_time ON vehicle_booking (exit_time);
