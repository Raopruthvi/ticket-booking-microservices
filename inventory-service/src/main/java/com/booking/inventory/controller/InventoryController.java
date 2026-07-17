package com.booking.inventory.controller;

import com.booking.inventory.entity.Event;
import com.booking.inventory.entity.Seat;
import com.booking.inventory.repository.EventRepository;
import com.booking.inventory.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    public record CreateEventRequest(String name, String venue, int numberOfSeats) {}

    @PostMapping("/events")
    public ResponseEntity<Event> createEvent(@RequestBody CreateEventRequest request) {
        Event event = Event.builder()
                .name(request.name())
                .venue(request.venue())
                .eventTime(LocalDateTime.now().plusDays(7))
                .build();
        event = eventRepository.save(event);

        for (int i = 1; i <= request.numberOfSeats(); i++) {
            Seat seat = Seat.builder()
                    .event(event)
                    .seatNumber("S" + i)
                    .status(Seat.SeatStatus.AVAILABLE)
                    .build();
            seatRepository.save(seat);
        }

        return ResponseEntity.ok(event);
    }

    @GetMapping("/events/{eventId}/seats")
    public ResponseEntity<List<Seat>> getSeats(@PathVariable Long eventId) {
        return ResponseEntity.ok(seatRepository.findByEventId(eventId));
    }
}
