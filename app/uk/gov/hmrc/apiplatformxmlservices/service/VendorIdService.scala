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

package uk.gov.hmrc.apiplatformxmlservices.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.apiplatformxmlservices.models.VendorId
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.service.VendorIdService.Config

@Singleton
class VendorIdService @Inject() (organisationRepository: OrganisationRepository, config: Config)(implicit val ec: ExecutionContext) {

  val ranges = List(
    ((9000L, 9999L)),
    ((2500L, 2999L)),
    ((3200L, 3999L)),
    ((4100L, 4999L)),
    ((5200L, 5999L)),
    ((6100L, 6999L))
  )

  def getNextVendorId(): Future[VendorId] = {
    getNextVendorId(ranges)
  }

  private final def getNextVendorId(ranges: List[(Long, Long)]): Future[VendorId] = {
    ranges match {
      case Nil           => Future.failed(new RuntimeException("No empty range of ids left to search"))
      case range :: tail =>
        organisationRepository.findOrgWithMaxVendorIdInRange(range._1, range._2).map {
          case None                                         => Some(VendorId(range._1))
          case Some(vendorId) if vendorId.value == range._2 => None
          case Some(vendorId)                               => Some(VendorId(vendorId.value + 1))
        }
          .flatMap { oNextId =>
            {
              if (oNextId.isDefined)
                Future.successful(oNextId.get)
              else
                getNextVendorId(tail)
            }
          }
    }
  }
}

object VendorIdService {
  case class Config(startingVendorId: Long)
}
