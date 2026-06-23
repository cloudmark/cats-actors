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

package com.suprnation.actor.test

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, Ref}
import com.suprnation.actor.Actor.{Actor, Receive}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.duration._

class ControlledTestKitSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with ControlledTestKit {

  "ControlledTestKit" should {
    "advance simulated time so delayed scheduler work fires (the README example)" in
      withActorSystemIO { system =>
        for {
          fired <- Ref[IO].of(false)
          _ <- system.actorOf(
            new Actor[IO, String] {
              override def preStart: IO[Unit] =
                context.system.scheduler.scheduleOnce_(5.seconds)(fired.set(true)).void

              override def receive: Receive[IO, String] = { case _ => IO.unit }
            },
            "scheduler-actor"
          )
          // Written as a 6s wait, but ControlledTestKit advances simulated
          // time, so the test still completes instantly.
          _ <- IO.sleep(6.seconds)
          result <- fired.get
        } yield result shouldBe true
      }

    "not observe the scheduled work before its delay elapses" in
      withActorSystemIO { system =>
        for {
          fired <- Ref[IO].of(false)
          _ <- system.actorOf(
            new Actor[IO, String] {
              override def preStart: IO[Unit] =
                context.system.scheduler.scheduleOnce_(5.seconds)(fired.set(true)).void

              override def receive: Receive[IO, String] = { case _ => IO.unit }
            },
            "scheduler-actor"
          )
          _ <- IO.sleep(4.seconds)
          result <- fired.get
        } yield result shouldBe false
      }

    "propagate a failure raised by the test logic" in {
      val boom = new RuntimeException("boom")
      withActorSystemIO[Unit](_ => IO.raiseError(boom)).attempt
        .asserting(_ shouldBe Left(boom))
    }
  }
}
