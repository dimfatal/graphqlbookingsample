-- V2__bookings_table.sql
-- set search_path to booking, public;

create table if not exists bookings (
    id uuid primary key default uuid_generate_v4(),
    home_id uuid not null,
    from_date date not null,
    to_date   date not null,
    guest_email text not null,
    source text not null,
    created_at timestamp not null default now(),
    period daterange generated always as (daterange(from_date, to_date, '[)')) stored,
    constraint chk_booking_dates check (from_date < to_date)
    );

-- Fast lookups by homeId
create index if not exists idx_bookings_home_from on bookings (home_id, from_date);

-- Exclusion to prevent overlaps per booking
alter table bookings
    add constraint no_overlap_per_home
    exclude using gist (home_id with =, period with &&);
