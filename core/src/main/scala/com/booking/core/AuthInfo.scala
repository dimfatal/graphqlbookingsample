package com.booking.core

import cats.MonadThrow
import cats.mtl.Local
import cats.syntax.all.*

sealed trait AuthInfo
object AuthInfo    {
  case object Empty               extends AuthInfo
  case class Token(token: String) extends AuthInfo
}

object AuthContext {
  def apply[F[_]](using L: Local[F, AuthInfo]): Local[F, AuthInfo]               = L
  def token[F[_]: MonadThrow](implicit L: Local[F, AuthInfo]): F[AuthInfo.Token] =
    L.ask[AuthInfo]
      .flatMap:
        case t: AuthInfo.Token => MonadThrow[F].pure(t)
        case AuthInfo.Empty    =>
          MonadThrow[F].raiseError(new Exception("Unauthenticated"))
}

type AuthContext[F[_]] = Local[F, AuthInfo]
