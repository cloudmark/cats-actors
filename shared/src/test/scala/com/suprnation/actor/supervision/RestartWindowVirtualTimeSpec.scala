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

package com.suprnation.actor.supervision

import cats.effect.testkit.TestControl
import cats.effect.{IO, Ref}
import cats.implicits._
import com.suprnation.actor.Actor.{Actor, Receive}
import com.suprnation.actor.ActorRef.ActorRef
import com.suprnation.actor.SupervisorStrategy.Restart
import com.suprnation.actor._
import com.suprnation.spec.CatsActorFlatSpec

import scala.concurrent.duration._
import scala.language.postfixOps

object RestartWindowVirtualTimeSpec {

  /** Fails on every message and records how many times it was restarted. */
  case class FailingChild(restarts: Ref[IO, Int]) extends Actor[IO, String] {
    override def preRestart(reason: Option[Throwable], message: Option[Any]): IO[Unit] =
      restarts.update(_ + 1)

    override def receive: Receive[IO, String] = { case _ =>
      IO.raiseError(new RuntimeException("boom"))
    }
  }

  /** Restarts its child at most twice within a 500ms window, so a window that never closes will
    * start stopping the child on the third failure.
    */
  case class Parent(restarts: Ref[IO, Int]) extends Actor[IO, String] {
    val child: Ref[IO, Option[ActorRef[IO, String]]] = Ref.unsafe(None)

    override def supervisorStrategy: SupervisionStrategy[IO] =
      OneForOneStrategy[IO](maxNrOfRetries = 2, withinTimeRange = 500 millis) { case _ => Restart }

    override def preStart: IO[Unit] =
      context.actorOf[String](FailingChild(restarts), "failing-child").flatMap(c => child.set(c.some))

    override def receive: Receive[IO, String] = { case msg =>
      child.get.flatMap(_.traverse_(_ ! msg))
    }
  }
}

class RestartWindowVirtualTimeSpec extends CatsActorFlatSpec {
  import RestartWindowVirtualTimeSpec._

  // Regression test for the restart window in ChildStats. The window is measured with
  // Clock[F].monotonic rather than System.nanoTime, so it advances with virtual time under
  // cats.effect.testkit.TestControl. Failures spaced further apart than `withinTimeRange` must each
  // open a fresh window, so the child is restarted every time even though `maxNrOfRetries` is 2.
  // With System.nanoTime the virtual second between failures did not move the clock, the window
  // never closed, and the third restart was denied (the child was stopped instead).
  it should "reset the restart window in virtual time under TestControl" in {
    val program = for {
      restarts <- Ref.of[IO, Int](0)
      result <- ActorSystem[IO]("restart-window-virtual-time", (_: Any) => IO.unit).use { system =>
        for {
          parent <- system.actorOf[String](Parent(restarts), "parent")
          _ <- (parent ! "boom") >> IO.sleep(1 second)
          _ <- (parent ! "boom") >> IO.sleep(1 second)
          _ <- (parent ! "boom") >> IO.sleep(1 second)
          count <- restarts.get
        } yield count
      }
    } yield result

    TestControl.executeEmbed(program).map(_ should be(3))
  }
}
