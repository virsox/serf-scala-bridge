package br.com.virsox.serfbridge.sample

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import br.com.virsox.serfbridge.server.WebServer
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.io.StdIn

/**
  * This class illustrates how a WebServer can be started to connect bridge and the QuorumManagerActor.
  */
object QuorumWebServer extends App {

  /** logger */
  lazy val logger = LoggerFactory.getLogger("WebServer")

  implicit val system = ActorSystem("manager-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val config = ConfigFactory.load()

  val manager = system.actorOf(QuorumManagerActor.props(
    config.getString("quorum.node.dc"),
    config.getString("quorum.node.name"),
    config.getInt("quorum.timeout").seconds
  ), "manager")

  val bind = config.getString("server.bind")
  val port = config.getInt("server.port")

  val bindingFuture = Http().bindAndHandle(
    WebServer.route(Seq(manager)), bind, port)

  logger.info(s"Server online at http://{}:{}/\nPress RETURN to stop...", bind, port)
  StdIn.readLine() // let it run until user presses return

  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
