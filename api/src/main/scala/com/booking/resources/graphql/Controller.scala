package com.booking.resources.graphql

import caliban.{CalibanError, GraphQLInterpreter, Http4sAdapter}
import caliban.interop.cats.CatsInterop
import caliban.interop.tapir.HttpInterpreter
import cats.{Applicative, MonadThrow}
import cats.data.{Kleisli, OptionT}
import cats.effect.kernel.Async
import cats.effect.std.Console
import cats.mtl.Local
import cats.mtl.syntax.local.*
import cats.syntax.all.*
import com.booking.auth.AuthMiddleware
import com.booking.core.{AuthContext, AuthInfo}
import com.comcast.ip4s.*
import fs2.io.net.Network
import org.http4s.{HttpRoutes, Request}
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.{Router, Server}
import org.typelevel.ci.*

type Context[F[_]] = Local[F, AuthInfo]
object Context {
  def apply[F[_]](implicit ev: Context[F]): Context[F] = ev

  def token[F[_]: MonadThrow](using L: Context[F]): F[AuthInfo.Token] =
    L.ask[AuthInfo].flatMap {
      case t: AuthInfo.Token => MonadThrow[F].pure(t)
      case AuthInfo.Empty    =>
        MonadThrow[F].raiseError(new Exception("Unauthenticated"))
    }
}

object Middleware {
  def default[F[_]: MonadThrow](routes: HttpRoutes[F])(using
      Ctx: Context[F]
  ): HttpRoutes[F] =
    given AuthContext[F] = Ctx.imap(identity)(identity)

    AuthMiddleware(routes)
}

object Controller {
  def bind[F[_]: Async: AuthContext: Console: Network](
      interpreter: GraphQLInterpreter[AuthInfo, CalibanError],
      port: Port = port"8090"
  )(using interop: CatsInterop[F, AuthInfo]) =
    EmberServerBuilder
      .default[F]
      .withPort(port)
      .withHttpApp(
        Middleware.default(commands <+> queries(interpreter) <+> extras).orNotFound
      )
      .build

  private def commands[F[_]: Applicative] = HttpRoutes.empty

  private def queries[F[_]: Async: Console](
      interpreter: GraphQLInterpreter[AuthInfo, CalibanError]
  )(using CatsInterop[F, AuthInfo]) =
    import caliban.interop.cats.implicits.*
    val queryEndpoint = Root / "api" / "graphql"
    // transform GraphQLResponse type into HTTP Response type
    val inner         = Http4sAdapter
      .makeHttpServiceF(
        HttpInterpreter(interpreter)
      )
    HttpRoutes[F] {
      case req @ (GET -> `queryEndpoint`)  => inner.run(req)
      case req @ (POST -> `queryEndpoint`) => inner.run(req)
    }

  private def extras[F[_]: Applicative] = HttpRoutes.empty
}
