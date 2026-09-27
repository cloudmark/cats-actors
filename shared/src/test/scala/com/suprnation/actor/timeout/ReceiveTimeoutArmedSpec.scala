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

package com.suprnation.actor.timeout

import cats.effect.{IO, Ref}
import com.suprnation.actor.dungeon.ReceiveTimeout
import com.suprnation.actor.dungeon.ReceiveTimeout.ReceiveTimeoutContext
import com.suprnation.spec.CatsActorFlatSpec

import scala.concurrent.duration._

class ReceiveTimeoutArmedSpec extends CatsActorFlatSpec {

  it should "report a receive timeout as armed only between set and cancel" in {
    for {
      context <- Ref.of[IO, ReceiveTimeoutContext[String]](ReceiveTimeoutContext(None, None, None))
      receiveTimeout = new ReceiveTimeout[IO, String](context)
      beforeSet <- receiveTimeout.isArmed
      _ <- receiveTimeout.setReceiveTimeout(1.second, "timeout")
      afterSet <- receiveTimeout.isArmed
      _ <- receiveTimeout.cancelReceiveTimeout
      afterCancel <- receiveTimeout.isArmed
    } yield {
      beforeSet should be(false)
      afterSet should be(true)
      afterCancel should be(false)
    }
  }
}
