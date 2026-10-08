package com.booking.booking.dto;

import lombok.*;

import java.io.Serializable;

// Published by this service, consumed by Inventory Service
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

//Instead of processing the entire booking synchronously while the user waits on the webpage,
// your REST controller accepts the request and immediately drops this BookingRequestedEvent into a message queue.
public class BookingRequestedEvent implements Serializable {
    private String bookingId;
    private Long eventId;
    private String seatNumber;
    private String userId;
}


//Serializable keyword tells the Java Virtual Machine (JVM) that this class can be converted into a raw binary stream of bytes.