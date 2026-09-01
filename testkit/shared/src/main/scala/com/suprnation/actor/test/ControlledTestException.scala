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

import scala.concurrent.duration.FiniteDuration

/** Signals a non-success outcome of a [[ControlledTestKit]] run that does not originate from the
  * test logic itself (i.e. cancellation or exhaustion of the simulated time window).
  */
sealed abstract class ControlledTestException(message: String) extends RuntimeException(message)

object ControlledTestException {

  /** The simulated test was canceled before completing. */
  case object Canceled extends ControlledTestException("The test was canceled.")

  /** The simulated test did not finish within the allotted time window. */
  final case class TimedOut(window: FiniteDuration)
      extends ControlledTestException(
        s"The test did not finish within the simulated time window ($window)."
      )
}
