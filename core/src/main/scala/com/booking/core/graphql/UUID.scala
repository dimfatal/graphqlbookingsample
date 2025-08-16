package com.booking.core.graphql

import caliban.schema.Schema

import java.util.UUID

sealed trait ID {
  def value: UUID
}

object ID {
  private case class Impl(value: UUID) extends ID
  def apply(id: UUID): ID = Impl(id)

}
