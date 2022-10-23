package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case object DoPayment extends Command
  sealed trait Event
  case object PaymentReceived extends Event
}

class Payment(
  method: String,
  orderManagerEventHandler: ActorRef[Payment.Event],
  checkoutEventHandler: ActorRef[Payment.Event]
) {

  import Payment._

  def start: Behavior[Payment.Command] = Behaviors.receiveMessage {
    case DoPayment =>
      orderManagerEventHandler ! PaymentReceived
      checkoutEventHandler ! PaymentReceived
      Behaviors.same
  }
}
