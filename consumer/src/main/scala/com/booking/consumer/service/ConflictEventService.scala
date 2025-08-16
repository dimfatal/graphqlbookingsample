package com.booking.consumer.service

import cats.effect.kernel.Async
import com.booking.core.domain.ConflictEventPayload
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.postgres.circe.jsonb.implicits.*
import doobie.postgres.implicits.*
import io.circe.syntax.*
import org.typelevel.log4cats.Logger

trait ConflictEventService[F[_]] {
  def insert(conflictEvent: ConflictEventPayload): F[Int]
}

object ConflictEventService {
  def apply[F[_]: Async](xa: HikariTransactor[F], logger: Logger[F]): ConflictEventService[F] =
    (event: ConflictEventPayload) =>
      ConfilctEventServiceSql
        .insertEvent(event)
        .transact(xa)
}

object ConfilctEventServiceSql {
  def insertEvent(conflictEvent: ConflictEventPayload): ConnectionIO[Int] = {
    import conflictEvent.*
    import conflictEvent.createBooking.*
    sql"""
    INSERT INTO booking.booking_conflicts
      (correlation_id, home_id, from_date, to_date, guest_email, source, suggested, occurred_at)
    VALUES
      ($correlationId,
       $homeId,
       $fromDate, 
       $toDate,
       $guestEmail,
       $source, 
       ${suggestions.asJson},
       now())
  """.update.run
  }
}
