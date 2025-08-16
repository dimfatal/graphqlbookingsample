package com.booking.graphql

import cats.Monoid
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.*
import com.booking.core.domain.CreateBooking
import com.booking.core.graphql.Types.MutationCreateBookingArgs
import com.booking.graphql.RequestError.InputValidationError
import doobie.implicits.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.util.Try

object Validation {

  private def dateFormatValidation(date: String, argName: String): Validated[InputValidationError, LocalDate] =
    Validated
      .fromTry(Try(LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
      .bimap(
        e =>
          InputValidationError(
            s"${e.getMessage} -> Wrong date format, yyyy-MM-dd required for $argName."
          ),
        identity
      )

  private def dateStartAndEndCorrect(start: LocalDate, end: LocalDate): Validated[InputValidationError, Unit] =
    if (end.compareTo(start) < 0) Invalid(InputValidationError(s"start date - $start is less when end date - $end"))
    else Valid(())

  private def uuidValidation(uuid: String, argName: String): Validated[InputValidationError, UUID] =
    Validated
      .fromTry(Try(UUID.fromString(uuid)))
      .bimap(
        e =>
          InputValidationError(
            s"${e.getMessage} -> Wrong uuid format - $argName."
          ),
        identity
      )

  private def emailValidation(email: String, argName: String): Validated[InputValidationError, String] =
    if (email.matches("^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$")) Valid(email)
    else Invalid(InputValidationError(s"Invalid email format: $email, for $argName"))

  implicit class ValidateCreateCase(args: MutationCreateBookingArgs) {
    def validate: Validated[InputValidationError, CreateBooking] =
      (
        dateFormatValidation(args.fromDate, "fromDate"),
        dateFormatValidation(args.toDate, "toDate"),
        uuidValidation(args.homeId, "homeId"),
        emailValidation(args.guestEmail, "guestEmail")
      )
        .mapN { case x => x }
        .andThen((fromDate, toDate, homeId, guestEmail) =>
          dateStartAndEndCorrect(fromDate, toDate).map(_ =>
            CreateBooking(
              homeId,
              fromDate,
              toDate,
              guestEmail,
              args.source
            )
          )
        )
  }
}

sealed abstract class RequestError extends Throwable
object RequestError {
  case class PostgresError(message: String)        extends RequestError
  case class InputValidationError(message: String) extends RequestError
  object InputValidationError {
    implicit val validationMonoid: Monoid[InputValidationError] =
      Monoid.instance[InputValidationError](
        InputValidationError(""),
        (errorA, errorB) =>
          InputValidationError(
            errorA.message + " | " + errorB.message
          )
      )
  }
}
