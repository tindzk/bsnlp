name := "bsnlp"

version := "1.0"

scalaVersion := "2.12.1"

addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)

libraryDependencies += "org.apache.commons" % "commons-compress" % "1.13"

libraryDependencies += "co.fs2" %% "fs2-core" % "0.9.2"

libraryDependencies += "co.fs2" %% "fs2-io" % "0.9.2"

libraryDependencies += "pl.metastack" %%  "metaweb" % "0.2.1-SNAPSHOT"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

val Circe = "0.7.0"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % Circe)
