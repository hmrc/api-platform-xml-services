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

import org.mockito.scalatest.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.apiplatformxmlservices.models.{Organisation, OrganisationId, VendorId}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID
import scala.concurrent.Future
import uk.gov.hmrc.apiplatformxmlservices.models.OrganisationName
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatformxmlservices.models.UserId
import uk.gov.hmrc.apiplatformxmlservices.models.UserIdResponse
import uk.gov.hmrc.apiplatformxmlservices.models.GetOrCreateUserIdRequest
import uk.gov.hmrc.apiplatformxmlservices.models.CoreUserDetail

class OrganisationServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockOrganisationRepo: OrganisationRepository = mock[OrganisationRepository]
  val mockUuidService: UuidService = mock[UuidService]
  val mockVendorIdService: VendorIdService = mock[VendorIdService]
  val mockThirdPartyDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockOrganisationRepo)
    reset(mockUuidService)
    reset(mockVendorIdService)
    reset(mockThirdPartyDeveloperConnector)
  }

  trait Setup {
    val inTest = new OrganisationService(mockOrganisationRepo, mockUuidService, mockVendorIdService, mockThirdPartyDeveloperConnector)

    val uuid = UUID.fromString("dcc80f1e-4798-11ec-81d3-0242ac130003")
    val vendorId = VendorId(9000)

    def getUuid() = UUID.randomUUID()
    val organisationToPersist = Organisation(organisationId = OrganisationId(uuid), vendorId = vendorId, name =  OrganisationName("Organisation Name"))

  }

  "createOrganisation" should {
    "return Right when vendorIdService returns a vendorId" in new Setup {
      when(mockUuidService.newUuid).thenReturn(uuid)
      when(mockVendorIdService.getNextVendorId).thenReturn(Future.successful(Some(vendorId)))
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Right(organisationToPersist)))

      await(inTest.create(organisationToPersist.name)) match {
        case Left(e: Exception)     => fail
        case Right(x: Organisation) => x shouldBe organisationToPersist
      }

      verify(mockUuidService).newUuid
      verify(mockVendorIdService).getNextVendorId
      verify(mockOrganisationRepo).createOrUpdate(organisationToPersist)
    }

    "return Left when vendorIdService does not return a vendorId" in new Setup {
      when(mockVendorIdService.getNextVendorId).thenReturn(Future.successful(None))

      await(inTest.create(organisationToPersist.name)) match {
        case Left(e: Exception) => e.getMessage shouldBe "Could not get max vendorId"
        case Right(_)           => fail
      }

      verify(mockVendorIdService).getNextVendorId
      verifyZeroInteractions(mockUuidService)
      verifyZeroInteractions(mockOrganisationRepo)
    }
  }

  "update" should {
    "return true when update successful" in new Setup {
      when(mockOrganisationRepo.update(*)).thenReturn(Future.successful(Right(true)))
      val updatedOrganisation = organisationToPersist.copy(name =  OrganisationName("New Organisation Name"))

      val result = await(inTest.update(updatedOrganisation))
      result shouldBe Right(true)

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

  "findAll" should {
    "return a List of Organisations when at least one exists" in new Setup {
      when(mockOrganisationRepo.findAll()).thenReturn(Future.successful(List(organisationToPersist)))

      val result = await(inTest.findAll)
      result shouldBe List(organisationToPersist)

      verify(mockOrganisationRepo).findAll

    }
  }

  "findByOrganisationName" should {
    "return a List of Organisations when at least one exists" in new Setup {
      val orgName = OrganisationName("orgname")
      when(mockOrganisationRepo.findByOrganisationName(eqTo(orgName))).thenReturn(Future.successful(List(organisationToPersist)))

      val result = await(inTest.findByOrganisationName(orgName))
      result shouldBe List(organisationToPersist)

      verify(mockOrganisationRepo).findByOrganisationName(eqTo(orgName))

    }
  }

  "getOrCreateUserId" should {
    "return UserIdResponse when successful" in new Setup {
      val userId = UserId(UUID.randomUUID())
      val email = "foo@bar.com"
      val getOrCreateUserIdRequest = GetOrCreateUserIdRequest(email)
      val coreUserDetail = CoreUserDetail(userId, email)
      when(mockThirdPartyDeveloperConnector.getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)).thenReturn(Future.successful(Right(coreUserDetail)))
      
      val result = await(inTest.getOrCreateUserId(getOrCreateUserIdRequest))
      
      result.map(u => u.userId shouldBe userId)
      verify(mockThirdPartyDeveloperConnector).getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)
    }
  }
}
