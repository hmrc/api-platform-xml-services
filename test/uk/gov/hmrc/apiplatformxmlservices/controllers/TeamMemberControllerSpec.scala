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
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress

import uk.gov.hmrc.apiplatformxmlservices.common.builder.OrganisationBuilder
import uk.gov.hmrc.apiplatformxmlservices.common.data.CommonTestData
import uk.gov.hmrc.apiplatformxmlservices.common.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.CoreUserDetail
import uk.gov.hmrc.apiplatformxmlservices.service.TeamMemberService

class TeamMemberControllerSpec extends AsyncHmrcSpec with CommonTestData with OrganisationBuilder {

  trait Setup {
    val mockTeamMemberService = mock[TeamMemberService]

    val controller = new TeamMemberController(
      mockTeamMemberService,
      Helpers.stubControllerComponents()
    )

    val createOrganisationRequest = CreateOrganisationRequest(organisationName = OrganisationName("Organisation Name"), LaxEmailAddress("some@email.com"), aFirstName, aLastName)

    val fakeRequest   = FakeRequest("GET", "/organisations")
    val createRequest = FakeRequest("POST", "/organisations").withBody(Json.toJson(createOrganisationRequest))

    val jsonMediaType = "application/json"

    val organisation = buildOrganisation(Some(anOrganisationId), vendorId = VendorId(2001), name = OrganisationName("Organisation Name"), List.empty)

    val coreUserDetail                      = CoreUserDetail(aUserId, anEmailAddress)
    val addCollaboratorRequestObj           = AddCollaboratorRequest(anEmailAddress, aFirstName, aLastName)
    val updatedOrganisationName             = OrganisationName("updated name")
    val updateOrganisationDetailsRequestObj = UpdateOrganisationDetailsRequest(updatedOrganisationName)
    val organisationWithCollaborator        = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(aUserId, anEmailAddress))

    val addCollaboratorRequest =
      FakeRequest("POST", s"/organisations/${organisation.organisationId.value}/collaborator").withBody(Json.toJson(addCollaboratorRequestObj))

    val updateOrganisationDetailsRequest =
      FakeRequest("POST", s"/organisations/${anOrganisationId.value}").withBody(Json.toJson(updateOrganisationDetailsRequestObj))

    val orgOne = OrganisationWithNameAndVendorId(name = OrganisationName("OrgOne"), vendorId = VendorId(1))
    val orgTwo = OrganisationWithNameAndVendorId(name = OrganisationName("OrgTwo"), vendorId = VendorId(2))

  }

  "TeamMemberController" when {

    "addCollaborator" should {

      "return 404 when fail to get organisation" in new Setup {
        when(mockTeamMemberService.addCollaborator(*[OrganisationId], *[LaxEmailAddress], *, *)(*))
          .thenReturn(Future.successful(Left(GetOrganisationFailedResult("Organisation does not exist"))))

        val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratorRequest)
        status(result) shouldBe Status.NOT_FOUND
        contentAsString(result) shouldBe "Organisation does not exist"
      }

      "return 400 when service returns collaborator already added" in new Setup {
        when(mockTeamMemberService.addCollaborator(*[OrganisationId], *[LaxEmailAddress], *, *)(*))
          .thenReturn(Future.successful(Left(OrganisationAlreadyHasCollaboratorResult("some error"))))
        val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratorRequest)
        status(result) shouldBe Status.BAD_REQUEST
        contentAsString(result) shouldBe "Organisation Already Has Collaborator"
      }

      "return 400 when fail to get or create user" in new Setup {
        when(mockTeamMemberService.addCollaborator(*[OrganisationId], *[LaxEmailAddress], *, *)(*)).thenReturn(
          Future.successful(Left(GetOrCreateUserFailedResult("Could not find or create user")))
        )
        val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratorRequest)
        status(result) shouldBe Status.BAD_REQUEST
        contentAsString(result) shouldBe "Could not find or create user"
      }

      "return 500 when fail to update organisation" in new Setup {
        when(mockTeamMemberService.addCollaborator(*[OrganisationId], *[LaxEmailAddress], *, *)(*))
          .thenReturn(Future.successful(Left(UpdateCollaboratorFailedResult("Organisation does not exist"))))

        val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratorRequest)
        status(result) shouldBe Status.INTERNAL_SERVER_ERROR
        contentAsString(result) shouldBe "Organisation does not exist"
      }

      "return 200 when collaborator added" in new Setup {
        when(mockTeamMemberService.addCollaborator(*[OrganisationId], *[LaxEmailAddress], *, *)(*))
          .thenReturn(Future.successful(Right(organisationWithCollaborator)))

        val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratorRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.toJson(organisationWithCollaborator)
      }
    }

    "getOrganisationUserByOrganisationId" should {
      "return 200 with list of users when service returns a list" in new Setup {
        val organisationUser = OrganisationUser(anOrganisationId, aUserId, anEmailAddress, aFirstName, aLastName, List.empty)
        when(mockTeamMemberService.getOrganisationUserByOrganisationId(eqTo(anOrganisationId))(*)).thenReturn(Future.successful(List(organisationUser)))

        val result: Future[Result] = controller.getOrganisationUserByOrganisationId(anOrganisationId)(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.toJson(List(organisationUser))

      }
    }
  }

}
