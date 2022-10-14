package EShop.lab2

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any) extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart extends Command
  case object StartCheckout extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  val system: ActorSystem = akka.actor.ActorSystem("system")

  import CartActor._

  private val log = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer: Cancellable =
    system.scheduler.scheduleOnce(delay = cartTimerDuration) {
      self ! ExpireCart
    }

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(
        cart = Cart.empty addItem item,
        timer = scheduleTimer,
      )
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      timer.cancel
      context become nonEmpty(
        cart = cart addItem item,
        timer = scheduleTimer,
      )

    case RemoveItem(item) =>
      onRemoveItem(
        cart = cart,
        item = item,
        timer = timer,
      )

    case ExpireCart =>
      context become empty

    case StartCheckout =>
      context become inCheckout(cart = cart)
  }

  private def onRemoveItem(cart: Cart,
                           item: Any,
                           timer: Cancellable): Unit = {
    val newCart = cart removeItem item

    if (isElementRemoved) {
      timer.cancel
      newCart.size match {
        case size if size > 0 => context become nonEmpty(
          cart = newCart,
          timer = scheduleTimer,
        )
        case size if size == 0 => context become empty
      }
    }

    def isElementRemoved = cart.size != newCart.size
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      context become nonEmpty(
        cart = cart,
        timer = scheduleTimer,
      )

    case ConfirmCheckoutClosed =>
      context become empty
  }

}
