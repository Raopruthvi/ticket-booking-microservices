# Distributed Ticket Booking System

A microservices-based ticket booking system built to explore real distributed-systems
problems: **concurrency control** (preventing double-booking), **asynchronous
communication** (RabbitMQ), and **idempotent, fault-tolerant event processing**.

Built with Java, Spring Boot, RabbitMQ, PostgreSQL, and Docker.

## Why this exists

Most fresher/portfolio projects are single-service CRUD apps. This project instead
tackles a genuinely hard problem: what happens when two users try to book the same
seat at the exact same instant? The answer involves optimistic locking, retries,
idempotent consumers, and dead-letter queues — concepts that come up constantly in
backend interviews.

## Architecture

```
                     ┌─────────────────────┐
   Client  ────────► │   Booking Service    │  (REST API, port 8080)
                     │   Postgres: booking  │
                     └──────────┬───────────┘
                                │ publishes "booking.requested"
                                ▼
                     ┌─────────────────────┐
                     │      RabbitMQ        │  (topic exchange: booking.exchange)
                     └──────────┬───────────┘
                                │
                                ▼
                     ┌─────────────────────┐
                     │  Inventory Service    │  (port 8081)
                     │  Postgres: inventory  │
                     │                       │
                     │  - optimistic locking │
                     │    (@Version) to stop │
                     │    double-booking     │
                     │  - idempotent consumer│
                     │  - dead-letter queue  │
                     └──────────┬───────────┘
                                │ publishes "seat.reservation.result"
                                ▼
                     ┌─────────────────────┐
                     │   Booking Service     │ (consumes result,
                     │   (same service)      │  updates booking status)
                     └──────────┬───────────┘
                                │ publishes "booking.status"
                                ▼
                     ┌─────────────────────┐
                     │ Notification Service  │  (port 8082)
                     │ (mock email via logs) │
                     └─────────────────────┘
```

## The core problem this solves: double-booking

Two users hit "book" on seat `S5` of the same event at the same millisecond.
Without protection, both requests could read "AVAILABLE" and both write "BOOKED" —
selling the same seat twice.

**Solution: optimistic locking via JPA's `@Version`.**
Every `Seat` row has a `version` column that Hibernate auto-increments on update.
If two transactions read the same seat and both try to update it, only the first
commit succeeds — the second gets an `OptimisticLockingFailureException` instead of
silently overwriting. The `SeatReservationService` catches this and retries with
fresh data, failing cleanly (`SEAT_ALREADY_BOOKED`) if the seat is genuinely gone.

See `inventory-service/.../service/SeatReservationService.java` for the full logic,
and `SeatRepository.java` for an alternative pessimistic-locking (`SELECT FOR
UPDATE`) approach kept in for comparison.

## Other things this project demonstrates

- **Idempotent consumers** — RabbitMQ guarantees at-least-once delivery, not
  exactly-once. Each service tracks processed message/booking IDs so a redelivered
  message doesn't get double-processed.
- **Dead-letter queues** — if the Inventory Service listener fails repeatedly
  (e.g. DB outage), the message is routed to a DLQ instead of being lost or
  retried forever.
- **Service-per-database** — each service owns its own Postgres database; there's
  no shared schema, so services can be deployed/scaled independently.
- **Async orchestration without a central orchestrator** — services react to events
  rather than being commanded step-by-step, which is closer to how this is done in
  production systems than a simple synchronous REST-call chain.

## Running it

Requires Docker and Docker Compose.

```bash
git clone <this-repo-url>
cd ticket-booking-system
docker-compose up --build
```

This starts: RabbitMQ (+ management UI), two Postgres instances, and all three
services. Wait ~30-60 seconds for everything to become healthy.

- Booking Service: http://localhost:8080
- Inventory Service: http://localhost:8081
- Notification Service: http://localhost:8082
- RabbitMQ management UI: http://localhost:15672 (guest/guest)

## Demo walkthrough

**1. Create an event with seats (Inventory Service):**
```bash
curl -X POST http://localhost:8081/api/inventory/events \
  -H "Content-Type: application/json" \
  -d '{"name": "Coldplay Live", "venue": "City Arena", "numberOfSeats": 10}'
```
Note the returned `id` — you'll need it as `eventId` below.

**2. Check seat availability:**
```bash
curl http://localhost:8081/api/inventory/events/1/seats
```

**3. Book a seat (Booking Service):**
```bash
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"eventId": 1, "seatNumber": "S1", "userId": "user-123"}'
```
This returns immediately with status `PENDING` — the actual reservation happens
asynchronously.

**4. Poll for the result:**
```bash
curl http://localhost:8080/api/bookings/<bookingId-from-step-3>
```
Status should flip to `CONFIRMED` within a second or two. Check the
notification-service logs (`docker logs notification-service`) to see the mock
confirmation email.

**5. Try to double-book the same seat** (run step 3 again with the same
`seatNumber`) — the second request will resolve to `FAILED` with reason
`SEAT_ALREADY_BOOKED`.

## Interactive API docs (Swagger UI)

Both Booking Service and Inventory Service expose a Swagger UI, so you can browse
and try every endpoint from the browser instead of the command line:

- Booking Service: http://localhost:8080/swagger-ui.html
- Inventory Service: http://localhost:8081/swagger-ui.html

## Proving the concurrency control with a real race condition

`concurrency-test.ps1` fires several booking requests at the *same* seat
essentially simultaneously (as background jobs, not one after another), then
polls each one's final status and prints a summary. This is a repeatable way
to demonstrate the optimistic locking under genuine concurrent load, not just
a single sequential retry.

```powershell
# 1. Create an event with seats first (see step 1 above), then:
.\concurrency-test.ps1 -EventId 1 -SeatNumber "S1" -Requests 5
```

Expected output: exactly 1 `CONFIRMED` and the rest `FAILED` with reason
`SEAT_ALREADY_BOOKED` — no matter how many requests you fire at the same seat.

## Verified

This flow has been tested end-to-end against the running Docker Compose stack.

**Happy path — booking a seat:**
```bash
curl -X POST http://localhost:8080/api/bookings -H "Content-Type: application/json" \
  -d '{"eventId": 1, "seatNumber": "S1", "userId": "user-123"}'
# → {"bookingId":"41ec11de-3659-44fb-95e4-e2f794b89670","status":"PENDING", ...}

curl http://localhost:8080/api/bookings/41ec11de-3659-44fb-95e4-e2f794b89670
# → {"status":"CONFIRMED", ...}
```

**Concurrency control — 5 simultaneous requests for the same seat:**
Ran `concurrency-test.ps1` firing 5 truly concurrent booking requests at the
same seat. Actual output:
```
CONFIRMED  -> bookingId=9f40870c-... userId=concurrent-user-1
FAILED     -> bookingId=1801235a-... userId=concurrent-user-2 reason=SEAT_ALREADY_BOOKED
FAILED     -> bookingId=02799eb4-... userId=concurrent-user-3 reason=SEAT_ALREADY_BOOKED
FAILED     -> bookingId=a656ef25-... userId=concurrent-user-4 reason=SEAT_ALREADY_BOOKED
FAILED     -> bookingId=3f3f8c62-... userId=concurrent-user-5 reason=SEAT_ALREADY_BOOKED

Summary: 1 CONFIRMED, 4 FAILED, 0 STILL PENDING (out of 5 requests for the same seat)
```

No seat was double-booked, even under a genuine 5-way race, not just a single
one-on-one collision. This confirms the `@Version`-based optimistic locking
in `SeatReservationService` works as intended: when multiple transactions race to
update the same seat row, only the first commit succeeds — every other
transaction is rejected by Hibernate's version check, caught, and resolved into a clean
failure response rather than corrupting data or silently overwriting the winner.

## Project structure

```
ticket-booking-system/
├── booking-service/       # Public REST API, orchestrates the flow
├── inventory-service/     # Owns seat state, concurrency control lives here
├── notification-service/  # Consumes final status, sends (mock) notifications
├── docker-compose.yml     # Orchestrates all of the above + RabbitMQ + Postgres
└── README.md
```

## What I'd add with more time

- Redis-based distributed lock as an alternative to DB-level optimistic locking
- A seat "hold" step (temporary lock while user is on a payment page) with TTL expiry
- WebSocket/SSE push instead of client polling for booking status
- Centralized logging (ELK) and metrics (Prometheus/Grafana) across services
- API Gateway (Spring Cloud Gateway) in front of Booking Service