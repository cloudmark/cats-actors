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

package com.suprnation.actor.lifecycle

import cats.effect.{IO, Ref}
import com.suprnation.actor.Actor.{Actor, Receive}
import com.suprnation.actor._
import com.suprnation.spec.CatsActorFlatSpec
import com.suprnation.typelevel.actors.syntax.ActorSystemDebugOps

class ActorLifecycleSuite extends CatsActorFlatSpec {
  trait ActorLifecycleRequests
  case object GetRef extends ActorLifecycleRequests
  case object Stop extends ActorLifecycleRequests
  case object Hello extends ActorLifecycleRequests

  it should "call the preStart hook before the actor start" in {
    ActorSystem[IO]("Actor Test", (_: Any) => IO.unit).use { actorSystem =>
      for {
        ref <- Ref.of[IO, Int](0)
        actor <- actorSystem.actorOf(
          new Actor[IO, ActorLifecycleRequests] {
            override def receive: Receive[IO, ActorLifecycleRequests] = { case _ => ref.get }
            override def preStart: IO[Unit] = ref.set(1)
          }
        )
        result1 <- actor ? GetRef // send a message to the actor to confirm that they received it.
      } yield result1 should be(1)
    }
  }

  it should "call the postHook when the actor dies" in {
    ActorSystem[IO]("Actor Test", (_: Any) => IO.unit).use { actorSystem =>
      for {
        ref <- Ref.of[IO, Int](0)
        actor <- actorSystem.actorOf[ActorLifecycleRequests](
          new Actor[IO, ActorLifecycleRequests] {
            override def receive: Receive[IO, ActorLifecycleRequests] = { case Stop =>
              context.stop(self)
            }
            override def postStop: IO[Unit] = ref.set(1) >> actorSystem.terminate(None).void
          }
        )
        _ <- actor ! Stop
        _ <- actorSystem.waitForTermination
        result1 <- ref.get
      } yield result1 should be(1)
    }
  }

  it should "allow an actor to be recreated once it is killed" in {

    def createActor: Actor[IO, ActorLifecycleRequests] = new Actor[IO, ActorLifecycleRequests] {
      override def receive: Receive[IO, ActorLifecycleRequests] = {
        case Stop  => context.stop(context.self).as(true)
        case Hello => IO.unit
      }
    }

    ActorSystem[IO]("Actor Test", (_: Any) => IO.unit).use { actorSystem =>
      for {
        actor <- actorSystem.actorOf(createActor, "testing")
        _ <- actor ? Hello
        _ <- actor ? Stop
        _ <- actorSystem.waitForIdle()

        actor <- actorSystem.actorOf(createActor, "testing")
        _ <- actor ? Hello
      } yield succeed
    }
  }

}
