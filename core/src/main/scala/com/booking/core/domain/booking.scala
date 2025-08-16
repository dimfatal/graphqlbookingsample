package com.booking.core.domain

import io.circe.Codec
import java.time.LocalDate
import java.util.UUID

final case class CreateBooking(
    homeId: UUID,
    fromDate: LocalDate,
    toDate: LocalDate,
    guestEmail: String,
    source: String
) derives Codec.AsObject

final case class BookingRow(
    id: UUID,
    homeId: UUID,
    fromDate: LocalDate,
    toDate: LocalDate,
    guestEmail: String,
    source: String
)
