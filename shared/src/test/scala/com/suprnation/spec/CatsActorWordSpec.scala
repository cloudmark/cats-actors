package com.suprnation.spec

import cats.effect.unsafe.IORuntime
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext

trait CatsActorWordSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {
  override implicit lazy val executionContext: scala.concurrent.ExecutionContext =
    IORuntime.global.compute
}
