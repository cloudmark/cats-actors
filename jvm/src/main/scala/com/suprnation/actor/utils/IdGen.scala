package com.suprnation.actor.utils
import java.util.UUID

object IdGen {
  def newId(): String = UUID.randomUUID().toString
}
