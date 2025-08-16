package com.booking.auth

import cats.MonadThrow
import cats.data.Kleisli
import cats.data.OptionT
import cats.syntax.all.*
import com.booking.core.{AuthContext, AuthInfo}
import org.http4s.HttpRoutes
import org.http4s.Request

object AuthMiddleware {

  import cats.mtl.syntax.local.*
  import cats.syntax.all.*
  import org.typelevel.ci.*

  private val TokenHeader = ci"X-Token"

  def apply[F[_]: MonadThrow: AuthContext](
      routes: HttpRoutes[F]
  ): HttpRoutes[F] =
    Kleisli { (req: Request[F]) =>
      req.headers.get(TokenHeader) match {
        case Some(token) =>
          routes.run(req).scope(AuthInfo.Token(token.head.value): AuthInfo)
        case None        =>
          OptionT.liftF(
            MonadThrow[F].raiseError(new Exception("Unauthenticated"))
          )
      }
    }
}
