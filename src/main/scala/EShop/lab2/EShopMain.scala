package EShop.lab2

import EShop.lab2.CartActor.{AddItem, RemoveItem}
import EShop.lab2.Checkout.{ConfirmPaymentReceived, SelectDeliveryMethod, StartCheckout}
import EShop.lab2.TypedCheckout.SelectPayment
import akka.actor.{ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object EShopMain {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("EShop")

    val cart = system.actorOf(Props[CartActor], "cart")
    cart ! AddItem("T-Shirt")
    cart ! AddItem("Jeans")
    cart ! RemoveItem("T-Shirt")

    val checkout = system.actorOf(Props[Checkout], "checkout")
    checkout ! StartCheckout
    checkout ! SelectDeliveryMethod("Paczkomat")
//    checkout ! SelectPayment("BLIK", )
    checkout ! ConfirmPaymentReceived

    Await.result(system.whenTerminated, Duration.Inf)
  }
}
