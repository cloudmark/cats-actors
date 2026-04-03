import sbt._
import Keys._

import sbtcrossproject.CrossPlugin.autoImport._

import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

val scala3 = "3.3.7"
val scala2 = "2.13.18"

ThisBuild / organization := "com.suprnation"
ThisBuild / version := "2.1.0"
ThisBuild / organizationName := "SuprNation"
ThisBuild / startYear := Some(2024)
ThisBuild / licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.txt"))

ThisBuild / crossScalaVersions := Seq(scala2, scala3)

ThisBuild / scalaVersion := scala3

lazy val benchmark = project
  .in(file("benchmark/"))
  .dependsOn(catsActorsJVM)
  .enablePlugins(JmhPlugin)
  .settings(
    name := "cats-actors-benchmark",
    scalaVersion := scala3,
    libraryDependencies ++= Seq(),
    publish / skip := true
  )

lazy val commonSettings = Seq(
  Test / parallelExecution := false,
  publishMavenStyle := true,
  publishTo := Some(
    "GitHub Package Registry" at "https://maven.pkg.github.com/cloudmark/cats-actors"
  ),
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  scalacOptions ++= {
    scalaBinaryVersion.value match {
      case "2.13" =>
        List(
          "-language:implicitConversions",
          "-language:existentials",
          "-P:kind-projector:underscore-placeholders"
        )
      case _ => List("-Ykind-projector:underscores")
    }
  },
  libraryDependencies ++= {
    scalaBinaryVersion.value match {
      case "2.13" =>
        List(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.4" cross CrossVersion.full))
      case _ =>
        Nil
    }
  }
)

lazy val catsActors = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .settings(commonSettings)
  .settings(
    name := "cats-actors",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % "3.7.0",
      "org.scalatest" %%% "scalatest" % "3.2.18" % Test,
      "org.typelevel" %%% "cats-effect-testing-scalatest" % "1.8.0" % Test
    )
  )
  .jvmSettings(
    libraryDependencies += "org.typelevel" %% "scalac-compat-annotation" % "0.1.4"
  )

lazy val catsActorsJVM = catsActors.jvm
lazy val catsActorsJS = catsActors.js
lazy val catsActorsNative = catsActors.native

lazy val root = (project in file("."))
  .aggregate(catsActorsJVM, catsActorsJS, catsActorsNative)
  .settings(
    name := "cats-actors-root",
    publish / skip := true
  )
