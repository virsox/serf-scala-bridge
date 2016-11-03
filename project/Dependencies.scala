import sbt._
import Keys._

object Dependencies {


  val logback        = "ch.qos.logback" % "logback-classic" % "1.1.3"
  
  val akkaVersion        = "2.4.10"
  val akkaActor          = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaSl4j           = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  val akkaHttp           = "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion
  val akkaHttpCore       = "com.typesafe.akka" %% "akka-http-core" % akkaVersion
  val akkaHttpSprayJson  = "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion


  // ------------------- test
  val junit     = "junit" % "junit" % "4.11" % Test
  val scalaTest = "org.scalatest" %% "scalatest" % "2.2.6" % Test
  val akkaTest     = "com.typesafe.akka" %% "akka-testkit" % akkaVersion  % Test
  val akkaHttpTest = "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion  % Test

  val baseDeps  = Seq(logback)
  val akkaDeps  = Seq(akkaActor, akkaSl4j, akkaHttp, akkaHttpCore, akkaHttpSprayJson)

  val testDeps     = Seq(junit, scalaTest)
  val akkaTestDeps = Seq(akkaTest, akkaHttpTest)


}