package net.lockney

import spray.json._
import scala.io.Source

case class Emoji(name:        String,
                 unified:     String,
                 docomo:      String,
                 au:          String,
                 softbank:    String,
                 google:      String,
                 image:       String,
                 sheet_x:     Int,
                 sheet_y:     Int,
                 short_names: Seq[String],
                 text:        Option[String]) {

  def matchableString: String =
    unified.split("-").map(Integer.parseInt(_, 16).toChar).mkString
}

object EmojiProtocol extends DefaultJsonProtocol {
  implicit val emojiFormat = jsonFormat11(Emoji)
}

object Emojis {
  import EmojiProtocol._

  // not the most efficient way to read this, but it's easy enough, it's a one-time cost... & it works.
  lazy val all: List[Emoji] = {
    val emojiCharArray = Source.fromInputStream(getClass.getResourceAsStream("/emoji.json")).toArray
    JsonParser(emojiCharArray).convertTo[List[Emoji]]
  }

  /**
   * Get the raw unicode strings associated with the unified field of each Emoji
   */
  lazy val emojiChars: List[String] = all.map(e => e.matchableString)
}