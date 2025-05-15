package com.suprnation.benchmark

import cats.effect.unsafe.IORuntime

import org.openjdk.jmh.annotations._
import com.suprnation.actor.ActorSystem
import cats.effect.IO
import cats.effect.IO.consoleForIO

trait ActorJmhSupport[Env] {

  private var system: ActorSystem[IO] = _
  private var release: IO[Unit] = _
  private var env: Env | Null = _

  def init: ActorSystem[IO] => IO[Env]

  @Setup
  def setup(): Unit = {
    val (s, r) = ActorSystem("Benchmark").allocated.unsafeRunSync()(IORuntime.global)
    system = s
    release = r
    env = init(system).unsafeRunSync()(IORuntime.global)
  }

  @TearDown
  def teardown(): Unit = {
    release.unsafeRunSync()(IORuntime.global)
    system = null
    env = null
  }

  def bench[A](f: (ActorSystem[IO], Env) => IO[A]): Unit =
    f(system, env.nn).unsafeRunSync()(IORuntime.global)
}
