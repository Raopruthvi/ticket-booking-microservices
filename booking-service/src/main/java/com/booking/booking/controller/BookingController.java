package com.booking.booking.controller;

import com.booking.booking.dto.ApiModels.BookingResponse;
import com.booking.booking.dto.ApiModels.CreateBookingRequest;
import com.booking.booking.entity.Booking;
import com.booking.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
//Controller classes act as front door to our springboot applications
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Create and check the status of ticket bookings")
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Create a booking request",
            description = "Publishes a BookingRequested event to Inventory Service and returns immediately with status PENDING. Poll GET /{bookingId} for the final result.")
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

    @Operation(summary = "Get a booking's current status",
            description = "Returns PENDING, CONFIRMED, or FAILED (with a failureReason if applicable).")
    @GetMapping("/{bookingId}")
    public ResponseEntity<Booking> getBooking(@PathVariable String bookingId) {
        return ResponseEntity.ok(bookingService.getBooking(bookingId));
    }
}
