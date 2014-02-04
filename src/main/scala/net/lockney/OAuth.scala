package net.lockney

import java.net.URLEncoder
import java.nio.charset.Charset
import javax.crypto
import org.parboiled.common.Base64
import scala.io.Source
import scala.collection.immutable.TreeMap
import spray.http.{HttpEntity, MediaTypes, ContentType, HttpRequest}
import spray.http.HttpHeaders.RawHeader

/*
 * This code was borrowed from the Cake Solutions Activator template described in this blog post:
 *    http://www.cakesolutions.net/teamblogs/2013/12/08/streaming-twitter-api-in-akka-and-spray/
 *
 * The original code is under the Apache license, so I'm taking the liberty of reusing it to allow use of OAuth with
 * Spray's existing HTTP client.
 *
 * To work properly, you'll need a file located at $HOME/.twitter/tweet-er-er containing 4 lines,
 * with each line containing the values (in this order): consumer key, consumer secret, token key, & token secret.
 */
trait OAuthTwitterAuthorization {
  import OAuth._
  val home = System.getProperty("user.home")
  val lines = Source.fromFile(s"$home/.twitter/tweet-er-er").getLines().toList

  val consumer = Consumer(lines(0), lines(1))
  val token = Token(lines(2), lines(3))

  val authorize: (HttpRequest) => HttpRequest = oAuthAuthorizer(consumer, token)
}

object OAuth {
  case class Consumer(key: String, secret: String)
  case class Token(value: String, secret: String)

  def oAuthAuthorizer(consumer: Consumer, token: Token): HttpRequest => HttpRequest = {
    // construct the key and cryptographic entity
    val SHA1 = "HmacSHA1"
    val keyString = percentEncode(consumer.secret :: token.secret :: Nil)
    val key = new crypto.spec.SecretKeySpec(bytes(keyString), SHA1)
    val mac = crypto.Mac.getInstance(SHA1)

    { httpRequest: HttpRequest =>
      val timestamp = (System.currentTimeMillis / 1000).toString
      // nonce is unique enough for our purposes here
      val nonce = System.nanoTime.toString

      // pick out x-www-form-urlencoded body
      val (requestParams, newEntity) = httpRequest.entity match {
        case HttpEntity.NonEmpty(ContentType(MediaTypes.`application/x-www-form-urlencoded`, _), data) =>
          val params = data.asString.split("&")
          val pairs = params.map { param =>
            val p = param.split("=")
            p(0) -> percentEncode(p(1))
          }
          (pairs.toMap, HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`), "%s=%s" format (pairs(0)._1, pairs(0)._2)))
        case e => (Map(), e)
      }

      // prepare the OAuth parameters
      val oauthParams = Map(
        "oauth_consumer_key" -> consumer.key,
        "oauth_signature_method" -> "HMAC-SHA1",
        "oauth_timestamp" -> timestamp,
        "oauth_nonce" -> nonce,
        "oauth_token" -> token.value,
        "oauth_version" -> "1.0"
      )

      // construct parts of the signature base string
      val encodedOrderedParams = (TreeMap[String, String]() ++ oauthParams ++ requestParams) map { case (k, v) => k + "=" + v } mkString "&"
      val url = httpRequest.uri.toString()
      // construct the signature base string
      val signatureBaseString = percentEncode(httpRequest.method.toString() :: url :: encodedOrderedParams :: Nil)

      mac.init(key)
      val sig = Base64.rfc2045().encodeToString(mac.doFinal(bytes(signatureBaseString)), false)
      mac.reset()

      val oauth = TreeMap[String, String]() ++ (oauthParams + ("oauth_signature" -> percentEncode(sig))) map { case (k, v) => "%s=\"%s\"" format (k, v) } mkString ", "

      // return the signed request
      httpRequest.withHeaders(List(RawHeader("Authorization", "OAuth " + oauth))).withEntity(newEntity)
    }
  }

  private def percentEncode(str: String): String = URLEncoder.encode(str, "UTF-8") replace ("+", "%20") replace ("%7E", "~")
  private def percentEncode(s: Seq[String]): String = s map percentEncode mkString "&"
  private def bytes(str: String) = str.getBytes(Charset.forName("UTF-8"))

}
