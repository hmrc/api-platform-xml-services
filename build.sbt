import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.DefaultBuildSettings
import AppDependencies._

import bloop.integrations.sbt.BloopDefaults

val appName = "api-platform-xml-services"

Global / bloopAggregateSourceDependencies := true

ThisBuild / majorVersion := 0
ThisBuild / organization := "uk.gov.hmrc"

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafmtConfig := Some(file(".scalafmt"))

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtDistributablesPlugin)

lazy val commonSettings = Seq(
  retrieveManaged := true
)

lazy val microservice = (project in file("."))
  .enablePlugins(plugins: _*)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    name := appName,
    PlayKeys.playDefaultPort         := 11116,
    libraryDependencies              ++= AppDependencies()
  )
  .settings(ScalafmtPlugin.scalafmtConfigSettings)
  .settings(commonSettings: _*)
  .settings(ScoverageSettings())
  .settings(Compile / unmanagedResourceDirectories  += baseDirectory.value / "resources")
  .settings(
    Test / fork := false,
    Test / parallelExecution := false,
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    Test / unmanagedSourceDirectories += baseDirectory.value / "testcommon"
  )
  .settings(
       routesImport  ++= Seq(
       "uk.gov.hmrc.apiplatformxmlservices.controllers.binders._",
       "uk.gov.hmrc.apiplatformxmlservices.models._",
       "uk.gov.hmrc.apiplatform.modules.common.domain.models._",
       "uk.gov.hmrc.apiplatform.modules.apis.domain.models._"
     )
   )
  .settings(
    scalacOptions ++= Seq(
      "-Wconf:cat=unused&src=views/.*\\.scala:s",
      "-Wconf:cat=unused&src=.*RoutesPrefix\\.scala:s",
      "-Wconf:cat=unused&src=.*Routes\\.scala:s",
      "-Wconf:cat=unused&src=.*ReverseRoutes\\.scala:s"
    )
  )

commands ++= Seq(
  Command.command("run-all-tests") { state => "test" :: "it/test" :: state },

  Command.command("clean-and-test") { state => "clean" :: "compile" :: "run-all-tests" :: state },

  // Coverage does not need compile !
  Command.command("pre-commit") { state => "clean" :: "scalafmtAll" :: "scalafixAll" :: "coverage" :: "run-all-tests" :: "coverageReport" :: "coverageOff" :: state }
)

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(commonSettings: _*)
  // // .settings(scalafixConfigSettings(IntegrationTest))
  .settings(

      ScalafmtPlugin.scalafmtConfigSettings
  )
  // .settings(DefaultBuildSettings.itSettings())

