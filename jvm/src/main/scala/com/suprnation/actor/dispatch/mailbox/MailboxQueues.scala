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

package com.suprnation.actor.dispatch.mailbox

import cats.effect.Async

import java.util.concurrent.LinkedTransferQueue
import scala.jdk.CollectionConverters._

object MailboxQueues {
  def create[F[_]: Async, A]: F[MailboxQueue[F, A]] =
    Async[F].delay {
      val queue = new LinkedTransferQueue[A]()
      new MailboxQueue[F, A] {
        def tryOffer(a: A): F[Boolean] = Async[F].delay(queue.offer(a))
        def tryTake: F[Option[A]]      = Async[F].delay(Option(queue.poll()))
        def take: F[A]                 = Async[F].blocking(queue.take())
        def size: F[Int]               = Async[F].delay(queue.size())
        def tryTakeN(max: Option[Int]): F[List[A]] =
          Async[F].delay {
            val buf = new java.util.ArrayList[A]()
            max.fold(queue.drainTo(buf))(queue.drainTo(buf, _))
            buf.asScala.toList
          }
      }
    }
}
