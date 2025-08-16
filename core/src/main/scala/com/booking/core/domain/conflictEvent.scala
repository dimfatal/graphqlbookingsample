package com.booking.core.domain

import doobie.Write
import doobie.postgres.circe.jsonb.implicits.*
import doobie.postgres.implicits.*
import doobie.util.Read
import io.circe.{Codec, Json}
import io.circe.syntax.*
import java.time.LocalDate
import java.util.UUID

final case class Interval(begin: LocalDate, end: LocalDate) derives Codec.AsObject
final case class ConflictEventPayload(
    correlationId: UUID,
    suggestions: List[Interval],
    createBooking: CreateBooking
) derives Codec.AsObject

object ConflictEventPayload {
  implicit val conflictEvent: Write[ConflictEventPayload] =
    Write[(UUID, UUID, LocalDate, LocalDate, String, String, Json)].contramap { event =>
      import event.*
      import event.createBooking.*
      (correlationId, homeId, fromDate, toDate, guestEmail, source, suggestions.asJson)
    }
}

final case class ConflictEventOutboxRow(
    eventId: UUID,
    correlationId: UUID,
    payload: Json,
    createdAt: LocalDate,
    publishedAt: Option[LocalDate]
)

object ConflictEventOutboxRow {
  implicit val conflictEventOutboxRowRead: Read[ConflictEventOutboxRow] =
    Read[(UUID, UUID, Json, LocalDate, Option[LocalDate])]
      .map { case (eventId, correlationId, payload, createdAt, publishedAt) =>
        ConflictEventOutboxRow(eventId, correlationId, payload, createdAt, publishedAt)
      }
}
