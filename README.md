# Booking Writer — Quick Guide

## core/
- **Domain types** (e.g., `CreateBooking`, `BookingRow`, `Interval`, `ConflictEvent`, …)
- **SuggestionLogic**: earliest future window & latest “past” window (end‑exclusive).
- **Doobie helpers**: savepoint/rollback to keep a single transaction alive on conflicts.

## api/
- GraphQL API built with **Caliban** + **http4s‑ember**.
- **BookingMutation** and **BookingQuery** resolvers:
    - Tries **INSERT** under a savepoint.
    - On **exclusion violation** (due to overlap): rolls back to savepoint, computes suggestions, inserts **outbox** record, and raises a **GraphQL error** (Caliban captures it).
    - On success: returns booking **ID**.
- Flyway migration bootstrap.
- Hikari transactor, log4cats logging.
- Kafka producer config + `PublishConflictsService` (outbox → Kafka).

## consumer/
- **fs2‑kafka** stream.
- Subscribes to topic **`booking_conflicts`**.
- Decodes `ConflictEvent` (Circe) and inserts into `booking.booking_conflicts`.
- Manual commits, idempotent insert on `correlation_id`.

## Flow / Architecture

```
Client ──GraphQL──> API (Caliban)
                    │
                    │ 1) INSERT booking under savepoint
                    │    - OK → commit & return ID
                    │    - 23P01 overlap:
                    │        a) rollback TO savepoint (tx usable)
                    │        b) look up bookings, compute suggestions
                    │        c) INSERT conflict into outbox
                    │        d) raise GraphQL error (Caliban puts into errors[])
                    │
                    └──(background) Outbox Relay (fs2)
                           ├─ SELECT unpublished
                           ├─ publish to Kafka (topic: booking_conflicts)
                           └─ mark published_at=now()
                                      │
                                      ▼
                                Kafka (broker)
                                      │
                                      ▼
                           Consumer (fs2-kafka)
                           ├─ decode ConflictEvent (Circe)
                           ├─ INSERT booking_conflicts (idempotent)
                           └─ commit offsets
```

## GraphQL API
- Example queries & schema path: `core/src/main/graphql`

## Running locally

### Prereqs
- JDK 21+
- Scala 3
- Docker (for Postgres & Kafka)
- sbt

### Environment
- Environment defaults stored in `.env`.

### Start services
```bash
docker compose up
```

### Run the API
```bash
sbt "project api" run
```

### Run the consumer
```bash
sbt "project consumer" run
```

### Run tests
```bash
sbt test
```
