import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  lazy val mongoVersion      = "2.6.0"
  lazy val bootstrapVersion  = "9.12.0"
  val apiDomainVersion       = "0.19.1"
  val commonDomainVersion    = "0.18.0"

  def apply(): Seq[ModuleID] =
    compile ++ test

  lazy val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % mongoVersion,
    "uk.gov.hmrc"       %% "api-platform-api-domain"   % apiDomainVersion,
    "org.typelevel"     %% "cats-core"                 % "2.10.0",
    "com.iheart"        %% "ficus"                     % "1.5.2"
  )

  lazy val test = Seq(
    "uk.gov.hmrc"           %% "bootstrap-test-play-30"              % bootstrapVersion,
    "uk.gov.hmrc.mongo"     %% "hmrc-mongo-test-play-30"             % mongoVersion,
    "com.vladsch.flexmark"  % "flexmark-all"                         % "0.64.8",
    "org.mockito"           %% "mockito-scala-scalatest"             % "1.17.30",
    "uk.gov.hmrc"           %% "api-platform-common-domain-fixtures" % commonDomainVersion
  ).map(_ % "test")
}
