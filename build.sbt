def akkaModule(m: String) = "com.typesafe.akka" %% ("akka-" + m) % "2.2.3"
def sprayModule(m: String) = "io.spray" % ("spray-" + m) % "1.2.0"

name := "tweet-er-er"

organization := "net.lockney"

version := "0.1.0-SNAPSHOT"

javaOptions := Seq("-Dfile.encoding=UTF-8")

libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.2.5",
  "ch.qos.logback" % "logback-classic" % "1.0.7",
  "com.codahale.metrics" % "metrics-core" % "3.0.1",
  akkaModule("actor"),
  akkaModule("slf4j"),
  sprayModule("can"),
  sprayModule("routing"),
  sprayModule("client"),
  "org.scalatest" % "scalatest_2.10" % "2.0" % "test"
)

seq(Revolver.settings: _*)