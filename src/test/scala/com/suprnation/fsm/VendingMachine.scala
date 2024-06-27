package com.suprnation.fsm

import cats.effect.{IO, Ref}
import com.suprnation.actor.Actor.Actor
import com.suprnation.actor.fsm.FSM.Event
import com.suprnation.actor.fsm.State.StateTimeout
import com.suprnation.actor.fsm.{FSM, StateManager}
import com.suprnation.typelevel.fsm.syntax._

import scala.concurrent.duration._

// Case class to store the data.
case class Item(name: String, amount: Int, price: Double)

// Messages
sealed trait VendingRequest
case class SelectProduct(product: String) extends VendingRequest
case class InsertMoney(amount: Double) extends VendingRequest
case object Dispense extends VendingRequest
case object AwaitingUserTimeout extends VendingRequest

sealed trait VendingResponse
case object ProductOutOfStock
case class RemainingMoney(amount: Double)
case object Timeout extends VendingResponse
case class Change(product: String, inserted: Double, change: Double) extends VendingResponse
case object PressDispense extends VendingResponse

// States
sealed trait VendingMachineState
case object Idle extends VendingMachineState
case object AwaitingPayment extends VendingMachineState
case object Dispensing extends VendingMachineState
case object OutOfStock extends VendingMachineState

// Data
trait VendingMachineData
case class Uninitialized() extends VendingMachineData
case class ReadyData(product: String, price: Double, inventory: Int) extends VendingMachineData
case class TransactionData(product: String, price: Double, insertedAmount: Double, inventory: Int)
    extends VendingMachineData

object VendingMachine {
  def vendingMachine(_inventory: Item*): IO[Actor[IO, VendingRequest]] = {
    type SM = StateManager[IO, VendingMachineState, VendingMachineData, VendingRequest, Any]
    type SF = FSM.StateFunction[IO, VendingMachineState, VendingMachineData, VendingRequest, Any]
    for {
      inventory <- Ref[IO].of(_inventory.map(x => x.name -> x).toMap)
      productAvailable = (product: String) =>
        inventory.get.map(
          _.get(product).fold(Option.empty[Item])(product =>
            if (product.amount > 0) Option(product) else None
          )
        )

      updateInventory = (product: String, newInventory: Int) =>
        inventory.update(currentInventory =>
          currentInventory.updated(product, currentInventory(product).copy(amount = newInventory))
        )

      idleState: SF = { case (Event(SelectProduct(product), Uninitialized()), sM: SM) =>
        productAvailable(product).flatMap {
          case Some(p) =>
            sM.goto(AwaitingPayment)
              .using(ReadyData(product, p.price, p.amount))
              .replying(RemainingMoney(p.price))
          case None =>
            sM.goto(OutOfStock).replying(ProductOutOfStock)
        }
      }

      awaitingPaymentState: SF = {
        // Customer has initiated settling the money
        case (Event(InsertMoney(amount), ReadyData(product, price, inventory)), sM: SM) =>
          if (amount >= price) {
            sM.goto(Dispensing)
              .using(TransactionData(product, price, amount, inventory))
              .replying(PressDispense)
          } else {
            // Save the current transaction amount and allow the customer to insert more money
            sM.stay()
              .using(TransactionData(product, price, amount, inventory))
              .replying(RemainingMoney(price - amount))
          }

        // Customer has insert some money prior
        case (
              Event(
                InsertMoney(amount),
                TransactionData(product, price, alreadyInsertedAmount, inventory)
              ),
              sM: SM
            ) =>
          // Total price has been fulfilled
          if (amount + alreadyInsertedAmount >= price) {
            sM.goto(Dispensing)
              .using(TransactionData(product, price, amount + alreadyInsertedAmount, inventory))
              .replying(PressDispense)
          } else {
            sM.stay()
              .using(TransactionData(product, price, amount + alreadyInsertedAmount, inventory))
              .replying(RemainingMoney(price - (amount + alreadyInsertedAmount)))
          }

        // Customer has timed out, we need to give back the money if the customer inserted some.
        case (
              Event(StateTimeout, _),
              sM: StateManager[IO, VendingMachineState, VendingMachineData, VendingRequest, Any]
            ) =>
          sM.goto(Idle).using(Uninitialized()).replying(Timeout)
      }

      dispensingState: SF = {
        case (
              Event(Dispense, TransactionData(product, price, insertedAmount, inventory)),
              sM: SM
            ) =>
          (updateInventory(product, inventory - 1) >>
            sM.goto(Idle))
            .using(Uninitialized())
            .replying(Change(product, insertedAmount, insertedAmount - price))
      }

      outOfStockState: SF = { case (Event(_, _), sM: SM) => // Just an example to handle this state.
        sM.goto(Idle).using(Uninitialized()).replying(ProductOutOfStock)
      }

      // Putting it all together...
      vendingMachine <- FSM[IO, VendingMachineState, VendingMachineData, VendingRequest, Any]
        .when(Idle)(idleState)
        .when(AwaitingPayment, stateTimeout = 1.seconds, onTimeout = AwaitingUserTimeout)(
          awaitingPaymentState
        )
        .when(Dispensing)(dispensingState)
        .when(OutOfStock)(outOfStockState)
        //        .withConfig(FSMConfig.withConsoleInformation)
        .startWith(Idle, Uninitialized())
        .initialize
    } yield vendingMachine
  }
}
