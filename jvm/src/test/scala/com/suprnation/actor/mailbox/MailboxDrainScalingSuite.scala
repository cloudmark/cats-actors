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

package com.suprnation.actor.mailbox

import cats.effect.{Deferred, IO, Ref}
import cats.syntax.all._
import com.suprnation.actor.dispatch.SystemMessage
import com.suprnation.actor.dispatch.mailbox.Mailboxes
import com.suprnation.spec.CatsActorFlatSpec

import scala.concurrent.duration._

/** Asserts on the per-message cost at two queue depths rather than on absolute time, so the result
  * does not depend on the speed of the machine. An O(n) step per message makes the deep drain cost
  * up to about eight times more per message than the shallow one.
  */
class MailboxDrainScalingSuite extends CatsActorFlatSpec {

  private val shallowDepth: Int = 4000
  private val deepDepth: Int = 32000
  private val maxCostRatio: Double = 2.5

  private def timeDrain(depth: Int): IO[(FiniteDuration, Int)] =
    for {
      processed <- Ref.of[IO, Int](0)
      drained <- Deferred[IO, Unit]
      mailbox <- Mailboxes.createMailbox[IO, SystemMessage[IO], Int]("drain-scaling")
      _ <- (1 to depth).toList.traverse_(mailbox.enqueue)
      start <- IO.monotonic
      fiber <- mailbox
        .processMailbox((_: SystemMessage[IO]) => IO.unit)((_: Int) =>
          processed.updateAndGet(_ + 1).flatMap(n => drained.complete(()).void.whenA(n == depth))
        )
        .foreverM
        .start
      _ <- drained.get
      elapsed <- IO.monotonic.map(_ - start)
      _ <- fiber.cancel
      _ <- mailbox.close
      count <- processed.get
    } yield (elapsed, count)

  // Fastest of three, so that a run which pays for JIT compilation or a GC does not count.
  private def bestDrain(depth: Int): IO[(FiniteDuration, Int)] =
    List.fill(3)(depth).traverse(timeDrain).map(_.minBy(_._1))

  it should "drain a deep mailbox at the same per-message cost as a shallow one" in {
    for {
      _ <- timeDrain(deepDepth) // warm-up
      shallow <- bestDrain(shallowDepth)
      deep <- bestDrain(deepDepth)
    } yield {
      val shallowPerMessage = shallow._1.toNanos.toDouble / shallowDepth
      val deepPerMessage = deep._1.toNanos.toDouble / deepDepth
      val ratio = deepPerMessage / shallowPerMessage
      info(f"n=$shallowDepth: ${shallow._1.toMillis} ms, ${shallowPerMessage / 1000}%.2f us/msg")
      info(f"n=$deepDepth: ${deep._1.toMillis} ms, ${deepPerMessage / 1000}%.2f us/msg")
      info(f"per-message cost ratio $ratio%.2f")

      shallow._2 should be(shallowDepth)
      deep._2 should be(deepDepth)
      ratio should be < maxCostRatio
    }
  }
}
