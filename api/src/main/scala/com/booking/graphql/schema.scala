package com.booking.graphql

import caliban.interop.cats.implicits.*
import caliban.schema.Schema
import caliban.schema.Schema.auto.field
import cats.effect.std.Dispatcher
import com.booking.core.graphql.Types.Booking

object schema {
  implicit def booking[F[_]: Dispatcher, R]: Schema[R, Booking] =
    Schema.obj("Booking", Some("booking"), Nil) { implicit fields =>
      List(
        field("id", None, Nil)(_.id),
        field("homeId", None, Nil)(_.homeId),
        field("fromDate", Some("Format: YYYY-MM-DD"), Nil)(_.fromDate),
        field("toDate", Some("Format: YYYY-MM-DD"), Nil)(_.toDate),
        field("guestEmail", None, Nil)(_.guestEmail),
        field("source", None, Nil)(_.source),
        field("createdAt", None, Nil)(_.createdAt)
      )
    }

  implicit def bookingF[F[_]: Dispatcher, R]: Schema[R, F[Booking]] = catsEffectSchema

}
