import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import bloop.integrations.sbt.BloopDefaults

val appName = "api-platform-xml-services"


scalaVersion := "2.13.8"

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision


lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion                     := 0,
    PlayKeys.playDefaultPort         := 11116,
    routesImport                     += "uk.gov.hmrc.apiplatformxmlservices.controllers.binders._",
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test,
  )
  .settings(scoverageSettings)
  .settings(Compile / unmanagedResourceDirectories  += baseDirectory.value / "resources")
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(headerSettings(IntegrationTest) ++ automateHeaderSettings(IntegrationTest))
  .settings(resolvers += Resolver.jcenterRepo)
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(
    scalacOptions ++= Seq(
      "-Wconf:cat=unused&src=views/.*\\.scala:s",
      "-Wconf:cat=unused&src=.*RoutesPrefix\\.scala:s",
      "-Wconf:cat=unused&src=.*Routes\\.scala:s",
      "-Wconf:cat=unused&src=.*ReverseRoutes\\.scala:s"
    )
  )

  lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := ";.*\\.models\\..*;uk\\.gov\\.hmrc\\.BuildInfo;.*\\.Routes;.*\\.RoutesPrefix;;.*ConfigurationModule;GraphiteStartUp;.*\\.Reverse[^.]*",
    ScoverageKeys.coverageMinimumStmtTotal := 96,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
