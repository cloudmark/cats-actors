package com.suprnation.actor.ask

import com.suprnation.actor.Actor.{Actor, ReplyingReceive}
import com.suprnation.actor.{ActorSystem, ReplyingActor}

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.IO
import org.scalatest.Assertions

class AskSpecCatsEffect extends AsyncFlatSpec with AsyncIOSpec with Matchers with Assertions {
  import AskSpec._

  "ask pattern" should "return correct value" in {
    ActorSystem[IO]("test").use { sys =>
      for {
        actor <- sys.replyingActorOf(AskSpec.askReceiver)
        result <- actor ? Hi(123)
      } yield result shouldBe 123
    }
  }

  it should "catch errors" in {
    recoverToSucceededIf[Error] {
      ActorSystem[IO]("AskSpec")
        .use { system =>
          for {
            actor <- system.replyingActorOf(askReceiver)

            response <- actor ? Error("oops")
          } yield ()
        }
    }

  }
}
