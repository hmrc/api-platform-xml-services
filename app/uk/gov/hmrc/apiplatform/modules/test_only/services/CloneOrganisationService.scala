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

import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.service.{OrganisationService, VendorIdService}

@Singleton
class CloneOrganisationService @Inject() (
    orgRepo: OrganisationRepository,
    testOrgRepo: TestOrganisationsRepository,
    organisationService: OrganisationService,
    vendorIdService: VendorIdService
  )(implicit ec: ExecutionContext
  ) {

  private val E  = EitherTHelper.make[Throwable]

  def cloneOrg(id: OrganisationId): Future[Either[Throwable, Organisation]] = {
    (
      for {
        oldOrg   <- E.fromOptionF(orgRepo.findByOrgId(id), new RuntimeException(s"Cannot find organisation: $id"))
        suffix    = Instant.now().toEpochMilli().toHexString
        newName   = OrganisationName(s"${oldOrg.name} clone $suffix")
        vendorId <- E.fromEitherF(vendorIdService.getNextVendorId())
        result   <- E.liftF(organisationService.createOrganisation(newName, vendorId, List.empty))
        org       = result match {
                      case CreateOrganisationSuccessResult(organisation) => organisation
                      case _                                             => throw new RuntimeException("Cannot creation organisation for clone")
                    }
        _        <- E.liftF(testOrgRepo.record(org.organisationId))
        finalOrg <- E.fromEitherF(organisationService.update(org.copy(services = oldOrg.services, collaborators = oldOrg.collaborators)))
      } yield finalOrg
    )
      .value
  }
}
