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

package com.suprnation.typelevel.actors.syntax

import cats.Parallel
import cats.effect.Sync
import cats.implicits._

trait BooleanSyntax {

  implicit class BooleanFOps[F[_]: Sync](a: F[Boolean]) {

    @inline final def &&&(b: => F[Boolean]): F[Boolean] =
      a.flatMap {
        case true  => b // Only evaluate b if a is true
        case false => false.pure[F] // Short-circuit if a is false
      }

    @inline final def |||(b: => F[Boolean]): F[Boolean] =
      a.flatMap {
        case true  => true.pure[F] // Short-circuit if a is true
        case false => b // Only evaluate b if a is false
      }

  }

  implicit class BooleanParFOps[F[_]: Parallel](a: F[Boolean]) {

    @inline final def &&&>(b: => F[Boolean]): F[Boolean] = (a, b).parMapN((a, b) => a && b)

  }

}
