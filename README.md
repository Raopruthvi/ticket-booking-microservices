# Distributed Ticket Booking System

A ticket booking backend split into three Spring Boot microservices that talk to each other through RabbitMQ. I built it to learn how backend systems handle things like two users trying to book the same seat, messages being delivered more than once, and a service failing halfway through a request.

Built with Java, Spring Boot, RabbitMQ, PostgreSQL and Docker.

## Why I built this

Most of my earlier projects were single-service CRUD apps. I wanted to build something where services have to cooperate without calling each other directly, and where I had to think about what goes wrong, not just the happy path. This project made me learn asynchronous messaging, idempotent consumers, dead-letter queues and optimistic locking.

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
                     │  - seat status check  │
                     │  - @Version optimistic│
                     │    locking            │
                     │  - idempotency table  │
                     │  - dead-letter queue  │
                     └──────────┬───────────┘
                                │ publishes "seat.reservation.result"
                                ▼
                     ┌─────────────────────┐
                     │   Booking Service     │ (consumes the result,
                     │   (same service)      │  updates booking status)
                     └──────────┬───────────┘
                                │ publishes "booking.status"
                                ▼
                     ┌─────────────────────┐
                     │ Notification Service  │  (port 8082)
                     │ (mock email via logs) │
                     └─────────────────────┘
```

### How a booking flows

1. The client sends `POST /api/bookings` to the Booking Service.
2. The Booking Service saves a booking with status `PENDING`, publishes a `BookingRequested` event and immediately returns `202 Accepted`. It does not wait for the seat to be reserved.
3. The Inventory Service picks up the event, checks the seat and either books it or rejects the request.
4. The Inventory Service publishes the result. The Booking Service consumes it and sets the booking to `CONFIRMED` or `FAILED` (with a reason).
5. The Booking Service publishes a status event and the Notification Service logs a mock email.
6. The client checks the final result with `GET /api/bookings/{id}`.

The `bookingId` (a UUID) is created once in the Booking Service and travels through every step. The consumers use it to recognise messages they have already handled.

## Preventing double booking

When a booking request reaches the Inventory Service, `SeatReservationService` looks up the seat. If the seat is not `AVAILABLE`, the request fails with `SEAT_ALREADY_BOOKED`. If it is available, it is set to `BOOKED` and saved.

The `Seat` entity also has a JPA `@Version` field. If two transactions load the same seat and both try to update it, only the first update succeeds and the second one fails with an `OptimisticLockingFailureException`. The service retries up to 3 times with fresh data. If the seat is no longer available, it fails cleanly with `SEAT_ALREADY_BOOKED`, and if it keeps conflicting it gives up with `SEAT_CONTENDED_TOO_MANY_RETRIES`.

**What actually protects the seat in the default setup:** the Inventory listener runs with a single consumer, so RabbitMQ hands it one booking request at a time. That means requests for the same seat are handled one after another, and the status check is what rejects the later ones. The `@Version` check is an extra safety net for the case where several writers touch the same seat at once, for example if I ran multiple consumer threads or scaled the Inventory Service to more than one instance.

`SeatRepository` also has a pessimistic locking query (`SELECT ... FOR UPDATE`). It is there so I can compare the two approaches, but the reservation flow does not use it.

## Other things this project covers

- **Idempotent processing.** RabbitMQ guarantees at-least-once delivery, so the same message can arrive twice. The Inventory Service stores every processed `bookingId` together with its outcome in a `processed_messages` table and replays the stored result for duplicates. The Booking Service skips results for bookings that are no longer `PENDING`. (The Notification Service just logs, so a duplicate message would produce a duplicate log line.)
- **Dead-letter queue.** If the Inventory listener keeps failing (for example if its database is down), it retries 3 times with exponential backoff (1s, then 2s). After that the message is rejected and the queue's dead-letter settings route it to `inventory.booking-requested.dlq` instead of losing it. This is configured for the booking-requested queue only.
- **Database per service.** Booking and Inventory each have their own PostgreSQL database and no shared schema.
- **Event-driven flow.** The services react to events instead of calling each other step by step over REST.
- **Validation and error handling.** Request validation with Bean Validation and a global exception handler that returns proper 400 and 404 responses.
- **API docs.** Swagger UI on the Booking and Inventory services.

## Running it

Requires Docker and Docker Compose.

```bash
git clone <this-repo-url>
cd <repo-folder>
docker-compose up --build
```

This starts RabbitMQ (with its management UI), two PostgreSQL databases and the three services. Give it 30 to 60 seconds to become healthy.

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
Note the returned `id`. You need it as `eventId` below.

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
This returns immediately with status `PENDING`. The reservation itself happens asynchronously.

**4. Check the result:**
```bash
curl http://localhost:8080/api/bookings/<bookingId-from-step-3>
```
The status should change to `CONFIRMED` within a second or two. The mock confirmation email shows up in `docker logs notification-service`.

**5. Try to book the same seat again** (run step 3 again with the same `seatNumber`). The second booking ends as `FAILED` with reason `SEAT_ALREADY_BOOKED`.

## Interactive API docs (Swagger UI)

- Booking Service: http://localhost:8080/swagger-ui.html
- Inventory Service: http://localhost:8081/swagger-ui.html

## Testing

### End-to-end check with several requests for one seat

`concurrency-test.ps1` sends several booking requests for the same seat at almost the same time (as parallel background jobs), then checks each booking's final status and prints a summary.

```powershell
# Create an event with seats first (see step 1 above), then:
.\concurrency-test.ps1 -EventId 1 -SeatNumber "S1" -Requests 5
```

Output from one of my runs with 5 requests:
```
CONFIRMED  -> bookingId=9f40870c-... userId=concurrent-user-1
FAILED     -> bookingId=1801235a-... userId=concurrent-user-2 reason=SEAT_ALREADY_BOOKED
FAILED     -> bookingId=02799eb4-... userId=concurrent-user-3 reason=SEAT_ALREADY_BOOKED
FAILED     -> bookingId=a656ef25-... userId=concurrent-user-4 reason=SEAT_ALREADY_BOOKED
FAILED     -> bookingId=3f3f8c62-... userId=concurrent-user-5 reason=SEAT_ALREADY_BOOKED

Summary: 1 CONFIRMED, 4 FAILED, 0 STILL PENDING (out of 5 requests for the same seat)
```

This shows that, end to end, a seat is never sold twice when several clients request it together. It is a small check, not a stress test. Because the Inventory listener processes messages one at a time, the requests reach the seat one after another, so this script does not by itself exercise the `@Version` conflict path.

### Automated tests

I am adding automated tests next (see below). They are not in the project yet.

## Project structure

```
ticket-booking-system/
├── booking-service/       # Public REST API, orchestrates the flow
├── inventory-service/     # Owns seat state and the reservation logic
├── notification-service/  # Consumes the final status, sends (mock) notifications
├── docker-compose.yml     # Runs all of the above plus RabbitMQ and Postgres
├── concurrency-test.ps1   # Several-requests-for-one-seat check
└── README.md
```

## Known limitations

- A booking is saved first and the message is published afterwards. If the Booking Service crashed between the two, the booking would stay `PENDING` forever. A transactional outbox would solve this.
- There is no timeout for bookings that stay `PENDING`.
- Only the booking-requested queue has a dead-letter queue.
- The Notification Service is not idempotent.
- Clients have to poll for the booking status.
- Database and RabbitMQ credentials in the config files are local development defaults.

## What I plan to add next

- Unit tests (JUnit and Mockito) for the reservation and result-handling logic.
- An integration test using Testcontainers and a real PostgreSQL that fires many threads at the same seat directly at the service layer, so the `@Version` conflict and retry path is exercised.
- GitHub Actions to run the tests on every push.
- Later, if time allows: a seat hold with an expiry, and metrics with Prometheus and Grafana.