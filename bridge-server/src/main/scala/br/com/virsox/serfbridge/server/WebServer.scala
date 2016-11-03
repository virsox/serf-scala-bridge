package br.com.virsox.serfbridge.server

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol


/** Support for Member JSON serialization / deserialization */
trait MemberJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val memberFormat = jsonFormat4(Member)
}

/***
  * Simple implementation of a web service that represents a multi-datacenter cluster
  * of machines. It was designed around Serf message and capabilities. This web service
  * transforms the Http requests into messages that are forwarded to Akka actors, which
  * can process them accordingly.
  */
object WebServer extends MemberJsonProtocol {

  /** logger */
  lazy val logger = LoggerFactory.getLogger("WebServer")

  /**
    * Builds the routes specification (akka-http object that represents the service itself).
    * @param destinations Actors to which messages are forwarded.
    * @return Route specification.
    */
  def route(destinations: Seq[ActorRef]) = {

     /*
      * Helper method that sends a message to all specified actors.
      * @param msg Message to be sent.
      */
    def send(msg: SerfMessage): Unit = {
      destinations.foreach((actor) => actor.tell(msg, ActorRef.noSender))
    }


    (pathPrefix("dc" / Segment) & extractRequestContext) { (datacentre, requestContext) =>
      pathPrefix("members") {
        post {
          // this is a POST at /dc/{dc_id}/members with JSON content
          // represents member-join messages
          entity(as[Member]) { member =>
            logger.debug("POST - {}", member)

            send(MemberJoin(datacentre, member))
            val request = requestContext.request
            val location = request.uri.copy(path = request.uri.path / member.name)
            respondWithHeader(Location(location)) {
              complete(HttpResponse(Created))
            }
          }
        } ~
        path(Segment) { memberId =>
          // this is a DELETE at /dc/{dc_id}/members/{member_id}
          // both member-leave and member-reap are modelled as deletes
          // a parameter "reap" is used to differentiate between them
          delete {
            parameters("reap".as[Boolean].?) { (reap) =>
              reap match {
                case Some(true) => send(MemberReap(datacentre, memberId))
                case _ =>          send(MemberLeave(datacentre, memberId))
              }
              logger.debug("DELETE - {}, reap = {}", Seq(memberId, reap))
              complete(HttpResponse(NoContent))
            }
          } ~
          put {
            // this is a PUT at /dc/{dc_id}/members/{member_id}
            // represents member-update messages
            entity(as[Member]) { member =>
              logger.debug("PUT - {}", member)
              send(MemberUpdate(datacentre, member))
              complete(member)
            }
          }
        }
      } ~
      path("failures") {
        post {
          // this is a POST to /dc/{dc_id}/failures
          // represents member-failed messages
          // note that a member-failed differs from member-leave messages because
          // a failure is expected to be temporary
          entity(as[Member]) { member =>
            logger.debug("POST Failure - {}", member)

            send(MemberFailed(datacentre, member.name))
            complete(HttpResponse(NoContent))
          }
        }
      }
    }
  }


}


