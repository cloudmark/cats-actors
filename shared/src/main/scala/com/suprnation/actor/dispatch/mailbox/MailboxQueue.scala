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

/** Platform-specific unbounded queue used internally by mailboxes.
  * The companion object providing `create` is defined in each platform-specific source tree.
  */
trait MailboxQueue[F[_], A] {
  def tryOffer(a: A): F[Boolean]
  def tryTake: F[Option[A]]
  def take: F[A]
  def size: F[Int]
  def tryTakeN(max: Option[Int]): F[List[A]]
}
