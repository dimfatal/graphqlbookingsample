package com.booking.resources

import cats.effect.{Async, Resource}
import com.booking.config.KafkaProducerConfig
import fs2.kafka.*

object KafkaProducers {
  def make[F[_]: Async](cfg: KafkaProducerConfig): Resource[F, KafkaProducer[F, String, Array[Byte]]] = {
    val settings =
      ProducerSettings[F, String, Array[Byte]]
        .withBootstrapServers(cfg.bootstrapServers)
        .withProperty("client.id", cfg.clientId)
        .withProperty("acks", if (cfg.acksAll) "all" else "1")
        .withProperty("enable.idempotence", cfg.enableIdempotence.toString)
        .withProperty("linger.ms", cfg.lingerMs.toString)
        .withProperty("batch.size", cfg.batchSizeBytes.toString)
        .withProperty("compression.type", cfg.compressionType)
        .withProperty("retries", cfg.retries.toString)
        .withProperty("max.in.flight.requests.per.connection", cfg.maxInFlightPerConn.toString)
        .withProperty("delivery.timeout.ms", cfg.deliveryTimeoutMs.toString)

    KafkaProducer.resource(settings)
  }
}