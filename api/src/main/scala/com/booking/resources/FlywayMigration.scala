package com.booking.resources

import cats.Applicative
import cats.effect.Async
import com.booking.config.DbConfig
import org.flywaydb.core.Flyway

object FlywayMigration {
  def mkFlywayMigration[F[_]: Async](cfg: DbConfig): F[Unit] =
    implicitly[Async[F]].pure(
      Flyway
        .configure(this.getClass.getClassLoader)
        .dataSource(cfg.url, cfg.user, cfg.password)
        .locations("classpath:db/migration")
        .schemas("booking")
        .createSchemas(true)
        .connectRetries(Int.MaxValue)
        .load()
        .migrate()
    )

}
