import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  lazy val enumeratumVersion = "1.6.2"
  lazy val scalaCheckVersion = "1.14.0"
  lazy val mongoVersion = "0.70.0"

  def apply(): Seq[ModuleID] =
    compile ++ test
  
  lazy val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % "5.16.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % mongoVersion,
    "com.beachape"            %% "enumeratum-play-json"       % enumeratumVersion,
    "org.typelevel"           %% "cats-core"                  % "2.4.2"
  )

  lazy val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % "5.16.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % mongoVersion,
    "org.pegdown"             %  "pegdown"                    % "1.6.0",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.36.8",
    "org.mockito"             %% "mockito-scala-scalatest"    % "1.7.1",
    "org.scalatestplus.play"  %% "scalatestplus-play"         % "5.1.0",
    "com.github.tomakehurst"  % "wiremock-jre8-standalone"    % "2.27.1",
    "org.scalacheck"          %% "scalacheck"                 % scalaCheckVersion
  ).map(_ % "test, it")
}
