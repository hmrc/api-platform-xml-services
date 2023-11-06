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

package uk.gov.hmrc.apiplatformxmlservices.controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatformxmlservices.common.builder.OrganisationBuilder
import uk.gov.hmrc.apiplatformxmlservices.common.data.CommonTestData
import uk.gov.hmrc.apiplatformxmlservices.common.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.CoreUserDetail
import uk.gov.hmrc.apiplatformxmlservices.service.OrganisationService
import uk.gov.hmrc.http.HeaderCarrier

class OrganisationControllerSpec extends AsyncHmrcSpec with CommonTestData with OrganisationBuilder {

  trait Setup {
    val mockOrgService = mock[OrganisationService]

    val controller = new OrganisationController(
      mockOrgService,
      Helpers.stubControllerComponents()
    )

    val createOrganisationRequest =
      CreateOrganisationRequest(organisationName = anOrganisationName, anEmailAddress, aFirstName, aLastName)

    val fakeRequest   = FakeRequest("GET", "/organisations")
    val createRequest = FakeRequest("POST", "/organisations").withBody(Json.toJson(createOrganisationRequest))

    val jsonMediaType = "application/json"

    val organisation = buildOrganisation(Some(anOrganisationId), vendorId = VendorId(2001), name = OrganisationName("Organisation Name"), List.empty)

    val coreUserDetail            = CoreUserDetail(aUserId, anEmailAddress)
    val addCollaboratorRequestObj = AddCollaboratorRequest(anEmailAddress, aFirstName, aLastName)

    val updateOrganisationDetailsRequestObj = UpdateOrganisationDetailsRequest(updatedOrgName)
    val organisationWithCollaborator        = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(aUserId, anEmailAddress))

    val addCollaboratorRequest =
      FakeRequest("POST", s"/organisations/${organisation.organisationId.value}/collaborator").withBody(Json.toJson(addCollaboratorRequestObj))

    val updateOrganisationDetailsRequest =
      FakeRequest("POST", s"/organisations/$anOrganisationId").withBody(Json.toJson(updateOrganisationDetailsRequestObj))

    val orgOne = OrganisationWithNameAndVendorId(name = anOrganisationName, vendorId = aVendorId)
    val orgTwo = OrganisationWithNameAndVendorId(name = OrganisationName("OrgTwo"), vendorId = VendorId(2))

  }

  "findByOrgId" should {
    "return 200" in new Setup {
      when(mockOrgService.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))

      val result: Future[Result] = controller.findByOrgId(anOrganisationId)(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return 404 when no results returned" in new Setup {
      when(mockOrgService.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.findByOrgId(OrganisationId.random)(fakeRequest)
      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "findByParams" should {
    "return 200" in new Setup {
      when(mockOrgService.findByVendorId(*[VendorId])).thenReturn(Future.successful(Some(organisation)))

      val result: Future[Result] = controller.findByParams(Some(organisation.vendorId), None, None, Some(OrganisationSortBy.ORGANISATION_NAME))(fakeRequest)
      status(result) shouldBe Status.OK
      verify(mockOrgService).findByVendorId(*[VendorId])
      verify(mockOrgService, times(0)).findAll(*)
    }

    "return 200 with all organisations" in new Setup {
      when(mockOrgService.findAll(None)).thenReturn(Future.successful(List(organisation)))

      val result: Future[Result] = controller.findByParams(sortBy = None)(fakeRequest)
      status(result) shouldBe Status.OK
      verify(mockOrgService, times(0)).findByVendorId(*[VendorId])
      verify(mockOrgService, times(1)).findAll(*)
    }

    "return 404 when no results returned" in new Setup {
      when(mockOrgService.findByVendorId(*[VendorId])).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.findByParams(Some(VendorId(9000)), sortBy = None)(fakeRequest)
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
      contentAsString(result) shouldBe s"Could not create Organisation with name $anOrganisationName - Duplicate ID"
    }

    "return 400" in new Setup {
      when(mockOrgService.create(any[CreateOrganisationRequest])(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreateOrganisationFailedResult("Failed")))

      val result: Future[Result] = controller.create()(createRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe s"Could not create Organisation with name $anOrganisationName - Failed"
    }
  }
  "updateOrganisationDetails" should {
    "return 200 when service returns UpdateOrganisationSuccessResult" in new Setup {
      when(mockOrgService.updateOrganisationDetails(eqTo(anOrganisationId), eqTo(updatedOrgName)))
        .thenReturn(Future.successful(UpdateOrganisationSuccessResult(organisation)))

      val result = controller.updateOrganisationDetails(organisation.organisationId)(updateOrganisationDetailsRequest)
      status(result) shouldBe Status.OK

    }

    "return 400 when organisationName contains only spaces" in new Setup {
      val invalidRequest = FakeRequest("POST", "/organisations")
        .withBody(Json.toJson(updateOrganisationDetailsRequestObj.copy(organisationName = OrganisationName("   "))))

      val result = controller.updateOrganisationDetails(organisation.organisationId)(invalidRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe "Could not update Organisation with empty name"

      verifyZeroInteractions(mockOrgService)
    }

    "return 500 when service returns UpdateOrganisationFailedResult" in new Setup {
      when(mockOrgService.updateOrganisationDetails(eqTo(anOrganisationId), eqTo(updatedOrgName)))
        .thenReturn(Future.successful(UpdateOrganisationFailedResult()))

      val result = controller.updateOrganisationDetails(organisation.organisationId)(updateOrganisationDetailsRequest)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

  }

}
