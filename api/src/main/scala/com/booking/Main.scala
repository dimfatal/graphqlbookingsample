package com.booking

import caliban.interop.cats.CatsInterop
import cats.data.Kleisli
import cats.effect.implicits.effectResourceOps
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.*
import com.booking.config.{AppConfig, AppConfigLoader}
import com.booking.core.AuthInfo
import com.booking.resources.graphql.{Controller, GraphQlBootstrap}
import com.booking.resources.{DbTransactor, FlywayMigration, KafkaProducers}
import com.booking.service.PublishConflictsService
import org.http4s.server.Server
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import zio.ZEnvironment

object Main extends IOApp {

  type RIO[A] = Kleisli[IO, AuthInfo, A]

  given logger: Logger[IO]    = Slf4jLogger.getLoggerFromName[IO]("booking-writer")
  given zio.Runtime[AuthInfo] = zio.Runtime.default.withEnvironment(ZEnvironment(AuthInfo.Empty))

  def run(args: List[String]): IO[ExitCode] =
    AppConfigLoader
      .load[IO]
      .flatMap { implicit cfg =>
        FlywayMigration
          .mkFlywayMigration[IO](cfg.db)
          .flatMap(_ => logger.info("Migrations complete, starting program…"))
          .flatMap(_ =>
            program(cfg).useForever
              .run(AuthInfo.Empty)
              .as(ExitCode.Success)
          )
      }

  def program(config: AppConfig)(using zio.Runtime[AuthInfo]): Resource[RIO, Server] = Dispatcher
    .parallel[RIO]
    .flatMap { case dispatcher @ (given Dispatcher[RIO]) =>
      given interop: CatsInterop.Contextual[RIO, AuthInfo] = CatsInterop.contextual(dispatcher)
      (for {
        xa                      <- DbTransactor.make[RIO](config.db)
        kafkaProducer           <- KafkaProducers.make[RIO](config.kafka.defaultProducer(s"booking-writer-local-1"))
        publishConflictsService <-
          PublishConflictsService[RIO](xa, kafkaProducer, config.kafka.conflictTopic).pure[RIO].toResource
        interpreter             <- GraphQlBootstrap.mkGraphQL[RIO](xa, publishConflictsService).toResource
      } yield (publishConflictsService, interpreter))
        .flatMap((publishConflictsService, inter) =>
          publishConflictsService
            .publish()
            .toResource
            .flatMap(_ => Controller.bind[RIO](inter))
        )
    }
}
