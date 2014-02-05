package net.lockney

import akka.actor.{ActorLogging, Actor}
import java.net.URL
import spray.json.DefaultJsonProtocol
import com.codahale.metrics.{JmxReporter, MetricRegistry}
import scala.collection.mutable.TreeSet
import java.util.concurrent.atomic.AtomicLong

case class StatsSummary(
                         totalTweets:       Long,
                         tweetsPerSecond:   Double,
                         tweetsPerMinute:   Double,
                         tweetsPerHour:     Double,
                         emojisSeen:        Long,
                         emojiToTweetRatio: Double,
                         urlsSeen:          Long,
                         urlsToTweetRatio:  Double,
                         pictureUrlRatio:   Double,
                         topEmojis:         Set[(String, Long)],
                         topHashtags:       Set[(String, Long)],
                         topDomains:        Set[(String, Long)]
                         )

object StatsSummaryProtocol extends DefaultJsonProtocol {
  implicit val statsSummaryFormat = jsonFormat12(StatsSummary)
}

object MetricsActor {

  protected[this] class Stat(val name: String, var count: AtomicLong = new AtomicLong(1)) {
    def currentCount = count.get
  }

  val name = "metrics"

  case object TweetSeen
  case class EmojiSeen(emoji: String)
  case class HashtagSeen(tag: String)
  case class UrlSeen(url: URL)

  implicit val statOrdering = new Ordering[Stat] {
    def compare(e1: Stat, e2: Stat) = {
      val count = (e1.count.get - e2.count.get).toInt
      if (count != 0) count else e1.name.compareTo(e2.name)
    }
  }

  private val emojiStats   = TreeSet[Stat]()
  private val hashtagStats = TreeSet[Stat]()
  private val urlStats     = TreeSet[Stat]()

  private def counterFor(name: String) = registry.counter(name)
  private def meterFor(name: String)   = registry.meter(MetricRegistry.name(name))

  private lazy val tweetMeter  = meterFor("net.lockney.tweets.meter")
  private lazy val emojisSeen  = counterFor("net.lockney.emojisSeen")
  private lazy val urlsSeen    = counterFor("net.lockney.urlsSeen")
  private lazy val picsSeen    = counterFor("net.lockney.picsSeen")

  private lazy val registry = {
    val reg = new MetricRegistry()
    JmxReporter.forRegistry(reg).build().start()
    reg
  }

  def countTweet = tweetMeter.mark

  private def updateStat(item: String, stats: TreeSet[Stat]) {
    stats.find(c => c.name == item) match {
      case Some(stat) => stat.count.incrementAndGet(); stats.update(stat, true)
      case None => stats += new Stat(item)
    }
  }

  /**
   * Note: this is *NOT* thread-safe. Either call it from a single thread or go home!
   */
  protected def countHashtag(item: String) = updateStat(item, hashtagStats)

  /**
   * Note: this is *NOT* thread-safe. Either call it from a single thread or go home!
   */
  protected def countEmoji(item: String) {
    emojisSeen.inc
    updateStat(item, emojiStats)
  }

  private val picDomains = Set("pic.twitter.com", "instagram.com")

  /**
   * Note: this is *NOT* thread-safe. Either call it from a single thread or go home!
   */
  protected def countUrl(u: URL) {
    urlsSeen.inc
    updateStat(u.getHost, urlStats)
    picDomains.foreach(pd => if (u.getHost.toLowerCase.endsWith(pd)) picsSeen.inc)
  }

  val statSorter = (c1: Product2[_, Long], c2: Product2[_, Long]) => c1._2 > c2._2

  def getStats = {

    val tweetCount = tweetMeter.getCount
    val emojiCount = emojisSeen.getCount
    val urlCount   = urlsSeen.getCount
    val picCount   = picsSeen.getCount
    val emojiRatio = if (tweetCount < 1) 0 else (emojiCount / tweetCount.toDouble)
    val urlRatio   = if (urlCount < 1) 0 else (urlCount / tweetCount.toDouble)
    val picRatio   = if (picCount < 1) 0 else (picCount / tweetCount.toDouble)

    val topEmojis = emojiStats.take(5).map(s => (s.name, s.currentCount)).toSet
    val topHashtags = hashtagStats.take(5).map( s => (s.name, s.currentCount)).toSet
    val topDomains = urlStats.take(5).map(s => (s.name, s.currentCount)).toSet

    StatsSummary(
      tweetCount,
      tweetMeter.getOneMinuteRate / 60,
      tweetMeter.getOneMinuteRate,
      tweetMeter.getFifteenMinuteRate * 4,
      emojiCount,
      emojiRatio,
      urlCount,
      urlRatio,
      picRatio,
      topEmojis,
      topHashtags,
      topDomains
    )
  }
}

class MetricsActor extends Actor with ActorLogging {

  import MetricsActor._

  def receive = {
    case TweetSeen =>
      countTweet

    case EmojiSeen(name) =>
      countEmoji(name)

    case HashtagSeen(tag) =>
      countHashtag(tag)

    case UrlSeen(url) =>
      countUrl(url)

    case _ =>
  }
}
