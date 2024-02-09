import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  lazy val mongoVersion      = "1.7.0"
  lazy val bootstrapVersion  = "8.4.0"
  val apiDomainVersion       = "0.11.0"
  val commonDomainVersion    = "0.10.0"

  def apply(): Seq[ModuleID] =
    compile ++ test

  lazy val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % mongoVersion,
    "uk.gov.hmrc"       %% "api-platform-api-domain"   % apiDomainVersion,
    "org.playframework" %% "play-json"                 % "3.0.1",
    "org.typelevel"     %% "cats-core"                 % "2.10.0"
  )

  lazy val test = Seq(
    "uk.gov.hmrc"           %% "bootstrap-test-play-30"          % bootstrapVersion,
    "uk.gov.hmrc.mongo"     %% "hmrc-mongo-test-play-30"         % mongoVersion,
    "org.pegdown"            % "pegdown"                         % "1.6.0",
    "org.mockito"           %% "mockito-scala-scalatest"         % "1.17.29",
    "com.github.tomakehurst" % "wiremock-jre8-standalone"        % "2.35.1",
    "uk.gov.hmrc"           %% "api-platform-test-common-domain" % commonDomainVersion
  ).map(_ % "test")
}
