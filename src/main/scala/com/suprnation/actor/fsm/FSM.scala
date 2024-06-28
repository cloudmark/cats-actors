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

package com.suprnation.actor.fsm

import cats.Parallel
import cats.effect._
import cats.effect.std.Console
import cats.implicits._
import com.suprnation.actor.Actor.{Actor, ReplyingReceive}
import com.suprnation.actor.ActorRef.{ActorRef, NoSendActorRef}
import com.suprnation.actor.fsm.FSM.{Event, StopEvent}
import com.suprnation.actor.fsm.State.StateTimeoutWithSender
import com.suprnation.actor.{ActorLogging, ReplyingActor}
import com.suprnation.typelevel.actors.syntax.ActorRefSyntaxOps
import com.suprnation.typelevel.fsm.syntax._

import scala.annotation.unused
import scala.concurrent.duration.FiniteDuration

object FSM {

  type StateFunction[F[+_], S, D, Request, Response] =
    PartialFunction[(Any, StateManager[F, S, D, Request, Response]), F[
      State[S, D, Request, Response]
    ]]

  def apply[F[+_]: Parallel: Async: Temporal, S, D, Request, Response]
      : FSMBuilder[F, S, D, Request, Response] =
    new FSMBuilder[F, S, D, Request, Response]()

  /** All messages sent to the FSM will be wrapped inside an `Event`, which allows pattern matching to extract both state and data.
    */
  final case class Event[D, Request](event: Request, stateData: D)

  /** Case class representing the state of the FMS within the `onTermination` block
    */
  final case class StopEvent[S, D](reason: Reason, currentState: S, stateData: D)

  object NullFunction extends PartialFunction[Any, Nothing] {
    def isDefinedAt(o: Any) = false
    def apply(o: Any) = sys.error("undefined")
  }

  /** This extractor is just convenience for matching a (S, S) pair, including a reminder what the new state is.
    */
  object `->` {
    def unapply[S](in: (S, S)): Option[(S, S)] = Some(in)
  }

}

object FSMBuilder {

  def apply[F[+_]: Parallel: Async: Temporal, S, D, Request, Response]()
      : FSMBuilder[F, S, D, Request, Response] =
    new FSMBuilder[F, S, D, Request, Response](
      FSMConfig(
        receive =
          (_: Any, _: Option[NoSendActorRef[F]], _: State[S, D, Request, Response]) => Sync[F].unit,
        transition =
          (_: State[S, D, Request, Response], _: State[S, D, Request, Response]) => Sync[F].unit
      )
    )

}

object FSMConfig {

  def noDebug[F[+_], S, D, Request, Response]: FSMConfig[F, S, D, Request, Response] = FSMConfig(
    debug = false,
    (e: Any, sender: Option[NoSendActorRef[F]], s: State[S, D, Request, Response]) =>
      throw new Error("Not used just for compilation"),
    (oS: State[S, D, Request, Response], nS: State[S, D, Request, Response]) =>
      throw new Error("Not used just for compilation")
  )

  def withConsoleInformation[F[+_]: Console, S, D, Request, Response]
      : FSMConfig[F, S, D, Request, Response] = FSMConfig(
    debug = true,
    receive = (
        event: Any,
        sender: Option[NoSendActorRef[F]],
        currentState: State[S, D, Request, Response]
    ) =>
      Console[F].println(
        s"[FSM] Received [Event: $event] [Sender: $sender] [CurrentState: ${currentState.stateName}] [CurrentData: ${currentState.stateData}]"
      ),
    transition = (
        oldState: State[S, D, Request, Response],
        newState: State[S, D, Request, Response]
    ) =>
      Console[F].println(
        s"[FSM] Transition ([CurrentState: ${oldState.stateName}] -> [CurrentData: ${oldState.stateData}]) ~> ([NextState: ${newState.stateName}] -> [NextData: ${newState.stateData}])"
      )
  )

}

case class FSMConfig[F[+_], S, D, Request, Response](
    debug: Boolean = false,
    receive: (Any, Option[NoSendActorRef[F]], State[S, D, Request, Response]) => F[Unit],
    transition: (State[S, D, Request, Response], State[S, D, Request, Response]) => F[Unit]
)

// In this version we will separate the description from the execution.
case class FSMBuilder[F[+_]: Parallel: Async: Temporal, S, D, Request, Response](
    config: FSMConfig[F, S, D, Request, Response] = FSMConfig.noDebug[F, S, D, Request, Response],
    stateFunctions: Map[S, PartialFunction[
      (FSM.Event[D, Request], StateManager[F, S, D, Request, Response]),
      F[State[S, D, Request, Response]]
    ]] = Map.empty[S, PartialFunction[
      (FSM.Event[D, Request], StateManager[F, S, D, Request, Response]),
      F[State[S, D, Request, Response]]
    ]],
    stateTimeouts: Map[S, Option[(FiniteDuration, Request)]] =
      Map.empty[S, Option[(FiniteDuration, Request)]],
    transitionEvent: List[PartialFunction[(S, S), F[Unit]]] = Nil,
    terminateEvent: PartialFunction[StopEvent[S, D], F[Unit]] = FSM.NullFunction,
    onTerminationCallback: Option[Reason => F[Unit]] = Option.empty[Reason => F[Unit]]
) { builderSelf =>

  type StateFunction[R] =
    PartialFunction[(FSM.Event[D, R], StateManager[F, S, D, Request, Response]), F[
      State[S, D, Request, Response]
    ]]
  type Timeout = Option[(FiniteDuration, Request)]
  type TransitionHandler = PartialFunction[(S, S), F[Unit]]

  def when(stateName: S, stateTimeout: FiniteDuration, onTimeout: Request)(
      stateFunction: StateFunction[Request]
  ): FSMBuilder[F, S, D, Request, Response] =
    register(stateName, stateFunction, Some((stateTimeout, onTimeout)))

  private def register(
      name: S,
      function: StateFunction[Request],
      timeout: Timeout
  ): FSMBuilder[F, S, D, Request, Response] =
    if (stateFunctions contains name) {
      FSMBuilder[F, S, D, Request, Response](
        config,
        stateFunctions + (name -> stateFunctions(name).orElse(function)),
        stateTimeouts + (name -> timeout.orElse(stateTimeouts(name))),
        transitionEvent,
        terminateEvent,
        onTerminationCallback
      )
    } else {
      FSMBuilder[F, S, D, Request, Response](
        config,
        stateFunctions + (name -> function),
        stateTimeouts + (name -> timeout),
        transitionEvent,
        terminateEvent,
        onTerminationCallback
      )
    }

  def when(stateName: S, stateTimeout: Timeout = Option.empty[(FiniteDuration, Request)])(
      stateFunction: StateFunction[Request]
  ): FSMBuilder[F, S, D, Request, Response] =
    register(stateName, stateFunction, stateTimeout)

  final def onTransition(
      transitionHandler: TransitionHandler
  ): FSMBuilder[F, S, D, Request, Response] =
    FSMBuilder[F, S, D, Request, Response](
      config,
      stateFunctions,
      stateTimeouts,
      transitionEvent ++ List(transitionHandler),
      terminateEvent,
      onTerminationCallback
    )

  def withConfig(
      config: FSMConfig[F, S, D, Request, Response]
  ): FSMBuilder[F, S, D, Request, Response] =
    FSMBuilder[F, S, D, Request, Response](
      config,
      stateFunctions,
      stateTimeouts,
      transitionEvent,
      terminateEvent,
      onTerminationCallback
    )

  def onTermination(onTerminated: Reason => F[Unit]): FSMBuilder[F, S, D, Request, Response] =
    FSMBuilder[F, S, D, Request, Response](
      config,
      stateFunctions,
      stateTimeouts,
      transitionEvent,
      terminateEvent,
      onTerminationCallback = Option(onTerminated)
    )

  final def startWith(stateName: S, stateData: D, timeout: Timeout = None): PreCompiledFSM = {
    new PreCompiledFSM { preCompiledSelf =>
      override def initialize: F[ReplyingActor[F, Request, Response]] = for {
        currentStateRef <- Ref.of[F, State[S, D, Request, Response]](
          State(stateName, stateData, timeout)
        )
        nextStateRef <- Ref.of[F, Option[State[S, D, Request, Response]]](None)
        generationRef <- Ref.of[F, Int](0)
        timeoutFiberRef <- Ref.of[F, Option[Fiber[F, Throwable, Unit]]](None)
      } yield {
        (new Actor[F, Any] with ActorLogging[F, Any] {
          // State manager will allow us to move from state to state.
          val stateManager: StateManager[F, S, D, Request, Response] =
            new StateManager[F, S, D, Request, Response] {
              override def goto(nextStateName: S): F[State[S, D, Request, Response]] =
                currentStateRef.get.map(currentState =>
                  State(nextStateName, currentState.stateData)
                )

              override def stay(): F[State[S, D, Request, Response]] =
                (currentStateRef.get >>= (currentState => goto(currentState.stateName)))
                  .withNotification(false)

              override def stop(reason: Reason): F[State[S, D, Request, Response]] =
                currentStateRef.get >>= (currentState => stop(Normal, currentState.stateData))

              override def stop(reason: Reason, stateData: D): F[State[S, D, Request, Response]] =
                stay().using(stateData).withStopReason(reason)

              override def forMax(
                  finiteDuration: Option[(FiniteDuration, Request)]
              ): F[State[S, D, Request, Response]] =
                stay().map(_.forMax(finiteDuration))

              override def stayAndReply(replyValue: Response): F[State[S, D, Request, Response]] =
                stay().map(_.replying(replyValue))
            }

          private def cancelTimeout: F[Unit] =
            timeoutFiberRef.get.flatMap(_.fold(Sync[F].unit)(_.cancel))

          private def handleTransition(prev: S, next: S): F[Unit] =
            transitionEvent.collect {
              case te if te.isDefinedAt((prev, next)) => te((prev, next))
            }.sequence_

          private val handleEvent: StateFunction[Any] = { case (Event(value, _), _) =>
            for {
              currentState <- currentStateRef.get
              _ <- log(
                s"[Warning] unhandled [Event $value] [State: ${currentState.stateName}] [Data: ${currentState.stateData}]"
              )
            } yield currentState
          }

          override def preStart: F[Unit] = currentStateRef.get >>= makeTransition

          override def receive: ReplyingReceive[F, Any, Any] = {
            case TimeoutMarker(gen, sender, msg) =>
              generationRef.get
                .map(_ == gen)
                .ifM(
                  processMsg(StateTimeoutWithSender(sender, msg), "state timeout"),
                  Sync[F].unit
                )

            case value =>
              cancelTimeout >> generationRef.update(_ + 1) >> processMsg(value, sender)
          }

          private def processMsg(value: Any, source: AnyRef): F[Any] =
            currentStateRef.get >>= (currentState =>
              (if (config.debug) config.receive(value, sender, currentState) else Sync[F].unit)
                >> processEvent(Event(value, currentState.stateData), source)
            )

          private def processEvent(event: FSM.Event[D, Any], @unused source: AnyRef): F[Any] =
            for {
              currentState <- currentStateRef.get
              stateFunc = stateFunctions(currentState.stateName)
              // If this is a Timeout event we will set the original sender on the cell so that the user can reply to the original sender.
              state <- event.event match {
                case StateTimeoutWithSender(sender, msg) =>
                  /* The StateTimeoutWithSender was only used to propagate the sender information, the actual user does not need to understand these
                   * semantics. */
                  // We will simplify the message to the SenderTimeout, the sender will be implicit on the cell.
                  val updatedEvent: FSM.Event[D, Request] =
                    event.copy(event = msg.asInstanceOf[Request])
                  /* Override the sender to be the same sender as the original state (we might want to notify him that something happened in the
                   * timeout) */
                  (self.cell >>= (c =>
                    Async[F]
                      .delay(c.creationContext.senderOp =
                        sender.asInstanceOf[Option[ActorRef[F, Nothing]]]
                      )
                  )) >>
                    stateFunc
                      .lift((updatedEvent, stateManager))
                      .getOrElse(
                        handleEvent((updatedEvent.asInstanceOf[FSM.Event[D, Any]], stateManager))
                      )
                case _ =>
                  stateFunc
                    .lift((event.asInstanceOf[Event[D, Request]], stateManager))
                    .getOrElse(handleEvent((event, stateManager)))
              }

              _ <- applyState(state)
            } yield ()

          private def applyState(nextState: State[S, D, Request, Response]): F[Unit] =
            nextState.stopReason match {
              case None =>
                makeTransition(nextState)

              case Some(_) =>
                val sendReplies: F[Unit] =
                  nextState.replies.reverse.traverse_(r => sender.get.widenRequest[Response] ! r)
                sendReplies >> terminate(nextState) >> context.stop(self)
            }

          def makeTransition(nextState: State[S, D, Request, Response]): F[Unit] = {
            def scheduleTimeout(timeoutData: (FiniteDuration, Request)): F[Unit] =
              cancelTimeout >>
                (for {
                  generation <- generationRef.get
                  currentSender = sender
                  cancelFn <- context.system.scheduler.scheduleOnce_(timeoutData._1)(
                    self !* TimeoutMarker[F, Request](generation, currentSender, timeoutData._2)
                  )
                  _ <- timeoutFiberRef.set(Some(cancelFn))
                } yield ())

            def processStateTransition(currentState: State[S, D, Request, Response]): F[Unit] =
              (if (currentState.stateName != nextState.stateName || nextState.notifies) {
                 nextStateRef.set(Some(nextState)) >>
                   handleTransition(currentState.stateName, nextState.stateName) >>
                   nextStateRef.set(None)
               } else Sync[F].unit) >>
                (if (config.debug) config.transition(currentState, nextState) else Sync[F].unit) >>
                currentStateRef.set(nextState) >>
                (currentState.timeout match {
                  case timeoutData @ Some((d: FiniteDuration, msg)) if d.length >= 0 =>
                    scheduleTimeout(d -> msg)
                  case _ =>
                    stateTimeouts(currentState.stateName).fold(Sync[F].unit)(scheduleTimeout)
                })

            stateFunctions.get(nextState.stateName) match {
              case None =>
                val errorMessage = s"Next state ${nextState.stateName} does not exist"
                stateManager.stay().withStopReason(Failure(errorMessage)) >>= terminate

              case Some(_) =>
                // If the sender is not not specified (e.g. if you are running directly from an application) we will ignore it to be safe.
                nextState.replies.reverse.traverse_(r =>
                  sender.fold(Sync[F].unit)(sender =>
                    {
                      // The sender knew that we could send him the Response so this is totally safe.
                      sender.widenRequest[Response] ! r
                    }.void
                  )
                ) >>
                  currentStateRef.get.flatMap(processStateTransition)
            }

          }

          protected def onTerminated(reason: Reason): F[Unit] =
            onTerminationCallback.fold(Sync[F].unit)(x => x(reason))

          override def postStop: F[Unit] =
            (stateManager.stay().withStopReason(Shutdown) >>= terminate) >> super.postStop

          private def terminate(nextState: State[S, D, Request, Response]): F[Unit] =
            for {
              currentState <- currentStateRef.get
              _ <- currentState.stopReason.fold {
                nextState.stopReason.fold(Sync[F].unit) { reason =>
                  for {
                    _ <- cancelTimeout
                    _ <- onTerminated(reason)
                    _ <- currentStateRef.set(nextState)
                    stopEvent = StopEvent(reason, currentState.stateName, currentState.stateData)
                    _ <- terminateEvent.lift(stopEvent).getOrElse(Sync[F].unit)
                  } yield ()
                }
              }(_ => Sync[F].unit)
            } yield ()
        }).asInstanceOf[ReplyingActor[F, Request, Response]]
      }
    }
  }

  trait PreCompiledFSM {

    def initialize: F[ReplyingActor[F, Request, Response]]
  }

}
