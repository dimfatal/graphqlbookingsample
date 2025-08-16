package com.booking.config

import cats.effect.Async
import cats.syntax.all._
import ciris._

final case class HttpConfig(host: String, port: Int)
final case class DbConfig(url: String, user: String, password: String, driver: String)
final case class KafkaConfig(bootstrapServers: String, conflictTopic: String) {
  def defaultProducer(clientId: String) =
    KafkaProducerConfig(bootstrapServers, clientId, conflictTopic)
}
final case class KafkaProducerConfig(
    bootstrapServers: String,
    clientId: String,
    conflictTopic: String,
    acksAll: Boolean = true,
    enableIdempotence: Boolean = true,
    lingerMs: Int = 5,
    batchSizeBytes: Int = 32768,
    compressionType: String = "lz4",
    retries: Int = 10,
    maxInFlightPerConn: Int = 5,
    deliveryTimeoutMs: Int = 120000
)

final case class AppConfig(http: HttpConfig, db: DbConfig, kafka: KafkaConfig)

object AppConfigLoader:
  def load[F[_]: Async]: F[AppConfig] =
    (
      env("APP_HTTP_HOST").default("0.0.0.0"),
      env("APP_HTTP_PORT").as[Int].default(8090),
      env("APP_DB_URL").default("jdbc:postgresql://localhost:5432/booking"),
      env("APP_DB_USER").default("user"),
      env("APP_DB_PASSWORD").default("1234"),
      env("APP_DB_DRIVER").default("org.postgresql.Driver"),
      env("APP_KAFKA_BOOTSTRAP").default("localhost:9092"),
      env("APP_KAFKA_CONFLICT_TOPIC").default("booking_conflicts")
    ).parMapN { (hHost, hPort, dbUrl, dbUser, dbPass, dbDriver, kBootstrap, kTopic) =>
      AppConfig(
        http = HttpConfig(hHost, hPort),
        db = DbConfig(dbUrl, dbUser, dbPass, dbDriver),
        kafka = KafkaConfig(kBootstrap, kTopic)
      )
    }.load[F]
