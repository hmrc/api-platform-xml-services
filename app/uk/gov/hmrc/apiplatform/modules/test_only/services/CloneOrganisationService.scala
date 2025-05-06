/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatform.modules.test_only.services

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.test_only.repositories.TestOrganisationsRepository
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.service.OrganisationService

@Singleton
class CloneOrganisationService @Inject() (
    orgRepo: OrganisationRepository,
    testOrgRepo: TestOrganisationsRepository,
    organisationService: OrganisationService,
    tpdConnector: ThirdPartyDeveloperConnector
  )(implicit ec: ExecutionContext
  ) {

  private val E  = EitherTHelper.make[OrganisationId]
  private val E2 = EitherTHelper.make[Throwable]

  def cloneOrg(id: OrganisationId)(implicit hc: HeaderCarrier): Future[Either[OrganisationId, VendorId]] = {
    (
      for {
        oldOrg     <- E.fromOptionF(orgRepo.findByOrgId(id), id)
        suffix      = Instant.now().toEpochMilli().toHexString
        newName     = OrganisationName(s"${oldOrg.name} clone $suffix")
        firstCollab = oldOrg.collaborators.head
        user       <- E2.fromEitherF(tpdConnector.getByEmail(List(firstCollab.email))).bimap(_ => id, _.head)

        result     <- E.liftF(organisationService.create(CreateOrganisationRequest(newName, user.email, user.firstName, user.lastName)))
        org         = result match {
                        case CreateOrganisationSuccessResult(organisation) => Right(organisation)
                        case _                                             => throw new RuntimeException("COR failed")
                      }
        _          <- E.liftF(testOrgRepo.record(org.value.organisationId))
      } yield result match {
        case CreateOrganisationSuccessResult(organisation) => organisation.vendorId
        case _                                             => throw new RuntimeException("COR failed")
      }
    )
      .value
      .recover {
        case NonFatal(_) => Left(id)
      }
  }
}
