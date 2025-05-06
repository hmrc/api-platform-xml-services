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

package uk.gov.hmrc.apiplatform.modules.test_only.scheduled

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.scheduling._
import uk.gov.hmrc.apiplatform.modules.test_only.repositories.TestOrganisationsRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.{LockRepository, LockService}

import uk.gov.hmrc.apiplatformxmlservices.models.OrganisationId
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository

object TestOrganisationsCleanupJob {
  case class Config(initialDelay: FiniteDuration, interval: FiniteDuration, enabled: Boolean, expiryDuration: FiniteDuration)
}

@Singleton
class TestOrganisationsCleanupJob @Inject() (
    cleanupLockService: TestOrganisationsCleanupJobLockService,
    orgRepo: OrganisationRepository,
    testOrgRepo: TestOrganisationsRepository,
    val clock: Clock,
    jobConfig: TestOrganisationsCleanupJob.Config
  )(implicit val ec: ExecutionContext
  ) extends ScheduledMongoJob with ClockNow {

  override def name: String                 = "testOrganisationsCleanupJob"
  override def interval: FiniteDuration     = jobConfig.interval
  override def initialDelay: FiniteDuration = jobConfig.initialDelay
  override val isEnabled: Boolean           = jobConfig.enabled
  override val lockService: LockService     = cleanupLockService
  implicit val hc: HeaderCarrier            = HeaderCarrier()

  logger.info("TestOrganisationsCleanupJob ready!!!")

  override def runJob(implicit ec: ExecutionContext): Future[RunningOfJobSuccessful] = {
    val timeBeforeWhichOrgIsConsideredExpired: Instant = instant().minus(jobConfig.expiryDuration.toMinutes, ChronoUnit.MINUTES)
    logger.info(s"Delete expired test applications created earlier than $timeBeforeWhichOrgIsConsideredExpired ( ${jobConfig.expiryDuration.toMinutes} mins ago)")

    val result: Future[RunningOfJobSuccessful.type] = for {
      idsToRemove <- testOrgRepo.findCreatedBefore(timeBeforeWhichOrgIsConsideredExpired)
      _            = logger.info(s"Scheduled job $name found ${idsToRemove.size} test applications")
      _           <- Future.sequence(idsToRemove.map(deleteExpiredOrganisations(_)))
    } yield RunningOfJobSuccessful

    result.recoverWith {
      case e: Throwable => Future.failed(RunningOfJobFailed(name, e))
    }
  }

  private def deleteExpiredOrganisations(id: OrganisationId): Future[Unit] = {
    logger.info(s"Delete expired test organisation $id.")

    (for {
      _ <- orgRepo.deleteByOrgId(id)
      _ <- testOrgRepo.delete(id)
    } yield ()).recover {
      case NonFatal(e) =>
        logger.info(s"Failed to delete expired test organisation $id", e)
        ()
    }
  }
}

class TestOrganisationsCleanupJobLockService @Inject() (repository: LockRepository)
    extends LockService {

  override val lockId: String                 = "TestOrganisationsCleanupScheduler"
  override val lockRepository: LockRepository = repository
  override val ttl: Duration                  = 1.hours
}
