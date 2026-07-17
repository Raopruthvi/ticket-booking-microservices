package com.booking.booking.controller;

import com.booking.booking.dto.ApiModels.BookingResponse;
import com.booking.booking.dto.ApiModels.CreateBookingRequest;
import com.booking.booking.entity.Booking;
import com.booking.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        Booking booking = bookingService.createBooking(
                request.getEventId(), request.getSeatNumber(), request.getUserId());

        // Note: this returns PENDING immediately - the actual reservation
        // happens asynchronously via RabbitMQ. Client polls GET /api/bookings/{id}
        // (or, in a fuller version, you'd push updates via WebSocket/SSE).
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                new BookingResponse(booking.getId(), booking.getStatus().name(),
                        "Booking request received, processing")
        );
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<Booking> getBooking(@PathVariable String bookingId) {
        return ResponseEntity.ok(bookingService.getBooking(bookingId));
    }
}
