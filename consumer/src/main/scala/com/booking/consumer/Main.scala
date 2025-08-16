package com.booking.consumer

import cats.effect.*
import cats.implicits.*
import cats.syntax.all.*
import com.booking.consumer.config.{AppConfigLoader, ConsumerConfig}
import com.booking.core.domain.ConflictEventPayload
import com.booking.consumer.resources.DbTransactor
import com.booking.consumer.service.ConflictEventService
import doobie.*
import doobie.hikari.HikariTransactor
import fs2.Stream
import fs2.kafka.*
import fs2.kafka.Deserializer
import io.circe.*
import io.circe.parser.*
import java.nio.charset.StandardCharsets.UTF_8
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp.Simple {
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  //todo delete only for test purposes, reading from begging every restart
  private val replayFromBeginning: Boolean = true

  private def consumerSettings[F[_]: Async](config: ConsumerConfig): ConsumerSettings[F, String, Array[Byte]] =
    ConsumerSettings[F, String, Array[Byte]](
      keyDeserializer = Deserializer.string[F],
      valueDeserializer = Deserializer[F, Array[Byte]]
    )
      .withBootstrapServers(config.bootstrap)
      .withGroupId(config.groupId)
      .withClientId(config.clientId)
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withEnableAutoCommit(false)

  private def decodeEvent[F[_]: Async](bytes: Array[Byte]): F[ConflictEventPayload] =
    Async[F]
      .fromEither(parse(new String(bytes, UTF_8)).flatMap(_.as[ConflictEventPayload]))
      .adaptError(e => new RuntimeException(s"payload decode failed: ${e.getMessage}", e))

  private def consumerStream[F[_]: Async](
      config: ConsumerConfig
  )(using xa: HikariTransactor[F], logger: Logger[F]): Stream[F, Unit] =
    KafkaConsumer
      .stream(consumerSettings(config))
      .subscribeTo(config.topic)
      .flatMap { c =>
        val onAssignments: Stream[F, Unit] =
          c.partitionsMapStream.evalMap { pm =>
            val parts = pm.keySet.toList
            val log   = logger.info(s"[consumer] assigned partitions: ${parts.mkString(",")}")
            if (replayFromBeginning && parts.nonEmpty) log *> c.seekToBeginning(parts)
            else log
          }

        val processRecords: Stream[F, Unit] =
          c.records.evalMap { comm =>
            val rec   = comm.record
            val key   = rec.key
            val bytes = rec.value

            (for {
              ev <- decodeEvent[F](bytes)
              _  <- ConflictEventService[F](xa, logger).insert(ev).flatMap {
                      case 1 => Logger[F].info(s"stored conflict ${ev.correlationId} (key=$key)")
                      case 0 => Logger[F].info(s"duplicate (correlation_id) ${ev.correlationId}, skipping")
                    }
              _  <- comm.offset.commit
            } yield ())
              .handleErrorWith { e =>
                Logger[F].error(e)(s"failed processing key=$key, offset=${rec.offset}") *>
                  // choose: commit to skip bad message, or DO NOT commit to retry
                  comm.offset.commit
              }
          }

        onAssignments.concurrently(processRecords)
      }
      .handleErrorWith(e => Stream.eval(logger.error(e)("[consumer] stream failed, staying alive")) >> Stream.never)

  val run: IO[Unit] =
    AppConfigLoader
      .load[IO]
      .flatMap { implicit cfg =>
        DbTransactor.make[IO](cfg.db).use { implicit xa =>
          Logger[IO]
            .info(s"Starting consumer: topic=${cfg.consumerConfig.topic}, group=${cfg.consumerConfig.groupId}") *>
            consumerStream[IO](cfg.consumerConfig).compile.drain
        }
      }
}
