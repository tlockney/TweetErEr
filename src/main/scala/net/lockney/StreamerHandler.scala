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

  implicit val ec = context.dispatcher

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
      log.debug("What is this? {} (class: {})", huh, huh.getClass.getCanonicalName)
  }

  def awaitingResponse: Receive = {
    log.debug("awaiting response")
    ({
      case ChunkedResponseStart(_) =>
        log.info("Stream handling has begun.")

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

      case res: HttpResponse =>
        log.debug("Received: {}", res)
        self ! RequestStream
        context become waiting

    }: Receive) orElse handleFailures}

  def waiting: Receive = ({
      case RequestStream =>
        log.debug("Requesting the stream")
        context become awaitingResponse
        sendToSelf(Get("https://stream.twitter.com/1.1/statuses/sample.json") ~> authorize ~> logRequest
          (requestLogger))
    }: Receive) orElse handleFailures
}
