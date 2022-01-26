/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformxmlservices.service

import uk.gov.hmrc.apiplatformxmlservices.models.VendorId
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.service.VendorIdService.Config

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VendorIdService @Inject()(organisationRepository: OrganisationRepository,
                                config: Config)(implicit val ec: ExecutionContext) {

  def getNextVendorId(): Future[Either[Throwable, VendorId]] = {

    organisationRepository.findOrgWithMaxVendorId().map {
          mayBeOrganisation =>
            mayBeOrganisation
              .fold(Right(VendorId(config.startingVendorId)))(x=> Right(calculateNextVendorId(x.vendorId)))
    }.recover{
      case e: Exception => Left(e)
    }
  }

  private def calculateNextVendorId(maxVendorId: VendorId) : VendorId = {
    if(maxVendorId.value < config.startingVendorId) VendorId(config.startingVendorId)
    else VendorId(maxVendorId.value + 1)
  }
}

object VendorIdService {
  case class Config(startingVendorId: Long)
}



