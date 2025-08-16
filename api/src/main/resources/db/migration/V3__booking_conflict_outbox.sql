-- V3__bookings_conflict_outbox.sql
create table if not exists booking_conflict_outbox (
    event_id uuid primary key default uuid_generate_v4(),
    correlation_id uuid not null unique,
    payload jsonb not null,
    created_at timestamp not null default now(),
    published_at timestamp
    );

create index if not exists idx_booking_conflict_outbox_unpublished
    on booking_conflict_outbox (published_at) where published_at is null;
