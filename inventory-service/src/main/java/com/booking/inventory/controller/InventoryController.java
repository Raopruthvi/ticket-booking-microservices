package com.booking.inventory.controller;

import com.booking.inventory.entity.Event;
import com.booking.inventory.entity.Seat;
import com.booking.inventory.repository.EventRepository;
import com.booking.inventory.repository.SeatRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Seed events/seats and check live seat availability")
public class InventoryController {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    public record CreateEventRequest(String name, String venue, int numberOfSeats) {}

    @Operation(summary = "Create an event with N seats (S1, S2, ... SN), all starting AVAILABLE")
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

    @Operation(summary = "List all seats for an event with their live status (AVAILABLE / LOCKED / BOOKED)")
    @GetMapping("/events/{eventId}/seats")
    public ResponseEntity<List<Seat>> getSeats(@PathVariable Long eventId) {
        return ResponseEntity.ok(seatRepository.findByEventId(eventId));
    }
}
