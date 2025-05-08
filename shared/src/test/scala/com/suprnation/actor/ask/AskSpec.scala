/*
 * Copyright 2024 SuprNation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.suprnation.actor.ask

import cats.effect.{Deferred, IO}
import cats.effect.implicits._
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import com.suprnation.actor.Actor.{Actor, ReplyingReceive}
import com.suprnation.actor.{ActorSystem, ReplyingActor}
import com.suprnation.compat.Compat
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Assertions
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

object AskSpec {
  sealed trait Input
  case class Hi(response: Int) extends Input
  case class Error(error: String) extends Exception(error) with Input

  lazy val askReceiver: ReplyingActor[IO, Input, Int] = new ReplyingActor[IO, Input, Int] {
    override def receive: ReplyingReceive[IO, Any, Int] = {
      case Hi(response)       => response.pure[IO]
      case err @ Error(error) => IO.raiseError(err)
    }
  }
}

import cats.effect.unsafe.IORuntime

class AskSpec extends AsyncFlatSpec with AsyncIOSpec with Matchers with Assertions {

  import AskSpec._

  // override implicit def executionContext: scala.concurrent.ExecutionContext =
  //   IORuntime.global.compute

  it should "return the correct response" in {
    ActorSystem[IO]("AskSpec")
      .use { system =>
        for {
          actor <- system.replyingActorOf(askReceiver)

          response <- actor ? Hi(4567)
        } yield response shouldBe 4567
      }

  }

  it should "catch errors" in {
    recoverToSucceededIf[Error] {
      ActorSystem[IO]("AskSpec")
        .use { system =>
          for {
            actor <- system.replyingActorOf(askReceiver)

            response <- actor ? Error("oops")
          } yield ()
        }
    }

  }

  it should "receive the response even if the actor system is terminated" in {

    // implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 2.seconds)

    val childResponseDelay = 500.millis
    val systemTerminateAfter = 200.millis

    case class AskChildActor() extends ReplyingActor[IO, String, String] {
      override def receive: ReplyingReceive[IO, String, String] = { case "req" =>
        println("Child actor received request")
        Compat.sleep(childResponseDelay).as("res")
      }
    }

    case class AskParentActor(responseDeferred: Deferred[IO, String]) extends Actor[IO, String] {
      override def receive: ReplyingReceive[IO, String, Any] = { case msg =>
        context
          .replyingActorOf(AskChildActor())
          .flatMap(_ ? msg)
          // .flatMap(res => responseDeferred.complete(res))
          .flatMap { res =>
            println("Compling the response!")
            responseDeferred.complete(res)
          }
      }
    }

    ActorSystem[IO]()
      .use { actorSystem =>
        for {
          responseDeferred <- IO.deferred[String]
          parentActor <- actorSystem.actorOf(AskParentActor(responseDeferred))
          _ <- parentActor ! "req"
          _ <- IO.sleep(systemTerminateAfter)
          response <- responseDeferred.get
        } yield responseDeferred
      }
      .flatMap(_.get)
      .map(_ shouldBe "res")
  }

}
