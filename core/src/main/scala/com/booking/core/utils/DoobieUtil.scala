package com.booking.core.utils

import cats.free.Free
import doobie.ConnectionIO
import java.sql.Savepoint
import doobie.free.connection.ConnectionOp as FC

object DoobieUtil {
  private def lift[A](op: FC[A]): ConnectionIO[A] =
    Free.liftF(op)

  def savepoint(): ConnectionIO[Savepoint] =
    lift(FC.Raw(_.setSavepoint()))

  def savepoint(name: String): ConnectionIO[Savepoint] =
    lift(FC.Raw(_.setSavepoint(name)))

  def rollback(sp: Savepoint): ConnectionIO[Unit] =
    lift(FC.Raw(_.rollback(sp)))

  def release(sp: Savepoint): ConnectionIO[Unit] =
    lift(FC.ReleaseSavepoint(sp))
}
