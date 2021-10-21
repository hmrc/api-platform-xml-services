import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  lazy val enumeratumVersion = "1.6.2"
  lazy val scalaCheckVersion = "1.14.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % "5.16.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % "0.55.0",
    "com.beachape"            %% "enumeratum-play-json"       % enumeratumVersion,
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % "5.16.0"          % Test,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % "0.55.0"          % Test,
    "org.pegdown"             %  "pegdown"                  % "1.6.0"             % "test, it",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.36.8"          % "test, it",
    "org.mockito"             %% "mockito-scala-scalatest"  % "1.7.1"             % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "5.1.0"             % "test, it",
    "com.github.tomakehurst"  % "wiremock-jre8-standalone"  % "2.27.1"            % "test, it",
    "org.scalacheck"          %% "scalacheck"               % scalaCheckVersion   % "test, it"
  )
}
