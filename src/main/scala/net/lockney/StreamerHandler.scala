package net.lockney

import akka.actor.{ActorRef, ActorLogging, Actor}
import spray.http._
import spray.client.pipelining._
import akka.io.IO
import spray.can.Http
import spray.json.JsonParser
import scala.concurrent.duration._
import spray.http.HttpRequest
import spray.http.ChunkedResponseStart

object StreamerHandler {

  val name = "streamHandler"

  case object RequestStream
}

class StreamerHandler extends OAuthTwitterAuthorization with Actor with ActorLogging {

  import StreamerHandler._
  import TwitterProtocol._

  val io = IO(Http)(context.system)
  val sendToSelf = sendTo(io).withResponsesReceivedBy(self)
  val retryDelay = 1.seconds
  lazy val streamUrl = context.system.settings.config.getConfig("tweet.er.er").getString("streamUrl")

  implicit val ec = context.dispatcher

  // handy debugging help -- returns the same curl command Twitter shows in their docs
  val requestLogger = { req: HttpRequest =>
    log.debug(s"""Curl: curl --get '${req.uri}' --header '${req.headers.head}' --verbose""")
  }

  // prime the pump
  override def preStart = self ! RequestStream

  // start out waiting for the RequestSteam message
  def receive = waiting

  def handleFailures: Receive = {
    case akka.actor.Status.Failure(e) =>
      log.debug("Failed trying to request stream: {}. Scheduling a retry in {} seconds", e.getMessage,
        retryDelay.toSeconds)
      context become waiting
      context.system.scheduler.scheduleOnce(retryDelay, self, RequestStream)

    case huh =>
      log.debug("What is this thing? {} (class: {})", huh, huh.getClass.getCanonicalName)
  }

  def awaitingResponse: Receive = {
    ({
      case ChunkedResponseStart(_) =>
        log.debug("Stream handling has begun.")

      case MessageChunk(entity, _) =>
        try {
          val tweet = JsonParser(entity.asString).convertTo[Tweet]
          context.actorSelection(s"/user/${TweetAnalyzer.name}") ! tweet
        } catch {
          case e: Exception => // ignore anything not matching the expected format
        }

      case end: ChunkedMessageEnd =>
        self ! RequestStream
        context become waiting

      // This *shouldn't* happen, but stranger things have been known to occur -- saw some weirdness in testing
      case res: HttpResponse =>
        self ! RequestStream
        context become waiting

    }: Receive) orElse handleFailures}

  def waiting: Receive = ({
      case RequestStream =>
        context become awaitingResponse
        sendToSelf(Get(streamUrl) ~> authorize ~> logRequest
          (requestLogger))
    }: Receive) orElse handleFailures
}
