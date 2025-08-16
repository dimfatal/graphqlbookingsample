package com.booking.service.query

import cats.effect.MonadCancelThrow
import com.booking.domain.bookingCodec.*
import com.booking.core.graphql.ID
import com.booking.core.graphql.Types.Booking
import doobie.hikari.HikariTransactor
import doobie.implicits.*

trait BookingQuery[F[_]] {
  def bookings(
      homeId: ID
  ): F[List[Booking]]
}

object BookingQuery {
  def apply[F[_]]()(using F: MonadCancelThrow[F], xa: HikariTransactor[F]): BookingQuery[F] = (homeId: ID) =>
    sql"""
        SELECT id, home_id, from_date, to_date, guest_email, source, created_at
        FROM bookings
        WHERE home_id = $homeId
      """
      .query[Booking]
      .to[List]
      .transact(xa)
}
