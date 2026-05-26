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

package com.suprnation.actor.dungeon

import cats.Applicative
import cats.effect.{Clock, Ref, Sync}
import cats.syntax.all._
import com.suprnation.actor.dungeon.ReceiveTimeout.ReceiveTimeoutContext

import scala.concurrent.duration.FiniteDuration

object ReceiveTimeout {
  case class ReceiveTimeoutContext[Request](
      receiveTimeout: Option[FiniteDuration],
      lastMessageTimestamp: Option[Long],
      message: Option[Request]
  )
}

class ReceiveTimeout[F[_]: Sync, Request](
    receiveTimeoutContextRef: Ref[F, ReceiveTimeout.ReceiveTimeoutContext[Request]]
) {

  // Use the cats-effect clock instead of System.currentTimeMillis so that receive-timeouts honour
  // virtual time under cats.effect.testkit.TestControl (deterministic, instant tests). We use
  // `monotonic` (not `realTime`): a timeout measures *elapsed* time, so it must be immune to
  // wall-clock jumps (NTP, manual clock changes). TestControl advances monotonic with virtual time.
  private def nowMillis: F[Long] = Clock[F].monotonic.map(_.toMillis)

  def setReceiveTimeout(timeout: FiniteDuration, onTimeout: => Request): F[Unit] =
    nowMillis.flatMap { now =>
      receiveTimeoutContextRef.set(
        ReceiveTimeoutContext[Request](
          Some(timeout),
          Some(now),
          Some(onTimeout)
        )
      )
    }

  def cancelReceiveTimeout: F[Unit] =
    receiveTimeoutContextRef.update(_.copy(receiveTimeout = None, message = None))

  def markLastMessageTimestamp: F[Unit] =
    nowMillis.flatMap { now =>
      receiveTimeoutContextRef.update { receiveTimeoutContext =>
        if (receiveTimeoutContext.receiveTimeout.isDefined) {
          receiveTimeoutContext.copy(lastMessageTimestamp = Some(now))
        } else {
          receiveTimeoutContext
        }
      }
    }

  def checkTimeout(action: Request => F[Any]): F[Any] =
    receiveTimeoutContextRef.get.flatMap {
      case ReceiveTimeoutContext(Some(timeout), Some(timestamp), Some(message)) =>
        nowMillis.flatMap { currentTime =>
          val timeoutTime: Long = timestamp + timeout.toMillis
          if (timeoutTime <= currentTime) {
            receiveTimeoutContextRef.set(
              ReceiveTimeout.ReceiveTimeoutContext(
                Some(timeout),
                Some(currentTime),
                Some(message)
              )
            ) >> action(message)
          } else {
            Applicative[F].pure(())
          }
        }
      case _ => Applicative[F].pure(())
    }
}
