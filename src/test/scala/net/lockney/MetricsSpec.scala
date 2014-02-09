package net.lockney

import akka.testkit.{TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.actor.{Props, ActorSystem}
import java.net.URL

class MetricsSpec(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers with
BeforeAndAfterAll {

  def this() = this(ActorSystem("MetricsSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val metrics = TestActorRef[Metrics]

  def generateData =
    for { i <- 0 to 9
          _ <- i to 9 } yield {
       ('a'.toInt + i).toChar
    }

  "The Metrics actor" should {
    "correctly report emojis seen" in {
      // send ten sets of letters, each decreasing in size by one, starting with 10 As
      generateData.foreach( datum => metrics ! Metrics.EmojiSeen(datum.toString))
      val stats = Metrics.getStats
      stats.emojisSeen should be(55)
      stats.topEmojis should contain(("a", 10))
      stats.topEmojis should contain(("b", 9))
      stats.topEmojis should contain(("c", 8))
      stats.topEmojis should contain(("d", 7))
      stats.topEmojis should contain(("e", 6))
    }

    "correctly report URLs seen" in {
      generateData.foreach( datum => metrics ! Metrics.UrlSeen(new URL(s"http://$datum.com")))
      val stats = Metrics.getStats
      stats.urlsSeen should be(55)
      stats.topDomains should contain(("a.com", 10))
      stats.topDomains should contain(("b.com", 9))
      stats.topDomains should contain(("c.com", 8))
      stats.topDomains should contain(("d.com", 7))
      stats.topDomains should contain(("e.com", 6))
    }

    "correctly report hashtags seen" in {
      generateData.foreach( datum => metrics ! Metrics.HashtagSeen(s"#$datum"))
      val stats = Metrics.getStats
      stats.topHashtags should contain(("#a", 10))
      stats.topHashtags should contain(("#b", 9))
      stats.topHashtags should contain(("#c", 8))
      stats.topHashtags should contain(("#d", 7))
      stats.topHashtags should contain(("#e", 6))
    }
  }

}
