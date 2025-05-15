package com.suprnation.actor

import org.openjdk.jmh.annotations._
import cats.effect.implicits.*
import cats.implicits.*
import cats.effect.IO
import java.util.concurrent.TimeUnit
import com.suprnation.actor.Actor.ReplyingReceive
import com.suprnation.benchmark.ActorJmhSupport

sealed trait Input
case class Hi(response: Int) extends Input
case class Error(error: String) extends Exception(error) with Input

@State(Scope.Benchmark)
class ActorBench extends ActorJmhSupport[ReplyingActorRef[IO, Input, Int]] {

  lazy val askReceiver: ReplyingActor[IO, Input, Int] = new ReplyingActor[IO, Input, Int] {
    override def receive: ReplyingReceive[IO, Any, Int] = {
      case Hi(response)       => response.pure[IO]
      case err @ Error(error) => IO.raiseError(err)
    }
  }

  override def init: ActorSystem[IO] => IO[ReplyingActorRef[IO, Input, Int]] = { system =>
    for {
      actor <- system.replyingActorOf(askReceiver)
    } yield actor
  }

  @Benchmark
  @Warmup(iterations = 1, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Fork(0)
  def ask(): Unit = bench { (system, actor) =>
    for {
      _ <- actor ? Hi(1234)
    } yield ()
  }
}
