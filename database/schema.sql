-- SlotGuard Database Schema Definition

DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS slots;

CREATE TABLE slots (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    capacity INT NOT NULL CHECK (capacity >= 0),
    reserved_count INT NOT NULL DEFAULT 0 CHECK (reserved_count >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    slot_id BIGINT NOT NULL REFERENCES slots(id) ON DELETE CASCADE,
    user_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index to optimize slot lookup under high concurrency
CREATE INDEX idx_reservations_slot_id ON reservations(slot_id);

-- Invariant Check Helper View
CREATE OR REPLACE VIEW slot_invariants_view AS
SELECT 
    s.id AS slot_id,
    s.title,
    s.capacity,
    s.reserved_count,
    COUNT(r.id) AS actual_db_reservations,
    CASE 
        WHEN COUNT(r.id) <= s.capacity THEN 'INVARIANT_HOLDING'
        ELSE 'DOUBLE_BOOKED'
    END AS invariant_status
FROM slots s
LEFT JOIN reservations r ON s.id = r.slot_id
GROUP BY s.id, s.title, s.capacity, s.reserved_count;
