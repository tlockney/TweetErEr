package net.lockney

import com.codahale.metrics._
import spray.json.DefaultJsonProtocol
import java.net.URL

case class EmojiStat(name: String, count: Long = 0)
case class HashtagStat(tag: String, count: Long = 0)
case class UrlStat(url: URL, count: Long = 0)

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
  topEmojis:         Seq[(String, Long)],
  topHashtags:       Seq[(String, Long)],
  topDomains:        Seq[(String, Long)]
)

object StatsSummaryProtocol extends DefaultJsonProtocol {
  implicit val emojiCountFormat   = jsonFormat2(EmojiStat)
  implicit val hashtagCountFormat = jsonFormat2(HashtagStat)
  implicit val statsSummaryFormat = jsonFormat12(StatsSummary)
}

object Metrics {

  private var emojiStats: Map[String, EmojiStat]     = Map.empty
  private var hashtagStats: Map[String, HashtagStat] = Map.empty
  private var urlStats: Map[URL, UrlStat]            = Map.empty

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

  /**
   * Note: this is *NOT* thread-safe. Either call it from a single thread or go home!
   */
  def countHashtag(s: String) {
    val stats = hashtagStats.getOrElse(s, HashtagStat(s))
    val newStats = stats.copy(count = stats.count + 1)
    hashtagStats = hashtagStats.updated(s, newStats)
  }

  /**
   * Note: this is *NOT* thread-safe. Either call it from a single thread or go home!
   */
  def countEmoji(s: String) {
    emojisSeen.inc
    val stats = emojiStats.getOrElse(s, EmojiStat(s))
    val newStats = stats.copy(count = stats.count  + 1)
    emojiStats = emojiStats.updated(s, newStats)
  }

  private val picDomains = Set("pic.twitter.com", "instagram.com")

  def countUrl(u: URL) {
    urlsSeen.inc
    val stats = urlStats.getOrElse(u, UrlStat(u))
    val newStats = stats.copy(count = stats.count + 1)
    urlStats = urlStats.updated(u, newStats)
    picDomains.foreach(pd => if (u.getHost.endsWith(pd)) picsSeen.inc)
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

    val topEmojis = emojiStats.map {
      case (name, stats) => (name, stats.count)
    }.toSeq.sortWith(statSorter).take(5)

    val topHashtags = hashtagStats.map {
      case (name, stats) => (name, stats.count)
    }.toSeq.sortWith(statSorter).take(5)

    val topDomains = urlStats.map {
      case (url, stats) => (url.getHost, stats.count)
    }.toSeq.sortWith(statSorter).take(5)

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
