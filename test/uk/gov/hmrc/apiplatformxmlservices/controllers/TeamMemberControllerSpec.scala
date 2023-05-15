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

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.mockito.scalatest.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}

import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators.{
  AddCollaboratorRequest,
  GetOrCreateUserFailedResult,
  GetOrganisationFailedResult,
  OrganisationAlreadyHasCollaboratorResult,
  UpdateCollaboratorFailedResult
}
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.CoreUserDetail
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.{BulkUploadOrganisationsRequest, CSVJsonFormats}
import uk.gov.hmrc.apiplatformxmlservices.service.TeamMemberService

class TeamMemberControllerSpec extends AnyWordSpec with Matchers with MockitoSugar
    with GuiceOneAppPerSuite with BeforeAndAfterEach with JsonFormatters with CSVJsonFormats {

  private val mockTeamMemberService = mock[TeamMemberService]

  private val controller = new TeamMemberController(
    mockTeamMemberService,
    Helpers.stubControllerComponents()
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTeamMemberService)
  }

  trait Setup {
    val firstName = "bob"
    val lastName  = "hope"

    val createOrganisationRequest: CreateOrganisationRequest =
      CreateOrganisationRequest(organisationName = OrganisationName("Organisation Name"), "some@email.com", firstName, lastName)

    val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/organisations")
    val createRequest: FakeRequest[JsValue]              = FakeRequest("POST", "/organisations").withBody(Json.toJson(createOrganisationRequest))

    val jsonMediaType = "application/json"

    def getUuid: UUID = UUID.randomUUID()

    val organisationId: OrganisationId = OrganisationId(getUuid)
    val organisation: Organisation     = Organisation(organisationId, vendorId = VendorId(2001), name = OrganisationName("Organisation Name"))
    val userId: UserId                 = UserId(UUID.randomUUID())
    val email: String                  = "foo@bar.com"

    val coreUserDetail: CoreUserDetail                                        = CoreUserDetail(userId, email)
    val addCollaboratorRequestObj: AddCollaboratorRequest                     = AddCollaboratorRequest(email, firstName, lastName)
    val updatedOrganisationName: OrganisationName                             = OrganisationName("updated name")
    val updateOrganisationDetailsRequestObj: UpdateOrganisationDetailsRequest = UpdateOrganisationDetailsRequest(updatedOrganisationName)
    val organisationWithCollaborator: Organisation                            = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(userId, email))

    val addCollaboratorRequest: FakeRequest[JsValue] =
      FakeRequest("POST", s"/organisations/${organisation.organisationId.value.toString}/collaborator").withBody(Json.toJson(addCollaboratorRequestObj))

    val updateOrganisationDetailsRequest: FakeRequest[JsValue] =
      FakeRequest("POST", s"/organisations/${organisationId.value.toString}").withBody(Json.toJson(updateOrganisationDetailsRequestObj))

    val orgOne: OrganisationWithNameAndVendorId                             = OrganisationWithNameAndVendorId(name = OrganisationName("OrgOne"), vendorId = VendorId(1))
    val orgTwo: OrganisationWithNameAndVendorId                             = OrganisationWithNameAndVendorId(name = OrganisationName("OrgTwo"), vendorId = VendorId(2))
    val bulkFindAndCreateOrUpdateRequestObj: BulkUploadOrganisationsRequest = BulkUploadOrganisationsRequest(Seq(orgOne, orgTwo))

    val bulkFindAndCreateOrUpdateRequest: FakeRequest[JsValue] =
      FakeRequest("POST", s"/organisations/bulk").withBody(Json.toJson(bulkFindAndCreateOrUpdateRequestObj))

  }

  "TeamMemberController" when {

    "addCollaborator" should {

      "return 404 when fail to get organisation" in new Setup {
        when(mockTeamMemberService.addCollaborator(*[OrganisationId], *, *, *)(*))
          .thenReturn(Future.successful(Left(GetOrganisationFailedResult("Organisation does not exist"))))

        val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratorRequest)
        status(result) shouldBe Status.NOT_FOUND
        contentAsString(result) shouldBe "Organisation does not exist"
      }

      "return 400 when service returns collaborator already added" in new Setup {
        when(mockTeamMemberService.addCollaborator(*[OrganisationId], *, *, *)(*))
          .thenReturn(Future.successful(Left(OrganisationAlreadyHasCollaboratorResult("some error"))))
        val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratorRequest)
        status(result) shouldBe Status.BAD_REQUEST
        contentAsString(result) shouldBe "Organisation Already Has Collaborator"
      }

      "return 400 when fail to get or create user" in new Setup {
        when(mockTeamMemberService.addCollaborator(*[OrganisationId], *, *, *)(*)).thenReturn(Future.successful(Left(GetOrCreateUserFailedResult("Could not find or create user"))))
        val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratorRequest)
        status(result) shouldBe Status.BAD_REQUEST
        contentAsString(result) shouldBe "Could not find or create user"
      }

      "return 500 when fail to update organisation" in new Setup {
        when(mockTeamMemberService.addCollaborator(*[OrganisationId], *, *, *)(*))
          .thenReturn(Future.successful(Left(UpdateCollaboratorFailedResult("Organisation does not exist"))))

        val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratorRequest)
        status(result) shouldBe Status.INTERNAL_SERVER_ERROR
        contentAsString(result) shouldBe "Organisation does not exist"
      }

      "return 200 when collaborator added" in new Setup {
        when(mockTeamMemberService.addCollaborator(*[OrganisationId], *, *, *)(*))
          .thenReturn(Future.successful(Right(organisationWithCollaborator)))

        val result: Future[Result] = controller.addCollaborator(organisation.organisationId)(addCollaboratorRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.toJson(organisationWithCollaborator)
      }
    }

    "getOrganisationUserByOrganisationId" should {
      "return 200 with list of users when service returns a list" in new Setup {
        val organisationUser: OrganisationUser = OrganisationUser(organisationId, userId, email, firstName, lastName, List.empty)
        when(mockTeamMemberService.getOrganisationUserByOrganisationId(eqTo(organisationId))(*)).thenReturn(Future.successful(List(organisationUser)))

        val result: Future[Result] = controller.getOrganisationUserByOrganisationId(organisationId)(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.toJson(List(organisationUser))

      }
    }
  }

}
