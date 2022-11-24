package EShop.lab6

import io.gatling.core.Predef.{Simulation, StringBody, jsonFile, rampUsers, scenario, _}
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._

class ProductCatalogAkkaHttpServerGatlingTest extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http //values here are adjusted to cluster_demo.sh script
    .baseUrl("http://localhost:10001")
    .acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn: ScenarioBuilder = scenario("BasicProductSimulation")
    .feed(
      jsonFile(classOf[ProductCatalogAkkaHttpServerGatlingTest].getResource("/data/product_data.json").getPath).random
    )
    .exec(
      http("work_basic")
        .get("/search")
        .body(StringBody("""{ "brand": "${brand}", "productKeyWords": ["${productKeyWords}"] }"""))
        .asJson
    )
    .pause(1)

  setUp(
    scn.inject(rampUsers(100).during(1.minutes))
  ).protocols(httpProtocol)
}
