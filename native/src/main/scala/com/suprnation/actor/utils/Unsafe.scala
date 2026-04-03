package com.suprnation.actor.utils

import com.suprnation.actor.{ActorContext, ReplyingActor, ReplyingActorRef}

object Unsafe {

  /** Set the actor context reflectively, we do this to avoid a ref and have a double map on the object Note that the actor is update via the ref and we are guaranteed that it will be only accessed once due to the take.
    */

  def setActorContext[F[+_], Request, Response](
      actor: ReplyingActor[F, Request, Response],
      context: ActorContext[F, Request, Response]
  ): ReplyingActor[F, Request, Response] = {
    actor.context = context
    actor
  }

  /** Set the actor self
    *
    * @param actor
    *   the actor
    * @param self
    *   the self address
    */
  def setActorSelf[F[+_], Request, Response](
      actor: ReplyingActor[F, Request, Response],
      self: ReplyingActorRef[F, Request, Response]
  ): ReplyingActor[F, Request, Response] = {
    actor.self = self
    actor
  }
}
