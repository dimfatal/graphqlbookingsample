package com.booking.domain

import com.booking.core.domain.ConflictEventOutboxRow
import com.booking.core.graphql.ID
import com.booking.core.graphql.Types.Booking
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits.*
import doobie.util.Read
import doobie.util.meta.Meta
import io.circe.{Codec, Json}
import io.circe.syntax._
import java.time.LocalDate
import java.util.UUID

object bookingCodec {
  implicit val idMeta: Meta[ID]           = Meta[UUID].imap(ID(_))(_.value)
  implicit val bookingRead: Read[Booking] =
    Read[(UUID, UUID, String, String, String, String, String)]
      .map { case (id, homeId, fromDate, toDate, guestEmail, source, createdAt) =>
        Booking(id.toString, homeId.toString, fromDate, toDate, guestEmail, source, createdAt)
      }
}
