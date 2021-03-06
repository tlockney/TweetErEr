package net.lockney

import akka.actor.{ActorLogging, Actor}
import scala.util.matching.Regex
import java.net.URL

object TweetAnalyzer {
  val name = "analyzer"
}

class TweetAnalyzer extends Actor with ActorLogging {

  val emojiSet: List[(String, String)] = Emojis.all.map(e => (e.name, e.matchableString))

  def metricsPath = context.actorSelection(s"/user/main/${Metrics.name}")

  def receive = {
    case Tweet(text, entities) =>
      metricsPath ! Metrics.TweetSeen

      // Check for emojis. This is an expensive way to do this, for a fully loaded system, it would make more sense to
      // use a smarted approach.
      emojiSet.foreach {
        case (name, chars) =>
          // yeah, regexs are heavy-handed here, but they make this relatively simple
          (new Regex(chars)).findFirstIn(text).foreach { _ =>
            metricsPath ! Metrics.EmojiSeen(name)
          }
      }

      entities.hashtags.map { tag =>
        metricsPath ! Metrics.HashtagSeen(tag.text)
      }

      entities.urls.map { url =>
        metricsPath ! Metrics.UrlSeen(new URL(url.expanded_url))
      }

    case _ =>
  }
}
