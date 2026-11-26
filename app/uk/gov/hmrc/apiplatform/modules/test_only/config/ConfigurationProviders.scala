/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apiplatform.modules.test_only.config

import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.deriving.Mirror

import pureconfig.generic.semiauto.deriveReader
import pureconfig.{ConfigReader, ConfigSource}

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apiplatform.modules.test_only.scheduled.TestOrganisationsCleanupJob
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): List[Binding[?]] = {
    List(
      bind[TestOrganisationsCleanupJob.Config].toProvider[TestOrganisationsCleanupJobConfigProvider]
    )
  }
}

object ConfigHelper {

  def getConfig[T](key: String, f: String => Option[T]): T = {
    f(key).getOrElse(throw new RuntimeException(s"[$key] is not configured!"))
  }
}

@Singleton
class TestOrganisationsCleanupJobConfigProvider @Inject() (val configuration: Configuration)
    extends ServicesConfig(configuration)
    with Provider[TestOrganisationsCleanupJob.Config] {

  extension (c: ConfigReader.type)
    inline def derived[A: Mirror.Of, ProductHint, CoproductHint]: ConfigReader[A] = deriveReader[A]
//  given fd:ValueReader[FiniteDuration] = ValueReader.generatedReader[FiniteDuration]
//  given v:ValueReader[TestOrganisationsCleanupJob.Config] = ValueReader.generatedReader[TestOrganisationsCleanupJob.Config]
  given ConfigReader[TestOrganisationsCleanupJob.Config]                          = deriveReader

  override def get() = {
    val jobConfig =
      //      configuration.underlying.as[TestOrganisationsCleanupJob.Config]("testOrganisationsCleanupJob")
      ConfigSource.fromConfig(configuration.underlying).at("testOrganisationsCleanupJob").loadOrThrow[TestOrganisationsCleanupJob.Config]

    TestOrganisationsCleanupJob.Config(jobConfig.initialDelay, jobConfig.interval, jobConfig.enabled, jobConfig.expiryDuration)
  }
}
