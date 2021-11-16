/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.scalatest.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.apiplatformxmlservices.models.{Organisation, OrganisationId, VendorId}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository

import java.util.UUID
import scala.concurrent.Future

class OrganisationServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  val mockOrganisationRepo: OrganisationRepository = mock[OrganisationRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockOrganisationRepo)
  }

  trait Setup {
    val inTest = new OrganisationService(mockOrganisationRepo)

    def getUuid() = UUID.randomUUID()
    val organisationToPersist = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(20001), name = "Organisation Name")
  }

  "createOrganisation" should {
    "return Right" in new Setup {
      when(mockOrganisationRepo.create(*)).thenReturn(Future.successful(Right(true)))

      val result = await(inTest.create(organisationToPersist))
      result match {
        case Left(e: Exception) => fail
        case Right(x: Boolean)  => x shouldBe true
      }

      verify(mockOrganisationRepo).create(organisationToPersist)
    }
  }

  "update" should {
    "return true when update successful" in new Setup {
      when(mockOrganisationRepo.update(*)).thenReturn(Future.successful(true))
      val updatedOrganisation = organisationToPersist.copy(name = "New organisation name")

      val result = await(inTest.update(updatedOrganisation))
      result shouldBe true

      verify(mockOrganisationRepo).update(updatedOrganisation)

    }
  }

  "deleteByOrgId" should {
    "return an true when delete successful" in new Setup {
      when(mockOrganisationRepo.deleteByOrgId(*[OrganisationId])).thenReturn(Future.successful(true))

      val result = await(inTest.deleteByOrgId(organisationToPersist.organisationId))
      result shouldBe true

      verify(mockOrganisationRepo).deleteByOrgId(organisationToPersist.organisationId)

    }
  }

  "findByOrgId" should {
    "return an Organisation when organisationId exists" in new Setup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationToPersist)))

      val result = await(inTest.findByOrgId(organisationToPersist.organisationId))
      result shouldBe Some(organisationToPersist)

      verify(mockOrganisationRepo).findByOrgId(organisationToPersist.organisationId)

    }
  }

  "findByVendorId" should {
    "return an Organisation when vendorId exists" in new Setup {
      when(mockOrganisationRepo.findByVendorId(*[VendorId])).thenReturn(Future.successful(Some(organisationToPersist)))

      val result = await(inTest.findByVendorId(organisationToPersist.vendorId))
      result shouldBe Some(organisationToPersist)

      verify(mockOrganisationRepo).findByVendorId(organisationToPersist.vendorId)

    }
  }


}
