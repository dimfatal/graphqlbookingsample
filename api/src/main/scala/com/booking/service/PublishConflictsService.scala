package com.booking.service

import cats.effect.Concurrent
import cats.implicits.*
import com.booking.core.domain.ConflictEventOutboxRow
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.postgres.implicits.*
import fs2.Stream
import fs2.kafka.{KafkaProducer, ProducerRecord, ProducerRecords}
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.typelevel.log4cats.Logger

trait PublishConflictsService[F[_]] {
  def publish(): F[Unit]
}

object PublishConflictsService {
  def apply[F[_]: Concurrent](
      xa: HikariTransactor[F],
      producer: KafkaProducer[F, String, Array[Byte]],
      topic: String
  )(using logger: Logger[F]): PublishConflictsService[F] =
    () =>
      PublishConflictServiceSql
        .conflictEventsOutboxUnPublishedLookup()
        .transact(xa)
        .evalMap { ev =>
          val key     = ev.correlationId.toString
          val bytes   = ev.payload.noSpaces.getBytes(StandardCharsets.UTF_8)
          val record  = ProducerRecord(topic, key, bytes)
          val records = ProducerRecords.one(record)
          producer.produce(records).flatten.attempt.flatMap {
            case Right(_) =>
              PublishConflictServiceSql.updateEventsOutbox(ev.eventId).transact(xa).void
            case Left(e)  =>
              logger.info(s"[Kafka publish service] - publish failed for ${ev.correlationId}: ${e.getMessage}")
          }
        }
        .compile
        .drain
}

object PublishConflictServiceSql {
  def conflictEventsOutboxUnPublishedLookup(): Stream[ConnectionIO, ConflictEventOutboxRow] =
    sql"""
        SELECT event_id, correlation_id, payload, created_at, published_at
        FROM booking.booking_conflict_outbox
        WHERE published_at IS NULL
        """
      .query[ConflictEventOutboxRow]
      .stream

  def updateEventsOutbox(id: UUID): ConnectionIO[Int] =
    sql"""
     UPDATE booking.booking_conflict_outbox
     SET published_at=now()
     WHERE event_id = $id
     """.update.run
}
