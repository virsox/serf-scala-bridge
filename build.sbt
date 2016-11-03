import Dependencies._
import sbt.Keys._


scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature")

lazy val commonSettings = Seq(
    organization := "br.com.virsox.serfbridge",
    version := "0.0.1-SNAPSHOT",
    isSnapshot := true,
    scalaVersion := "2.11.7",
    libraryDependencies ++= (baseDeps ++ testDeps ++ akkaDeps ++ akkaTestDeps)
)

lazy val server = (project in file("bridge-server")).
    settings(commonSettings:_*).
    settings(
        name := "bridge-server"
    )

lazy val sample = (project in file("bridge-sample")).
    enablePlugins(JavaAppPackaging).
    settings(commonSettings:_*).
    settings(
        name := "bridge-sample",
        mainClass in Compile := Some("br.com.virsox.serfbridge.sample.QuorumWebServer")
    ).
    dependsOn(server)
