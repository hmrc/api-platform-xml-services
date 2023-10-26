import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.DefaultBuildSettings
import AppDependencies._

import bloop.integrations.sbt.BloopDefaults

val appName = "api-platform-xml-services"


scalaVersion := "2.13.8"

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision


lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .settings(
    name := appName,
    organization := "uk.gov.hmrc",
    majorVersion                     := 0,
    PlayKeys.playDefaultPort         := 11116,
    libraryDependencies              ++= AppDependencies(),
    retrieveManaged := true
  )
  .settings(ScoverageSettings())
  .settings(Compile / unmanagedResourceDirectories  += baseDirectory.value / "resources")
  .configs(IntegrationTest)
  .settings(DefaultBuildSettings.integrationTestSettings())
  .settings(
    IntegrationTest / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    IntegrationTest / unmanagedSourceDirectories += baseDirectory.value / "testcommon",
    IntegrationTest / unmanagedSourceDirectories += baseDirectory.value / "it",
    IntegrationTest / parallelExecution := false
  )
  .settings(
    Test / fork := false,
    Test / parallelExecution := false,
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    Test / unmanagedSourceDirectories += baseDirectory.value / "testcommon",
    Test / unmanagedSourceDirectories += baseDirectory.value / "test"
  )
  .settings(
       routesImport  ++= Seq(
       "uk.gov.hmrc.apiplatformxmlservices.controllers.binders._",
       "uk.gov.hmrc.apiplatformxmlservices.models._",
       "uk.gov.hmrc.apiplatform.modules.common.domain.models._",
       "uk.gov.hmrc.apiplatform.modules.apis.domain.models._"
     )
   )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)

Global / bloopAggregateSourceDependencies := true

