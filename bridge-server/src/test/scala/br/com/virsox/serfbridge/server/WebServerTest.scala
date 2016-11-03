package br.com.virsox.serfbridge.server

import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.{TestKitBase, TestProbe}
import org.scalatest.{Matchers, WordSpec}
import spray.json._

import scala.concurrent.duration._

/** Test for the WebServer services. */
class WebServerTest extends WordSpec with Matchers with ScalatestRouteTest with TestKitBase {

  /** Fixture */
  trait TestFixture {
    val probe = TestProbe()
    val route = WebServer.route(Seq(probe.ref))
    val jsonData = """{
        "name": "node1",
        "address": "200.100.101.122",
        "role": "none",
        "tags": {
          "dc": "us-east1"
         }
      }"""

    val node1 = Member("node1", "200.100.101.122", "none", Map("dc" -> "us-east1"))
  }


  "The service" should {

    "accept new members" in new TestFixture {
      Post("/dc/us-east1/members", HttpEntity(`application/json`, jsonData)) ~> route ~> check {
        status shouldBe Created
        header[Location] shouldBe Some(Location(Uri("http://example.com/dc/us-east1/members/node1")))
      }

      probe.expectMsg(1.second, MemberJoin("us-east1", node1))
    }


    "update existing members" in new TestFixture {
      val updatedJsonData = """{
        "name": "node1",
        "address": "200.100.101.130",
        "role": "web",
        "tags": {
          "dc": "us-east1"
         }
      }"""

      Put("/dc/us-east1/members/node1", HttpEntity(`application/json`, updatedJsonData)) ~> route ~> check {
        status shouldBe OK

        val jsonBody = responseAs[String].parseJson.asJsObject
        jsonBody.fields("name")    shouldBe JsString("node1")
        jsonBody.fields("address") shouldBe JsString("200.100.101.130")
        jsonBody.fields("role")    shouldBe JsString("web")
        jsonBody.fields("tags")    shouldBe JsObject(Map("dc" -> JsString("us-east1")))
      }

      val updatedNode1 = Member("node1", "200.100.101.130", "web", Map("dc" -> "us-east1"))
      probe.expectMsg(1.second, MemberUpdate("us-east1", updatedNode1))
    }

    "delete leaving members" in new TestFixture {
      Delete("/dc/us-east1/members/node1") ~> route ~> check {
        status shouldBe NoContent
      }
      probe.expectMsg(1.second, MemberLeave("us-east1", "node1"))
    }

    "delete reaped members" in new TestFixture {
      Delete("/dc/us-east1/members/node1?reap=true") ~> route ~> check {
        status shouldBe NoContent
      }
      probe.expectMsg(1.second, MemberReap("us-east1", "node1"))
    }

    "signal failed members" in new TestFixture {
      Post("/dc/us-east1/failures", HttpEntity(`application/json`, jsonData)) ~> route ~> check {
        status shouldBe NoContent
      }
      probe.expectMsg(1.second, MemberFailed("us-east1", "node1"))

    }
  }

}



