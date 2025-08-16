package com.booking.graphql

import caliban.CalibanError.ExecutionError
import caliban.Value
import caliban.schema.{ArgBuilder, Schema}
import com.booking.core.graphql.ID
import com.booking.core.graphql.Types.{MutationCreateBookingArgs, QueryBookingsArgs}
import java.time.LocalDate
import scala.util.Try

object codec {
  implicit val id: ArgBuilder[ID]         = ArgBuilder.uuid.map(ID(_))
  implicit def idSchema[R]: Schema[R, ID] = Schema.uuidSchema.contramap(_.value)

  implicit def QueryBookingArgsSchema[R]: Schema[R, QueryBookingsArgs] = Schema.gen
  implicit val QueryBookingArgs: ArgBuilder[QueryBookingsArgs]         = ArgBuilder.gen

  implicit def MutationCreateBookingArgs[R]: Schema[R, MutationCreateBookingArgs] = Schema.gen
  implicit val MutationCreateArgs: ArgBuilder[MutationCreateBookingArgs]          = ArgBuilder.gen

  
  implicit val localDateArgBuilder: ArgBuilder[LocalDate] = {
    case Value.StringValue(value) =>
      Try(LocalDate.parse(value))
        .fold(ex => Left(ExecutionError(s"Can't parse $value into a LocalDate", innerThrowable = Some(ex))), Right(_))
    case other                    => Left(ExecutionError(s"Can't build a LocalDate from input $other"))
  }
}
