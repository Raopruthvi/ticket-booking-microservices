package com.booking.inventory.service;

import com.booking.inventory.entity.Event;
import com.booking.inventory.entity.Seat;
import com.booking.inventory.repository.EventRepository;
import com.booking.inventory.repository.ProcessedMessageRepository;
import com.booking.inventory.repository.SeatRepository;
import com.booking.inventory.service.SeatReservationService.ReservationOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Testcontainers
class SeatReservationConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired SeatReservationService seatReservationService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired ProcessedMessageRepository processedMessageRepository;

    @Test
    void onlyOneOfManyConcurrentBookingsForTheSameSeatSucceeds() throws Exception {
        Event event = eventRepository.save(Event.builder()
                .name("Test Event").venue("Test Arena")
                .eventTime(LocalDateTime.now().plusDays(1)).build());
        seatRepository.save(Seat.builder()
                .event(event).seatNumber("S1").status(Seat.SeatStatus.AVAILABLE).build());

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ReservationOutcome>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String bookingId = "booking-" + i;
            futures.add(pool.submit(() -> {
                ready.countDown();   // "I'm at the starting line"
                go.await();          // wait for the starting gun
                return seatReservationService.reserveSeat(bookingId, event.getId(), "S1");
            }));
        }

        ready.await();   // wait until all 20 threads are lined up
        go.countDown();  // fire them all at once

        int successes = 0;
        for (Future<ReservationOutcome> future : futures) {
            if (future.get(30, TimeUnit.SECONDS).success()) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);

        Seat seat = seatRepository.findByEventIdAndSeatNumber(event.getId(), "S1").orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.BOOKED);
        assertThat(processedMessageRepository.count()).isEqualTo(threads);
    }
}