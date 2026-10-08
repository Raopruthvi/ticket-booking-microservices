# Distributed Ticket Booking System

![CI](https://github.com/Raopruthvi/ticket-booking-microservices/actions/workflows/ci.yml/badge.svg)

A ticket booking backend split into three Spring Boot microservices that talk to each other through RabbitMQ. I built it to learn how backend systems handle things like two users trying to book the same seat, messages being delivered more than once, and a service failing halfway through a request.

Built with Java, Spring Boot, RabbitMQ, PostgreSQL and Docker. Tested with JUnit 5, Mockito and Testcontainers.

## Why I built this

Most of my earlier projects were single-service CRUD apps. I wanted to build something where services have to cooperate without calling each other directly, and where I had to think about what goes wrong, not just the happy path. This project made me learn asynchronous messaging, idempotent consumers, dead-letter queues, optimistic locking and how to test concurrent code.

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
                     │    locking + retries  │
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

The reservation logic in the Inventory Service is split into two classes:

- **`SeatBooker.attemptReservation`** runs in a single database transaction. It loads the seat, and if it is `AVAILABLE` it sets it to `BOOKED`. It also saves a `ProcessedMessage` record with the outcome in the same transaction, so the seat update and the idempotency record are saved together or not at all. If the seat is missing or already booked, it records that failure instead.
- **`SeatReservationService.reserveSeat`** first checks whether this `bookingId` was already processed and, if so, returns the stored outcome. Otherwise it calls `SeatBooker` inside a retry loop (up to 3 attempts). The loop lives outside the transaction so every retry starts with a fresh transaction and a fresh read of the seat.

The `Seat` entity has a JPA `@Version` field. If two transactions load the same seat and both try to update it, only the first commit succeeds and the second one fails with an `OptimisticLockingFailureException`. The service then retries, sees the seat is no longer available and fails cleanly with `SEAT_ALREADY_BOOKED`. If it keeps conflicting, it gives up with `SEAT_CONTENDED_TOO_MANY_RETRIES`.

**What protects the seat in the default setup:** the Inventory listener runs with a single consumer, so RabbitMQ hands it one booking request at a time and the status check rejects later requests for the same seat. `@Version` is what keeps the data correct if several writers touch the same seat at once, for example with multiple consumer threads or several Inventory instances. The concurrency test below exercises exactly that case.

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

There are 18 automated tests across the Booking and Inventory services. GitHub Actions runs them for every push and pull request (see the badge at the top).

### Inventory Service (8 tests)

- **`SeatBookerTest`** (Mockito): seat not found, seat already booked, and successful booking. It checks the seat is saved as `BOOKED` and the outcome is recorded.
- **`SeatReservationServiceTest`** (Mockito): a duplicate `bookingId` returns the stored outcome without touching the seat, a lock conflict followed by success is retried, and three conflicts in a row end with `SEAT_CONTENDED_TOO_MANY_RETRIES`.
- **`SeatReservationConcurrencyTest`** (Testcontainers with a real PostgreSQL): 20 threads are released at the same moment and all try to book the same seat. The test asserts that exactly one booking succeeds, the seat ends up `BOOKED`, and all 20 outcomes were recorded. It calls the service directly rather than going through RabbitMQ, because the queue would otherwise hand the requests over one at a time and the `@Version` check would never be exercised. I confirmed the test is meaningful by removing `@Version` from `Seat`: the test then fails.

### Booking Service (10 tests)

- **`BookingServiceTest`** (Mockito): a booking is saved as `PENDING` before the event is published, the event carries the same `bookingId`, and unknown bookings are rejected.
- **`ReservationResultListenerTest`** (Mockito): success confirms the booking, failure records the reason, duplicate results for a finished booking are ignored, and results for unknown bookings are ignored.
- **`BookingControllerTest`** (`@WebMvcTest`): a valid request returns 202, a missing field returns 400, and an unknown booking returns 404.

### Running the tests

```bash
cd inventory-service && mvn test   # needs Docker running for the concurrency test
cd booking-service && mvn test
```

If Testcontainers cannot find Docker on a very recent Docker Desktop, create a file named `.docker-java.properties` in your user home folder containing `api.version=1.44`.

### End-to-end check with several requests for one seat

`concurrency-test.ps1` sends several booking requests for the same seat at almost the same time (as parallel background jobs) against the full running stack, then checks each booking's final status and prints a summary.

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

This shows that, through the whole system including RabbitMQ, a seat is never sold twice when several clients request it together. Because the Inventory listener processes messages one at a time, it is a small end-to-end check and not a stress test of `@Version`. That is what the concurrency test above is for.

## Project structure

```
ticket-booking-system/
├── booking-service/       # Public REST API, orchestrates the flow
├── inventory-service/     # Owns seat state and the reservation logic
├── notification-service/  # Consumes the final status, sends (mock) notifications
├── .github/workflows/     # CI: runs the tests on every push
├── docker-compose.yml     # Runs all of the above plus RabbitMQ and Postgres
├── concurrency-test.ps1   # End-to-end several-requests-for-one-seat check
└── README.md
```

## Known limitations

- A booking is saved first and the message is published afterwards. If the Booking Service crashed between the two, the booking would stay `PENDING` forever. A transactional outbox would solve this.
- There is no timeout for bookings that stay `PENDING`.
- Only the booking-requested queue has a dead-letter queue.
- The Notification Service is not idempotent and has no tests.
- Clients have to poll for the booking status.
- Database and RabbitMQ credentials in the config files are local development defaults.

## Ideas for next steps

- A transactional outbox in the Booking Service to remove the save-then-publish gap.
- Seat holds that expire after a timeout, with a scheduled job that fails stale `PENDING` bookings.
- Metrics with Prometheus and Grafana.