package com.booking.service.mutation

import cats.effect.{MonadCancelThrow, Resource}
import cats.implicits.*
import com.booking.core.domain.*
import com.booking.core.graphql.ID
import com.booking.core.graphql.Types.MutationCreateBookingArgs
import com.booking.core.utils.{DoobieUtil, SuggestionLogic}
import com.booking.graphql.RequestError.PostgresError
import com.booking.graphql.Validation.*
import com.booking.service.PublishConflictsService
import doobie.*
import doobie.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.postgres.circe.jsonb.implicits.*
import doobie.postgres.implicits.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.util.UUID
import org.postgresql.util.PSQLException

trait BookingMutation[F[_]] {
  def createBooking(createBooking: MutationCreateBookingArgs): F[ID]
}

object BookingMutation {

  private def withSavepoint[A](fa: ConnectionIO[A]): ConnectionIO[Either[Throwable, A]] =
    Resource
      .make(DoobieUtil.savepoint())(sp => DoobieUtil.release(sp).attempt.void)
      .use { sp =>
        fa.attempt.flatTap {
          case Left(_)  => DoobieUtil.rollback(sp)
          case Right(_) => ().pure[ConnectionIO]
        }
      }

  private def guardedInsert(createBooking: CreateBooking): ConnectionIO[Either[PostgresError, UUID]] =
    withSavepoint(BookingMutationSql.createBookingUnsafe(createBooking))
      .flatMap {
        case Right(id) =>
          Right(id).pure[ConnectionIO]

        case Left(e: PSQLException) if e.getSQLState == "23P01" =>
          Left(
            PostgresError(
              s"Date conflict: the requested stay homeId - ${createBooking.homeId} from ${createBooking.fromDate} to ${createBooking.toDate} overlaps an existing booking."
            )
          ).pure[ConnectionIO]

        case Left(other) =>
          other.raiseError[ConnectionIO, Either[PostgresError, UUID]]
      }

  def apply[F[_]]()(using
      F: MonadCancelThrow[F],
      xa: HikariTransactor[F],
      publishConflictService: PublishConflictsService[F]
  ): BookingMutation[F] =
    (args: MutationCreateBookingArgs) =>
      args.validate.toEither match {
        case Left(inputValidationError) =>
          F.raiseError(inputValidationError)
        case Right(createBooking)       =>
          val program = for {
            attempt <- guardedInsert(createBooking)
            result  <- attempt match {
                         case Left(error) =>
                           val today       = LocalDate.now()
                           val lookupLimit = today.plusMonths(3)
                           val duration    = DAYS.between(createBooking.fromDate, createBooking.toDate).toInt
                           for {
                             reservations <-
                               BookingMutationSql.bookingsContainedIn(createBooking.homeId, today, lookupLimit)

                             busyIntervals  = reservations.map(r => Interval(r.fromDate, r.toDate))
                             availableDates = SuggestionLogic.findFreeIntervals(busyIntervals, today, lookupLimit)
                             anchor         = if (today.isAfter(createBooking.toDate)) today else createBooking.toDate
                             suggestions    = List(
                                                SuggestionLogic.earliestFutureSuggestion(availableDates, anchor, duration),
                                                SuggestionLogic.latestPast(availableDates, today, lookupLimit, duration)
                                              ).flatten
                             _             <- BookingMutationSql.insertOutboxConflict(UUID.randomUUID(), createBooking, suggestions)
                           } yield error.asLeft[ID]
                         case Right(id)   =>
                           ID(id).asRight[PostgresError].pure[ConnectionIO]

                       }
          } yield result

          program.transact(xa).flatMap {
            case Left(error) =>
              publishConflictService.publish().attempt.void *>
                F.raiseError[ID](
                  caliban.CalibanError.ExecutionError(
                    s"postgres error - ${error.message}"
                  )
                )
            case Right(id)   => F.pure(id)
          }
      }

}

object BookingMutationSql {
  def createBookingUnsafe(createBooking: CreateBooking): ConnectionIO[UUID] =
    import createBooking.*
    sql"""
          INSERT INTO booking.bookings (home_id, from_date, to_date, guest_email, source)
          VALUES ($homeId, $fromDate, $toDate, $guestEmail, $source)
        """.update
      .withUniqueGeneratedKeys[UUID]("id")

  def insertOutboxConflict(
      correlationId: UUID,
      createBooking: CreateBooking,
      suggestions: List[Interval]
  ): ConnectionIO[UUID] = {
    val conflictEvent     =
      ConflictEventPayload(UUID.randomUUID(), createBooking = createBooking, suggestions = suggestions)
    import io.circe.syntax.*
    val conflictEventJson = conflictEvent.asJson
    val query             = sql"""
        INSERT INTO booking.booking_conflict_outbox (
           correlation_id, payload
        )
        VALUES (
           $correlationId, $conflictEventJson
         )
      """.update

    query
      .withUniqueGeneratedKeys[UUID]("event_id")

  }

  def bookingsContainedIn(homeId: UUID, start: LocalDate, end: LocalDate): ConnectionIO[List[BookingRow]] =
    sql"""
      SELECT id, home_id, from_date, to_date, guest_email, source
      FROM booking.bookings
      WHERE home_id = $homeId
        AND period <@ daterange($start::date, $end::date, '[)')  
      ORDER BY from_date ASC"""
      .query[BookingRow]
      .to[List]

}
