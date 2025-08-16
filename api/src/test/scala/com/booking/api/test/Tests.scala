package com.booking.api.test

import caliban.ResponseValue.*
import caliban.interop.cats.*
import caliban.interop.cats.implicits.*
import caliban.*
import cats.data.Kleisli
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.~>
import com.booking.core.AuthInfo
import com.booking.resources.graphql.GraphQlBootstrap
import com.booking.service.PublishConflictsService
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.zaxxer.hikari.HikariConfig
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import org.flywaydb.core.Flyway
import org.testcontainers.utility.DockerImageName
import weaver.*
import zio.ZEnvironment

import java.time.LocalDate
import java.util.UUID

object PostgresContainer {
  def make[F[_]](using F: Async[F]): Resource[F, PostgreSQLContainer] =
    Resource.make(F.delay {
      val sqlContainer =
        new PostgreSQLContainer(
          databaseName = Some("booking"),
          pgUsername = Some("user"),
          pgPassword = Some("1234"),
          dockerImageNameOverride = Some(DockerImageName.parse("postgres:15-alpine")),
        )
      sqlContainer.container.start();
      sqlContainer
    })(c => F.delay(c.container.stop()))
}

object HikaryTransactor {
  def make[F[_]](url: String, user: String, pass: String)(using F: Async[F]) = {
    val cfg = new HikariConfig()
    cfg.setJdbcUrl(url)
    cfg.setUsername(user)
    cfg.setPassword(pass)
    cfg.setMaximumPoolSize(4)
    cfg.setMinimumIdle(0)
    HikariTransactor.fromHikariConfig[F](cfg)
  }
}

object BookingGraphqlSuiteAdvanced extends IOSuite {

  type RIO[A] = Kleisli[IO, AuthInfo, A]
  given zio.Runtime[AuthInfo] = zio.Runtime.default.withEnvironment(ZEnvironment(AuthInfo.Empty))

  def shared: Resource[RIO, Handles] =
    for {
      pgContainer <- PostgresContainer.make[RIO]
      xa          <- HikaryTransactor.make[RIO](
                       "jdbc:postgresql://localhost:5432/booking?currentSchema=booking",
                       pgContainer.username,
                       pgContainer.password
                     )
      _           <- Resource.eval(summon[Async[RIO]].delay {
                       Flyway
                         .configure()
                         .dataSource(pgContainer.jdbcUrl, pgContainer.username, pgContainer.password)
                         .defaultSchema("booking")
                         .schemas("booking")
                         .createSchemas(true)
                         .load()
                         .migrate()

                     })

      publishSvc   = new PublishConflictsService[RIO] {
                       def publish() = summon[Async[RIO]].unit
                     }

      interpreter <- Dispatcher
                       .parallel[RIO]
                       .flatMap { case dispatcher @ (given Dispatcher[RIO]) =>
                         given interop: CatsInterop.Contextual[RIO, AuthInfo] = CatsInterop.contextual(dispatcher)
                         Resource.eval(GraphQlBootstrap.mkGraphQL[RIO](xa, publishSvc))
                       }
    } yield Handles(xa, interpreter)

  final case class Handles(
      xa: HikariTransactor[RIO],
      interpreter: GraphQLInterpreter[AuthInfo, CalibanError]
  )

  override type Res = Handles

  override def sharedResource: Resource[IO, Handles] = {
    val lower: (RIO ~> IO) = new (RIO ~> IO) {
      def apply[A](fa: RIO[A]): IO[A] = fa.run(AuthInfo.Empty)
    }
    shared.mapK(lower)
  }

  private def exec(
      itp: GraphQLInterpreter[AuthInfo, CalibanError],
      q: String
  ): IO[GraphQLResponse[CalibanError]] =
    itp.executeAsync[RIO](q).run(AuthInfo.Empty)

  private def createBookingQuery(home: UUID, from: String, to: String, email: String, source: String): String =
    s"""mutation {
           createBooking(
             homeId:"$home",
             fromDate:"$from",
             toDate:"$to",
             guestEmail:"$email",
             source:"$source"
           )
         }"""

  private def bookingQuery(homeId: UUID): String =
    s"""query { bookings(homeId:"$homeId") { id fromDate toDate guestEmail source } }"""

  // ---------- TESTS ----------
  private def field(obj: ResponseValue, name: String): Option[ResponseValue] =
    obj match {
      case ObjectValue(fs) => fs.collectFirst { case (`name`, v) => v }
      case _               => None
    }

  private def asList(rv: ResponseValue): List[ResponseValue] =
    rv match {
      case ListValue(items) => items;
      case _                => Nil
    }

  private def asString(rv: ResponseValue): Option[String] =
    rv match {
      case Value.StringValue(s) => Some(s);
      case _                    => None
    }

  private def bookingsCount(data: ResponseValue): Int =
    field(data, "bookings").map(asList).map(_.size).getOrElse(0)

  def expectTrue(cond: Boolean, msg: => String): Expectations =
    if (cond) expect(true) else failure(msg)

  test("HAPPY PATH: create -> list returns booking") { case Handles(_, interpreter) =>
    val homeId   = java.util.UUID.randomUUID()
    val booking1 = createBookingQuery(homeId, "2025-10-01", "2025-10-04", "a@b.com", "WEB")
    val booking2 = createBookingQuery(homeId, "2025-10-04", "2025-10-06", "b@c.com", "WEB")
    val query    = bookingQuery(homeId)

    for {
      r1   <- exec(interpreter, booking1)
      r2   <- exec(interpreter, booking2)
      rq   <- exec(interpreter, query)
      count = bookingsCount(rq.data)
    } yield expectTrue(r1.errors.isEmpty, s"create #1 failed: ${r1.errors.map(_.getMessage)}") &&
      expectTrue(r2.errors.isEmpty, s"create #2 failed: ${r2.errors.map(_.getMessage)}") &&
      expectTrue(count == 2, s"expected 2 bookings, got $count")

  }
  test("CONFLICT PATH: overlap fails; list has one; conflict recorded") { case Handles(xa, interpreter) =>
    val homeId            = UUID.randomUUID()
    val booking           = createBookingQuery(homeId, "2025-11-01", "2025-11-05", "a@b.com", "WEB")
    val overlapingBooking = createBookingQuery(homeId, "2025-11-03", "2025-11-06", "c@d.com", "WEB")
    val listBookingsQuery = bookingQuery(homeId)

    for {
      r1 <- exec(interpreter, booking)
      r2 <- exec(interpreter, overlapingBooking)

      r3   <- exec(interpreter, listBookingsQuery)
      count = bookingsCount(r3.data)
      _    <- TestSQL.drainOutboxOnce.transact(xa).run(AuthInfo.Empty)
      cnt  <- TestSQL.countConflicts(homeId).transact(xa).run(AuthInfo.Empty)
    } yield expectTrue(r1.errors.isEmpty, s"create #1 failed: ${r1.errors.map(_.getMessage)}") &&
      expectTrue(r2.errors.nonEmpty, s"expected overlap error, got: ${r2.data} / ${r2.errors}") &&
      expectTrue(count == 1, s"expected 1 bookings, got $count") &&
      expectTrue(cnt == 1, s"Expected 1 conflict row, got: $cnt")
  }

  test("RACE PATH: two concurrent creates; one succeeds, one fails; list one; conflict recorded") {
    case Handles(xa, interpreter) =>
      val homeId   = UUID.randomUUID()
      val booking1 = createBookingQuery(homeId, "2025-12-10", "2025-12-13", "x@y.com", "WEB")
      val booking2 = createBookingQuery(homeId, "2025-12-10", "2025-12-13", "x@y.com", "WEB")

      for {
        pair <- (exec(interpreter, booking1), exec(interpreter, booking2)).parTupled
        errs  = List(pair._1.errors.nonEmpty, pair._2.errors.nonEmpty).count(identity)

        rows <- TestSQL.listBookings(homeId).transact(xa).run(AuthInfo.Empty)

        _   <- TestSQL.drainOutboxOnce.transact(xa).run(AuthInfo.Empty)
        cnt <- TestSQL.countConflicts(homeId).transact(xa).run(AuthInfo.Empty)

      } yield expectTrue(errs == 1, s"Expected one failure; got r1=${pair._1.errors} r2=${pair._2.errors}") &&
        expectTrue(rows.size == 1, s"Expected one booking row, got: ${rows.size}") &&
        expectTrue(cnt == 1, s"Expected = 1 conflict row, got: $cnt")
  }
}

object TestSQL {

  import doobie.*
  import doobie.free.connection.ConnectionIO
  import doobie.implicits.*
  import doobie.postgres.implicits.*

  val drainOutboxOnce: doobie.ConnectionIO[Int] =
    sql"""
    WITH to_ins AS (
      SELECT
        (payload->>'correlationId')::uuid               AS correlation_id,
        (payload->'createBooking'->>'homeId')::uuid     AS home_id,
        (payload->'createBooking'->>'fromDate')::date   AS from_date,
        (payload->'createBooking'->>'toDate')::date     AS to_date,
        (payload->'createBooking'->>'guestEmail')::text AS guest_email,
        (payload->'createBooking'->>'source')::text     AS source,
        (payload->'suggested')::jsonb                   AS suggested,
        COALESCE((payload->>'occurredAt')::timestamptz, NOW()) AS occurred_at,
        event_id
      FROM booking.booking_conflict_outbox
      WHERE published_at IS NULL
    ),
    ins AS (
      INSERT INTO booking.booking_conflicts
        (correlation_id, home_id, from_date, to_date, guest_email, source, suggested, occurred_at)
      SELECT correlation_id, home_id, from_date, to_date, guest_email, source, suggested, occurred_at
      FROM to_ins
      ON CONFLICT (correlation_id) DO NOTHING
    )
    UPDATE booking.booking_conflict_outbox b
       SET published_at = NOW()
      FROM to_ins t
     WHERE b.event_id = t.event_id
    """.update.run

  def countConflicts(homeId: UUID): ConnectionIO[Int] =
    sql"select count(*) from booking.booking_conflicts where home_id = $homeId".query[Int].unique

  def listBookings(homeId: UUID): ConnectionIO[List[(UUID, LocalDate, LocalDate)]] =
    sql"""
      select id, from_date, to_date
      from booking.bookings
      where home_id = $homeId
      order by from_date
    """.query[(UUID, LocalDate, LocalDate)].to[List]

}
