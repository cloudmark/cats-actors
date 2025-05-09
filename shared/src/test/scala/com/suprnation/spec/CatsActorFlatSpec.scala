package com.suprnation.spec

import cats.effect.unsafe.IORuntime
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext

trait CatsActorFlatSpec extends AsyncFlatSpec with AsyncIOSpec with Matchers {
  override implicit lazy val executionContext: scala.concurrent.ExecutionContext =
    IORuntime.global.compute
}
