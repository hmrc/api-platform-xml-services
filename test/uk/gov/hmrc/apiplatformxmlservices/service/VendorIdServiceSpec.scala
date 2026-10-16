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

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import uk.gov.hmrc.apiplatformxmlservices.common.data.CommonTestData
import uk.gov.hmrc.apiplatformxmlservices.common.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatformxmlservices.models.{Organisation, OrganisationName, VendorId, XmlOrganisationId}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.service.VendorIdService.Config

class VendorIdServiceSpec extends AsyncHmrcSpec {

  trait Setup extends CommonTestData {
    val mockOrganisationRepo: OrganisationRepository = mock[OrganisationRepository]
    val mockConfig: Config                           = mock[Config]

    val inTest = new VendorIdService(mockOrganisationRepo, mockConfig)

    val expectedStartingVendorIdValue = 9001
    when(mockConfig.startingVendorId).thenReturn(expectedStartingVendorIdValue)

    val configStartingVendorId = VendorId(expectedStartingVendorIdValue)

    val vendorId4001 = VendorId(4001)

    val organisationWithVendorId4001 = Organisation(organisationId = XmlOrganisationId(UUID.randomUUID()), vendorId = vendorId4001, name = OrganisationName("Organisation Name"))
  }

  "getNextVendorId" should {
    "return maxVendorId from repo + 1" in new Setup {
      val vendorId = 9500
      when(mockOrganisationRepo.findOrgWithMaxVendorIdInRange(eqTo(9000), eqTo(9999))).thenReturn(Future.successful(Some(VendorId(vendorId))))

      val result = await(inTest.getNextVendorId())
      result shouldBe VendorId(vendorId + 1)

      verify(mockOrganisationRepo).findOrgWithMaxVendorIdInRange(*, *)
    }

    "return from subsequent range if first is full and second is empty" in new Setup {
      when(mockOrganisationRepo.findOrgWithMaxVendorIdInRange(eqTo(9000), eqTo(9999))).thenReturn(Future.successful(Some(VendorId(9999))))
      when(mockOrganisationRepo.findOrgWithMaxVendorIdInRange(eqTo(2500L), eqTo(2999))).thenReturn(Future.successful(None))

      val result = await(inTest.getNextVendorId())
      result shouldBe VendorId(2500L)

      verify(mockOrganisationRepo, times(2)).findOrgWithMaxVendorIdInRange(*, *)
    }

    "return maxVendorId from repo + 1 from subsequent range if first is full" in new Setup {
      when(mockOrganisationRepo.findOrgWithMaxVendorIdInRange(eqTo(9000), eqTo(9999))).thenReturn(Future.successful(Some(VendorId(9999))))
      when(mockOrganisationRepo.findOrgWithMaxVendorIdInRange(eqTo(2500L), eqTo(2999))).thenReturn(Future.successful(Some(VendorId(2505))))

      val result = await(inTest.getNextVendorId())
      result shouldBe VendorId(2506)

      verify(mockOrganisationRepo, times(2)).findOrgWithMaxVendorIdInRange(*, *)
    }

    "return start of range value when no vendorId is found in range" in new Setup {
      val start = 9000
      when(mockOrganisationRepo.findOrgWithMaxVendorIdInRange(eqTo(start), eqTo(9999))).thenReturn(Future.successful(None))

      val result = await(inTest.getNextVendorId())
      result.value shouldBe start

      verify(mockOrganisationRepo).findOrgWithMaxVendorIdInRange(*, *)
    }

    "return exception when repo returns Exception" in new Setup {
      val exception = new RuntimeException("some error")
      when(mockOrganisationRepo.findOrgWithMaxVendorIdInRange(eqTo(9000), eqTo(9999))).thenReturn(Future.failed(exception))

      intercept[Exception] {
        await(inTest.getNextVendorId())
      } shouldBe exception

      verify(mockOrganisationRepo).findOrgWithMaxVendorIdInRange(*, *)

    }
  }
}
