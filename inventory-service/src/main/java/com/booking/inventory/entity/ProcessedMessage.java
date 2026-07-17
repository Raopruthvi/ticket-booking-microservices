package com.booking.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// RabbitMQ guarantees "at-least-once" delivery, not "exactly-once".
// A message can be redelivered (e.g. if the consumer crashes after
// processing but before acking). Without tracking what we've already
// processed, a redelivered "BookingRequested" event could reserve a
// second seat for the same booking. This table is our idempotency guard.
@Entity
@Table(name = "processed_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedMessage {

    @Id
    private String messageId; // the bookingId / idempotency key from the event

    private LocalDateTime processedAt;
}
