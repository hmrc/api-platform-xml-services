import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  lazy val mongoVersion      = "2.14.0"
  lazy val bootstrapVersion  = "10.8.0"
  val apiDomainVersion       = "1.8.0"
  val commonDomainVersion    = "1.4.0"

  def apply(): Seq[ModuleID] =
    compile ++ test

  lazy val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % mongoVersion,
    "uk.gov.hmrc"       %% "api-platform-api-domain"   % apiDomainVersion,
    "uk.gov.hmrc"       %% "api-platform-common-domain" % commonDomainVersion,
    "org.typelevel"     %% "cats-core"                 % "2.13.0",
    "com.iheart"        %% "ficus"                     % "1.5.2"
  )

  lazy val test = Seq(
    "uk.gov.hmrc"           %% "bootstrap-test-play-30"              % bootstrapVersion,
    "uk.gov.hmrc.mongo"     %% "hmrc-mongo-test-play-30"             % mongoVersion,
    "com.vladsch.flexmark"  % "flexmark-all"                         % "0.64.8",
    "org.mockito"           %% "mockito-scala-scalatest"             % "2.0.0",
    "uk.gov.hmrc"           %% "api-platform-common-domain-fixtures" % commonDomainVersion
  ).map(_ % "test")
}
