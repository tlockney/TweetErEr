package net.lockney

import akka.testkit.{TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.actor.{Props, ActorSystem}

class MetricsSpec(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers with
BeforeAndAfterAll {

  def this() = this(ActorSystem("MetricsSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "The Metrics actor" should {
    val metrics = TestActorRef[Metrics]
    for (i <- 0 to 9) {
      for (_ <- i to 9) {
        val name = ('A'.toInt + i).toChar
        metrics ! Metrics.EmojiSeen(name.toString)
      }
    }
    val stats = Metrics.getStats
    stats.emojisSeen should be(55)
    stats.topEmojis should contain(("A", 10))
  }
}
