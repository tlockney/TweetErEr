package net.lockney

import akka.actor.{ActorLogging, Actor}
import java.net.URL

object MetricsActor {

  val name = "metrics"

  case object TweetSeen
  case class EmojiSeen(name: String)
  case class HashtagSeen(tag: String)
  case class UrlSeen(url: URL)
}

class MetricsActor extends Actor with ActorLogging {

  import MetricsActor._

  def receive = {
    case TweetSeen =>
      Metrics.countTweet

    case EmojiSeen(name) =>
      Metrics.countEmoji(name)


    case HashtagSeen(tag) =>
      Metrics.countHashtag(tag)

    case UrlSeen(url) =>
      Metrics.countUrl(url)

    case _ =>
  }
}
