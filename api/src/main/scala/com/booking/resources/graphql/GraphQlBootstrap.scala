package com.booking.resources.graphql

import caliban.{graphQL, CalibanError, GraphQLInterpreter, RootResolver}
import caliban.CalibanError.ExecutionError
import caliban.interop.cats.{CatsInterop, FromEffect}
import caliban.interop.cats.implicits.{*, given}
import caliban.schema.Schema
import caliban.wrappers.Wrappers.{maxDepth, printErrors, printSlowQueries}
import cats.Applicative
import cats.effect.*
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.implicits.*
import cats.syntax.*
import com.booking.core.{AuthContext, AuthInfo}
import com.booking.core.graphql.ID
import com.booking.core.graphql.Operations.{Mutation, Query}
import com.booking.core.graphql.Types
import com.booking.graphql.RequestError
import com.booking.service.PublishConflictsService
import com.booking.service.mutation.BookingMutation
import com.booking.service.query.BookingQuery
import doobie.hikari.HikariTransactor
import zio.durationInt

object GraphQlBootstrap {
  private def mapDomainErrors(e: CalibanError): CalibanError =
    e match
      case ee @ CalibanError.ExecutionError(_, _, _, cause, _) =>
        cause
          .flatMap {
            case RequestError.InputValidationError(m) =>
              Some(ee.copy(msg = s"validation error - $m"))
            case RequestError.PostgresError(m)        =>
              Some(ee.copy(msg = s"postgres error - $m"))
            case _                                    =>
              None
          }
          .getOrElse(ee)

      case other => other

  def mkGraphQL[F[_]: Async: AuthContext: Dispatcher](
      xa: HikariTransactor[F],
      publishConflicts: PublishConflictsService[F]
  )(using
      interop: CatsInterop[F, AuthInfo]
  ): F[GraphQLInterpreter[AuthInfo, CalibanError]] =
    import com.booking.graphql.codec.*
    import com.booking.graphql.schema.*
    given Schema[AuthInfo, Query[F]]    = Schema.gen
    given Schema[AuthInfo, Mutation[F]] = Schema.gen
    given HikariTransactor[F]           = xa
    given PublishConflictsService[F]    = publishConflicts
    (graphQL(resolver[F])
      @@ maxDepth(10)
      @@ printSlowQueries(500.milliseconds)
      @@ printErrors)
      .interpreterF[F]
      .map(_.mapError(mapDomainErrors))

  private def resolver[F[_]: Async: HikariTransactor: AuthContext: PublishConflictsService] = RootResolver(
    query[F],
    mutation[F]
  )

  private def query[F[_]: HikariTransactor: AuthContext](using F: Async[F]) =
    lazy val bookingsQuery: BookingQuery[F] = BookingQuery[F]()
    Query[F](
      bookings = args => bookingsQuery.bookings(args.homeId)
    )

  private def mutation[F[_]: HikariTransactor: PublishConflictsService: AuthContext](using F: Async[F]) =
    lazy val bookingsMutation: BookingMutation[F] = BookingMutation[F]()
    Mutation[F](
      createBooking = args => bookingsMutation.createBooking(args)
    )
}
