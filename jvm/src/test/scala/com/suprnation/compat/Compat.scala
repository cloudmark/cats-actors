package com.suprnation.compat

import scala.concurrent.duration.FiniteDuration
import cats.effect.IO

object Compat {
  inline def sleep(finiteDelay: FiniteDuration): IO[Unit] =
    IO.sleep(finiteDelay)
}
