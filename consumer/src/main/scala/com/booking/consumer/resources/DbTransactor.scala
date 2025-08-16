package com.booking.consumer.resources

import cats.effect.{Async, Resource}
import com.booking.consumer.config.DbConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts

object DbTransactor {
  def make[F[_]: Async](cfg: DbConfig, poolSize: Int = 8): Resource[F, HikariTransactor[F]] =
    for {
      ce         <- ExecutionContexts.fixedThreadPool[F](poolSize)
      transactor <- HikariTransactor.newHikariTransactor[F](
                      cfg.driver,
                      cfg.url,
                      cfg.user,
                      cfg.password,
                      ce
                    )
    } yield transactor
}
