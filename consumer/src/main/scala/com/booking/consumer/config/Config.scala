package com.booking.consumer.config

import cats.effect.Async
import cats.syntax.all._
import ciris._

final case class ConsumerConfig(
    bootstrap: String,
    groupId: String,
    clientId: String,
    topic: String
)

final case class DbConfig(url: String, user: String, password: String, driver: String)
final case class AppConfig(consumerConfig: ConsumerConfig, db: DbConfig)
object AppConfigLoader:
  def load[F[_]: Async]: F[AppConfig] =
    (
      env("APP_DB_URL").default("jdbc:postgresql://localhost:5432/booking"),
      env("APP_DB_USER").default("user"),
      env("APP_DB_PASSWORD").default("1234"),
      env("APP_DB_DRIVER").default("org.postgresql.Driver"),
      env("APP_KAFKA_BOOTSTRAP").default("localhost:9092"),
      env("APP_KAFKA_CONSUMER_CLIENT_ID").default(s"conflict-recorder"),
//      env("APP_KAFKA_CONSUMER_GROUP_ID").default("booking-conflict-recorder-2"),
      env("APP_KAFKA_CONSUMER_GROUP_ID").default(s"replay-${java.util.UUID.randomUUID()}"),
      env("APP_KAFKA_CONFLICT_TOPIC").default("booking_conflicts")
    ).parMapN { (dbUrl, dbUser, dbPass, dbDriver, bootstrapServer, clientId, groupId, topic) =>
      AppConfig(
        consumerConfig = ConsumerConfig(bootstrapServer, groupId, clientId, topic),
        db = DbConfig(dbUrl, dbUser, dbPass, dbDriver),
      )
    }.load[F]
