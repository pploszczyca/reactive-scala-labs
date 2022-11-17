package EShop.lab5

import EShop.lab5.ProductCatalogApp.config
import akka.Done
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}

object ProductCatalogAkkaHttpServer {
  sealed trait Command
  case class Listing(listing: Receptionist.Listing) extends Command

  case class SearchRequest(brand: String, productKeyWords: List[String])
  case class SearchResult(items: List[ProductCatalog.Item])
}

trait ProductCatalogJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val searchRequest: RootJsonFormat[ProductCatalogAkkaHttpServer.SearchRequest] = jsonFormat2(
    ProductCatalogAkkaHttpServer.SearchRequest
  )
  implicit val searchResult: RootJsonFormat[ProductCatalogAkkaHttpServer.SearchResult] = jsonFormat1(
    ProductCatalogAkkaHttpServer.SearchResult
  )
  implicit val item: RootJsonFormat[ProductCatalog.Item] = jsonFormat5(
    ProductCatalog.Item
  )

  //custom formatter just for example
  implicit val uriFormat: JsonFormat[URI] = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }
}

class ProductCatalogAkkaHttpServer extends ProductCatalogJsonSupport {
  private implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "ProductCatalogHttp")

  implicit val timeout: Timeout = 3.seconds

  def routes(productCatalogRef: ActorRef[ProductCatalog.Query]): Route = {
    path("search") {
      get {
        entity(as[ProductCatalogAkkaHttpServer.SearchRequest]) { searchRequest =>
          val response = productCatalogRef
            .ask(ref => ProductCatalog.GetItems(searchRequest.brand, searchRequest.productKeyWords, ref))

          onSuccess(response) {
            case ProductCatalog.Items(items) => complete(ProductCatalogAkkaHttpServer.SearchResult(items))
          }
        }
      }
    }
  }

  def start(port: Int): Future[Done] = {
    Behaviors.setup[ProductCatalogAkkaHttpServer.Command] { context =>
      val listingAdapter = context.messageAdapter[Receptionist.Listing](ProductCatalogAkkaHttpServer.Listing.apply)

      context.system.receptionist ! Receptionist.Find(ProductCatalog.ProductCatalogServiceKey, listingAdapter)

      Behaviors.receiveMessagePartial {
        case ProductCatalogAkkaHttpServer.Listing(ProductCatalog.ProductCatalogServiceKey.Listing(listing)) =>
          Http()
            .newServerAt("localhost", port)
            .bind(routes(productCatalogRef = listing.head))
          Behaviors.ignore
      }
    }

    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

object ProductCatalogAkkaHttpServerApp extends App {
  new ProductCatalogAkkaHttpServer().start(10000)
}
