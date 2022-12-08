package EShop.lab5

import akka.Done
import akka.actor.typed.javadsl.{GroupRouter, Routers}
import akka.actor.typed.scaladsl.AskPattern.Askable
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
import scala.util.Try

object ProductCatalogAkkaHttpServer {
  case class SearchRequest(brand: String, productKeyWords: List[String])
  case class SearchResult(items: List[ProductCatalog.Item])
}

trait ProductCatalogJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  //custom formatter just for example
  implicit val uriFormat: JsonFormat[URI] = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)

    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val searchRequest: RootJsonFormat[ProductCatalogAkkaHttpServer.SearchRequest] = jsonFormat2(
    ProductCatalogAkkaHttpServer.SearchRequest
  )
  implicit val item: RootJsonFormat[ProductCatalog.Item] = jsonFormat5(
    ProductCatalog.Item
  )
  implicit val searchResult: RootJsonFormat[ProductCatalogAkkaHttpServer.SearchResult] = jsonFormat1(
    ProductCatalogAkkaHttpServer.SearchResult
  )
}

class ProductCatalogAkkaHttpServer extends ProductCatalogJsonSupport {
  private val config               = ConfigFactory.load()
  private val httpWorkersNodeCount = 3

  private implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalogCluster",
    config
  )
//  private val workersPool =
//    system.systemActorOf(Routers.pool(3)(ProductCatalog(new SearchService())), "productWorkersRouter")

  implicit val scheduler        = system.scheduler
  implicit val executionContext = system.executionContext

  private val group: GroupRouter[ProductCatalog.Query] =
    Routers.group[ProductCatalog.Query](ProductCatalog.ProductCatalogServiceKey)
  private val workers: ActorRef[ProductCatalog.Query] =
    system.systemActorOf(group, "clusterProductWorkerRouter")

  implicit val timeout: Timeout = 5.seconds

  def routes(): Route = {
    path("search") {
      get {
        entity(as[ProductCatalogAkkaHttpServer.SearchRequest]) { searchRequest =>
          val items: Future[ProductCatalog.Ack] = workers
            .ask(
              (ref: ActorRef[ProductCatalog.Ack]) =>
                ProductCatalog.GetItems(searchRequest.brand, searchRequest.productKeyWords, ref)
            )

          onSuccess(items) {
            case ProductCatalog.Items(items) =>
              complete(ProductCatalogAkkaHttpServer.SearchResult(items))
          }
        }
      }
    }
  }

  def start(port: Int): Future[Done] = {
    Http()
      .newServerAt("localhost", port)
      .bind(routes())
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

object ProductCatalogAkkaHttpServerApp extends App {
  val port = Try(args(0).toInt).getOrElse(10001)

  new ProductCatalogAkkaHttpServer().start(port)
}

object ClusterNodeApp extends App {
  private val instancesPerNode = 3
  private val config           = ConfigFactory.load()

  val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalogCluster",
    config
      .getConfig(Try(args(0)).getOrElse("seed-node1"))
      .withFallback(config)
  )

  for (i <- 0 to instancesPerNode) system.systemActorOf(ProductCatalog(new SearchService()), s"worker$i")

  Await.ready(system.whenTerminated, Duration.Inf)
}
