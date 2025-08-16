package com.booking.resources

import cats.effect.{Async, Resource}
import cats.effect.Sync
import com.booking.config.DbConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, LogEvent, LogHandler, ProcessingFailure, Success}
import org.typelevel.log4cats.Logger
import scala.concurrent.duration._

object DbTransactor {
  def make[F[_]: Async](cfg: DbConfig, poolSize: Int = 8)(using
      logger: Logger[F]
  ): Resource[F, HikariTransactor[F]] =
    for {
      ce         <- ExecutionContexts.fixedThreadPool[F](poolSize)
      transactor <- HikariTransactor.newHikariTransactor[F](
                      cfg.driver,
                      cfg.url,
                      cfg.user,
                      cfg.password,
                      ce,
                      logHandler = Some(doobieLogHandler[F](logger))
                    )
    } yield transactor

  private def doobieLogHandler[F[_]: Sync](logger: Logger[F]): LogHandler[F] =
    (logEvent: LogEvent) => Sync[F].delay(
      logEvent match {
        case Success(sql, args, _, exec, proc) =>
          logger.debug(
            s"""SQL OK
               |$sql
               |args=${args.allParams.mkString(", ")} exec=${exec.toMillis}ms proc=${proc.toMillis}ms""".stripMargin
          )

        case ProcessingFailure(sql, args, _, exec, proc, t) =>
          logger.error(t)(
            s"""SQL ROW PROCESSING FAILED
               |$sql
               |args=${args.allParams.mkString(", ")} exec=${exec.toMillis}ms proc=${proc.toMillis}ms""".stripMargin
          )

        case ExecFailure(sql, args, _, _, t) =>
          logger.error(t)(
            s"""SQL EXEC FAILED
               |$sql
               |args=${args.allParams.mkString(", ")}""".stripMargin
          )
      }
    )

}
