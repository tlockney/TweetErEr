package net.lockney

import akka.actor.{Props, ActorSystem}
import akka.io.IO
import spray.can.Http
import akka.util.Timeout
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

object TweetErEr extends OAuthTwitterAuthorization {

  def main(args: Array[String]) {
    println("Tweet-er-er is a go!")

    implicit val system = ActorSystem("tweet-er-er")

    implicit val futureTimeout: Timeout = 60.seconds

    val config = system.settings.config.getConfig("tweet.er.er")

    val io = IO(Http)

    // these should really get proper supervision, but given the nature of this work,
    // it's neither worthwhile nor obvious where fault handling should go
    system.actorOf(Props[MetricsActor], MetricsActor.name)
    system.actorOf(Props[TweetAnalyzer].withRouter(FromConfig()), TweetAnalyzer.name)
    system.actorOf(Props[StreamerHandler], StreamerHandler.name)
    io ! Http.Bind(system.actorOf(Props[Router], "router"),
      interface = config.getString("host"),
      port = config.getInt("port"))

  }
}
