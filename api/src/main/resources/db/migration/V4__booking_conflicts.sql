-- V3__bookings_conflict_outbox.sql
-- Projection of conflicts consumed from Kafka.
create table if not exists booking_conflicts (
    id uuid primary key default uuid_generate_v4(),
    correlation_id uuid not null unique,
    home_id uuid not null,
    from_date date not null,
    to_date   date not null,
    guest_email text not null,
    source text not null,
    suggested jsonb,
    occurred_at timestamp not null
    );

create index if not exists idx_booking_conflicts_home_at
    on booking_conflicts (home_id, occurred_at desc);
