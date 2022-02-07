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

package uk.gov.hmrc.apiplatformxmlservices.controllers

import org.mockito.scalatest.MockitoSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.service.OrganisationService
import uk.gov.hmrc.apiplatformxmlservices.models.JsonFormatters._
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
class OrganisationControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfterEach {

  private val mockOrgService = mock[OrganisationService]

  private val controller = new OrganisationController(
    mockOrgService,
    Helpers.stubControllerComponents()
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockOrgService)
  }

  trait Setup {
    val createOrganisationRequest = CreateOrganisationRequest(organisationName = OrganisationName("Organisation Name"), "some@email.com")

    val fakeRequest = FakeRequest("GET", "/organisations")
    val createRequest = FakeRequest("POST", "/organisations").withBody(Json.toJson(createOrganisationRequest))

    val jsonMediaType = "application/json"
    def getUuid() = UUID.randomUUID()
    val organisationId = OrganisationId(getUuid)
    val organisation = Organisation(organisationId, vendorId = VendorId(2001), name = OrganisationName("Organisation Name"))
    val userId = UserId(UUID.randomUUID())
    val email = "foo@bar.com"
    val coreUserDetail = CoreUserDetail(userId, email)
    val addCollaboratordRequestObj = AddCollaboratorRequest(email)
    val updatedOrganisationName = OrganisationName("updated name")
    val updateOrganisationDetailsRequestObj = UpdateOrganisationDetailsRequest(updatedOrganisationName)
    val organisationWithCollaborator = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(userId, email))

    val addCollaboratordRequest =
      FakeRequest("POST", s"/organisations/${organisation.organisationId.value.toString}/collaborator").withBody(Json.toJson(addCollaboratordRequestObj))

    val updateOrganisationDetailsRequest =
      FakeRequest("POST", s"/organisations/${organisationId.value.toString}").withBody(Json.toJson(updateOrganisationDetailsRequestObj))

    val orgOne = OrganisationWithNameAndVendorId(name = OrganisationName("OrgOne"), vendorId = VendorId(1))
    val orgTwo = OrganisationWithNameAndVendorId(name = OrganisationName("OrgTwo"), vendorId = VendorId(2))
    val bulkFindAndCreateOrUpdateRequestObj = BulkUploadOrganisationsRequest(Seq(orgOne, orgTwo))

    val bulkFindAndCreateOrUpdateRequest =
      FakeRequest("POST", s"/organisations/bulk").withBody(Json.toJson(bulkFindAndCreateOrUpdateRequestObj))

  }

  "findByOrgId" should {
    "return 200" in new Setup {
      when(mockOrgService.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))

      val result: Future[Result] = controller.findByOrgId(organisationId)(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return 404 when no results returned" in new Setup {
      when(mockOrgService.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.findByOrgId(OrganisationId(getUuid))(fakeRequest)
      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "findByParams" should {
    "return 200" in new Setup {
      when(mockOrgService.findByVendorId(*[VendorId])).thenReturn(Future.successful(Some(organisation)))

      val result: Future[Result] = controller.findByParams(Some(organisation.vendorId), None, Some(OrganisationSortBy.ORGANISATION_NAME))(fakeRequest)
      status(result) shouldBe Status.OK
      verify(mockOrgService).findByVendorId(*[VendorId])
      verify(mockOrgService, times(0)).findAll(*)
    }

    "return 200 with all organisations" in new Setup {
      when(mockOrgService.findAll(None)).thenReturn(Future.successful(List(organisation)))


      val result: Future[Result] = controller.findByParams(sortBy= None)(fakeRequest)
      status(result) shouldBe Status.OK
      verify(mockOrgService, times(0)).findByVendorId(*[VendorId])
      verify(mockOrgService, times(1)).findAll(*)
    }

    "return 404 when no results returned" in new Setup {
      when(mockOrgService.findByVendorId(*[VendorId])).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.findByParams(Some(VendorId(9000)), sortBy= None)(fakeRequest)
      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "create" should {
    "return 200" in new Setup {
      when(mockOrgService.create(any[CreateOrganisationRequest])(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreateOrganisationSuccessResult(organisation)))

      val result: Future[Result] = controller.create()(createRequest)
      status(result) shouldBe Status.CREATED
      contentAsJson(result) shouldBe Json.toJson(organisation)
    }

    "return 400 when organisationName contains only spaces" in new Setup {
      val invalidCreateRequest = FakeRequest("POST", "/organisations")
        .withBody(Json.toJson(createOrganisationRequest.copy(organisationName = OrganisationName("   "))))

      val result: Future[Result] = controller.create()(invalidCreateRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe "Could not create Organisation with empty name"

      verifyZeroInteractions(mockOrgService)
    }

    "return 409" in new Setup {
      when(mockOrgService.create(any[CreateOrganisationRequest])(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreateOrganisationFailedDuplicateIdResult("some Error Message")))

      val result: Future[Result] = controller.create()(createRequest)
      status(result) shouldBe Status.CONFLICT
      contentAsString(result) shouldBe "Could not create Organisation with name OrganisationName(Organisation Name) - Duplicate ID"
    }

    "return 400" in new Setup {
      when(mockOrgService.create(any[CreateOrganisationRequest])(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreateOrganisationFailedResult("Failed")))

      val result: Future[Result] = controller.create()(createRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe "Could not create Organisation with name OrganisationName(Organisation Name) - Failed"
    }
  }
  "updateOrganisationDetails" should {
    "return 200 when service returns UpdateOrganisationSuccessResult" in new Setup {
      when(mockOrgService.updateOrganisationDetails(eqTo(organisationId), eqTo(updatedOrganisationName)))
        .thenReturn(Future.successful(UpdateOrganisationSuccessResult(organisation)))

      val result = controller.updateOrganisationDetails(organisation.organisationId)(updateOrganisationDetailsRequest)
      status(result) shouldBe Status.OK

    }

    "return 400 when organisationName contains only spaces" in new Setup {
      val invalidRequest = FakeRequest("POST", "/organisations")
        .withBody(Json.toJson(updateOrganisationDetailsRequestObj.copy(organisationName = OrganisationName("   "))))

      val result: Future[Result] = controller.updateOrganisationDetails(organisation.organisationId)(invalidRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe "Could not update Organisation with empty name"

      verifyZeroInteractions(mockOrgService)
    }

    "return 500 when service returns UpdateOrganisationFailedResult" in new Setup {
      when(mockOrgService.updateOrganisationDetails(eqTo(organisationId), eqTo(updatedOrganisationName)))
        .thenReturn(Future.successful(UpdateOrganisationFailedResult()))

      val result = controller.updateOrganisationDetails(organisation.organisationId)(updateOrganisationDetailsRequest)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

  }

  "addCollaborator" should {

    "return 404 when fail to get organisation" in new Setup {
      when(mockOrgService.addCollaborator(*[OrganisationId], *)(*))
        .thenReturn(Future.successful(Left(GetOrganisationFailedResult("Organisation does not exist"))))

      val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratordRequest)
      status(result) shouldBe Status.NOT_FOUND
      contentAsString(result) shouldBe "Organisation does not exist"
    }

    "return 400 when fail to get or create user" in new Setup {
      when(mockOrgService.addCollaborator(*[OrganisationId], *)(*)).thenReturn(Future.successful(Left(GetOrCreateUserIdFailedResult("Could not find or create user"))))
      val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratordRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe "Could not find or create user"
    }

    "return 500 when fail to update organisation" in new Setup {
      when(mockOrgService.addCollaborator(*[OrganisationId], *)(*))
        .thenReturn(Future.successful(Left(UpdateCollaboratorFailedResult("Organisation does not exist"))))

      val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratordRequest)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsString(result) shouldBe "Organisation does not exist"
    }

    "return 200 when collaborator added" in new Setup {
      when(mockOrgService.addCollaborator(*[OrganisationId], *)(*))
        .thenReturn(Future.successful(Right(organisationWithCollaborator)))

      val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratordRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.toJson(organisationWithCollaborator)
    }
  }

}
