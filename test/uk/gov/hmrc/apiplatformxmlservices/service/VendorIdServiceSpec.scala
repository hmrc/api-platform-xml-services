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

import org.mockito.scalatest.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import play.api.test.Helpers.{await, defaultAwaitTimeout}

import uk.gov.hmrc.apiplatformxmlservices.models.{Organisation, OrganisationId, OrganisationName, VendorId}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.service.VendorIdService.Config

class VendorIdServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  val mockOrganisationRepo: OrganisationRepository = mock[OrganisationRepository]
  val mockConfig: Config                           = mock[Config]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockOrganisationRepo)
    reset(mockConfig)
  }

  trait Setup {
    val inTest = new VendorIdService(mockOrganisationRepo, mockConfig)

    val configStartingVendorIdValue = 9000
    when(mockConfig.startingVendorId).thenReturn(configStartingVendorIdValue)

    val configStartingVendorId           = VendorId(configStartingVendorIdValue)
    val vendorId9001                     = VendorId(9001)
    val vendorId4001                     = VendorId(4001)
    val uuid                             = UUID.fromString("dcc80f1e-4798-11ec-81d3-0242ac130003")
    val organisationWithStartingVendorId = Organisation(organisationId = OrganisationId(uuid), vendorId = configStartingVendorId, name = OrganisationName("Organisation Name"))
    val organisationWithVendorId4001     = Organisation(organisationId = OrganisationId(uuid), vendorId = vendorId4001, name = OrganisationName("Organisation Name"))

  }

  "getNextVendorId" should {
    "return (maxVendorId + 1) when max vendorId from repo is greater than or equal to config startingVendorId" in new Setup {
      when(mockOrganisationRepo.findOrgWithMaxVendorId()).thenReturn(Future.successful(Some(organisationWithStartingVendorId)))

      val result = await(inTest.getNextVendorId())
      result shouldBe Right(vendorId9001)

      verify(mockOrganisationRepo).findOrgWithMaxVendorId()

    }

    "return config startingVendorId when max vendorId from repo is less than config startingVendorId" in new Setup {
      when(mockOrganisationRepo.findOrgWithMaxVendorId()).thenReturn(Future.successful(Some(organisationWithVendorId4001)))

      val result = await(inTest.getNextVendorId())
      result shouldBe Right(configStartingVendorId)

      verify(mockOrganisationRepo).findOrgWithMaxVendorId()

    }

    "return config startingVendorId when repo returns None" in new Setup {
      when(mockOrganisationRepo.findOrgWithMaxVendorId()).thenReturn(Future.successful(None))

      val result = await(inTest.getNextVendorId())
      result shouldBe Right(configStartingVendorId)

      verify(mockOrganisationRepo).findOrgWithMaxVendorId()

    }

    "return Left with exception when repo returns Exception" in new Setup {
      val exception = new RuntimeException("some error")
      when(mockOrganisationRepo.findOrgWithMaxVendorId()).thenReturn(Future.failed(exception))

      val result = await(inTest.getNextVendorId())
      result shouldBe Left(exception)

      verify(mockOrganisationRepo).findOrgWithMaxVendorId()

    }
  }
}
