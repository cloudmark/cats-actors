package com.suprnation.actor.utils

object IdGen {
  lazy val r = new scala.util.Random()

  def newId(): String = f"${r.nextLong()}%016x-${r.nextLong()}%016x"

}
