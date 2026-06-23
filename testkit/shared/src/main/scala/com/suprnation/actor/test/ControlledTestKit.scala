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

import cats.effect.IO
import cats.effect.kernel.Outcome
import cats.effect.testkit.TestControl
import com.suprnation.actor.ActorSystem

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

trait ControlledTestKit extends TestKit {

  /** Creates an actor system and runs the provided test code within a simulated time environment.
    *
    * @tparam T             The return type of the test logic.
    * @param testCode       The test logic to execute using the provisioned actor system.
    * @param testTimeWindow The maximum simulated time allowed for the test execution (defaults to 90 seconds).
    * @return               An `IO` containing the result of the test, which fails if the test errors, cancels, or times out.
    */
  def withActorSystemIO[T](
      testCode: ActorSystem[IO] => IO[T],
      testTimeWindow: FiniteDuration = FiniteDuration(90, TimeUnit.SECONDS)
  ): IO[T] =
    for {
      testControl <- TestControl.execute(ActorSystem[IO](this.getClass.getName).use(testCode))
      _ <- testControl.tickFor(testTimeWindow)
      outcome <- testControl.results
      result <- outcome match {
        case Some(Outcome.Succeeded(value)) => IO.pure(value)
        case Some(Outcome.Errored(e))       => IO.raiseError(e)
        case Some(Outcome.Canceled())       => IO.raiseError(ControlledTestException.Canceled)
        case None => IO.raiseError(ControlledTestException.TimedOut(testTimeWindow))
      }
    } yield result
}
