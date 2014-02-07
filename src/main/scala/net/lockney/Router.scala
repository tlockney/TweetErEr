package net.lockney

import spray.routing.HttpServiceActor
import spray.httpx.SprayJsonSupport
import spray.http.StatusCodes

class Router extends HttpServiceActor with SprayJsonSupport {

  import StatsSummaryProtocol._

  def receive = runRoute(
    path("") {
      get {
        // a redirect here might be better, but... keeping it simple for now
        redirect("/stats", StatusCodes.TemporaryRedirect)
      }
    } ~
    path("stats") {
      get {
        complete(Metrics.getStats)
      }
    }
  )

}
