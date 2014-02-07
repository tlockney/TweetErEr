package net.lockney

import akka.actor._
import akka.io.IO
import spray.can.Http
import scala.concurrent.duration._
import akka.routing.FromConfig
import spray.json.DefaultJsonProtocol

case class Url(expanded_url: String)
case class Hashtag(text: String)
case class Media(url: String)
case class Entities(urls: Seq[Url], hashtags: Seq[Hashtag], media: Seq[Media])
case class Tweet(text: String, entities: Entities)

object TwitterProtocol extends DefaultJsonProtocol {
  implicit val urlFormat = jsonFormat1(Url)
  implicit val hashtagFormat = jsonFormat1(Hashtag)
  implicit val mediaFormat = jsonFormat1(Media)
  implicit val entitiesFormat = jsonFormat3(Entities)
  implicit val tweetFormat = jsonFormat2(Tweet)
}

object MainActor {
  val name = "main"
}
class MainActor extends Actor with ActorLogging {

  val io = IO(Http)(context.system)

  val config = context.system.settings.config.getConfig("tweet.er.er")

  override def preStart {
    context.actorOf(Props[Metrics], Metrics.name)
    context.actorOf(Props[TweetAnalyzer].withRouter(FromConfig()), TweetAnalyzer.name)
    context.actorOf(Props(classOf[StreamerHandler], io), StreamerHandler.name)
    io ! Http.Bind(context.actorOf(Props[Router], "router"),
      interface = config.getString("host"),
      port = config.getInt("port"))
  }

  def receive = {
    case huh => log.debug("Received unexpected message: {}", huh)
  }

  // Yeah, it's not subtle, but... just restart everything for now.
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(5, 30.seconds) {
    case _ => SupervisorStrategy.Restart
  }
}

object TweetErEr extends OAuthTwitterAuthorization {

  def main(args: Array[String]) {
    val system = ActorSystem("tweet-er-er")

    system.actorOf(Props[MainActor], MainActor.name)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() {
        system.shutdown()
      }
    })
  }
}
