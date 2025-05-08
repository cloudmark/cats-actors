import sbt._
import Keys._

import sbtcrossproject.CrossPlugin.autoImport._

import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

val scala3 = "3.3.5"
val scala2 = "2.13.16"

ThisBuild / organization := "com.suprnation"
ThisBuild / version := "2.0.1"
ThisBuild / organizationName := "SuprNation"
ThisBuild / startYear := Some(2024)
ThisBuild / licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.txt"))

//ThisBuild / crossScalaVersions := Seq("2.13.16", "3.3.5")
ThisBuild / scalaVersion := scala3

lazy val commonSettings = Seq(
  Test / parallelExecution := false,
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
        List(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full))
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
      "org.typelevel" %%% "cats-effect" % "3.7-4972921",
      "org.scalatest" %%% "scalatest" % "3.2.19" % Test,
      // "org.typelevel" %%% "cats-effect-testing-scalatest" % "1.6-3786de2-SNAPSHOT" % Test
      "org.typelevel" %%% "cats-effect-testing-scalatest" % "1.6-87d2376-SNAPSHOT" % Test
    )
  )
  .jvmSettings(
    crossJavaVersions := Seq(scala3, scala2)
  )
  .jsSettings(
    crossScalaVersions := Seq(scala3)
  )
  .nativeSettings(
    crossScalaVersions := Seq(scala3)
  )

lazy val catsActorsJVM = catsActors.jvm
lazy val catsActorsJS = catsActors.js
lazy val catsActorsNative = catsActors.native

lazy val root = (project in file("root"))
  .aggregate(catsActorsJVM, catsActorsJS, catsActorsNative)
  .settings(
    name := "cats-actors-root",
    publish / skip := true
  )
