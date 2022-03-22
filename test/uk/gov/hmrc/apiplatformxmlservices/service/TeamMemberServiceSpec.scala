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
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.{CoreUserDetail, EmailPreferences, GetOrCreateUserIdRequest, ImportUserRequest, UserResponse}
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.{CreateVerifiedUserFailedResult, CreatedUserResult}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UpstreamErrorResponse}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TeamMemberServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockOrganisationRepo: OrganisationRepository = mock[OrganisationRepository]
  val mockThirdPartyDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
  val xmlApiService = new XmlApiService()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockOrganisationRepo)
    reset(mockThirdPartyDeveloperConnector)
  }

  trait Setup {
    val inTest = new TeamMemberService(mockOrganisationRepo, mockThirdPartyDeveloperConnector, xmlApiService)

    val uuid = UUID.fromString("dcc80f1e-4798-11ec-81d3-0242ac130003")
    val vendorId = VendorId(9000)

    def getUuid() = UUID.randomUUID()

    val organisationId = OrganisationId(uuid)
    val organisation = Organisation(organisationId = organisationId, vendorId = vendorId, name = OrganisationName("Organisation Name"))

    val userId1 = UserId(UUID.randomUUID())
    val userId2 = UserId(UUID.randomUUID())
    val firstName = "bob"
    val lastName = "hope"
    val emailOne = "foo@bar.com"
    val emailTwo = "anotheruser@bar.com"
    val collaboratorOne = Collaborator(userId1, emailOne)
    val collaboratorTwo = Collaborator(userId2, emailTwo)
    val collaborators = List(collaboratorOne, collaboratorTwo)
    val organisationWithCollaborators = organisation.copy(collaborators = collaborators)
    val gatekeeperUserId = "John Doe"
    val getOrCreateUserIdRequest = GetOrCreateUserIdRequest(emailOne)
    val coreUserDetail = CoreUserDetail(userId1, emailOne)

    val removeCollaboratorRequest = RemoveCollaboratorRequest(emailOne, gatekeeperUserId)

    val userResponse1 = UserResponse(emailOne, firstName + 1, lastName + 1, verified = true, userId1, emailPreferences = EmailPreferences.noPreferences)
    val userResponse2 = UserResponse(emailTwo, firstName + 2, lastName + 2, verified = true, userId1, emailPreferences = EmailPreferences.noPreferences)
  }

  "addCollaborator" should {
    "return Left when Organisation does not exist" in new Setup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(None))

      await(inTest.addCollaborator(organisationId, emailOne, firstName, lastName)) match {
        case Left(x: GetOrganisationFailedResult) => x.message shouldBe s"Failed to get organisation for Id: ${organisationId.value.toString}"
        case Right(_)                             => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verifyZeroInteractions(mockThirdPartyDeveloperConnector)

    }

    "return Left when Organisation exists but fails to get or create user" in new Setup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(emailOne, firstName, lastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreateVerifiedUserFailedResult("some error")))

      await(inTest.addCollaborator(organisationId, emailOne, firstName, lastName)) match {
        case Left(x: GetOrCreateUserFailedResult) => x.message shouldBe s"some error"
        case _                                    => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(*[ImportUserRequest])(*)

    }

    "return Left when Organisation exists but user already assigned to organisation" in new Setup {

      val organisationWithUSer = organisation.copy(collaborators = List(Collaborator(UserId(UUID.randomUUID()), emailOne)))
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithUSer)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(emailOne, firstName, lastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreatedUserResult(UserResponse(emailOne, firstName, lastName, verified = true, userId1, EmailPreferences.noPreferences))))

      await(inTest.addCollaborator(organisationId, emailOne, firstName, lastName)) match {
        case Left(_: OrganisationAlreadyHasCollaboratorResult) => succeed
        case _                                                 => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])

    }

    "return Left when Organisation exists and get User is successful but update Org fails" in new Setup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(emailOne, firstName, lastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreatedUserResult(UserResponse(emailOne, firstName, lastName, verified = true, userId1, EmailPreferences.noPreferences))))

      when(mockOrganisationRepo.addCollaboratorToOrganisation(*[OrganisationId], *)).thenReturn(Future.successful(Left(new BadRequestException("Organisation does not exist"))))

      await(inTest.addCollaborator(organisationId, emailOne, firstName, lastName)) match {
        case Left(x: UpdateCollaboratorFailedResult) => x.message shouldBe s"Organisation does not exist"
        case _                                       => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(*[ImportUserRequest])(*)
      verify(mockOrganisationRepo).addCollaboratorToOrganisation(*[OrganisationId], *)

    }

    "return Right when collaborator successfully added to organisation" in new Setup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(emailOne, firstName, lastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreatedUserResult(UserResponse(emailOne, firstName, lastName, verified = true, userId1, EmailPreferences.noPreferences))))

      val organisationWithCollaborator = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(coreUserDetail.userId, coreUserDetail.email))
      when(mockOrganisationRepo.addCollaboratorToOrganisation(*[OrganisationId], *)).thenReturn(Future.successful(Right(organisationWithCollaborator)))

      await(inTest.addCollaborator(organisationId, emailOne, firstName, lastName)) match {
        case Right(organisation: Organisation) => organisation.collaborators should contain only collaboratorOne
        case _                                 => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(*[ImportUserRequest])(*)
      verify(mockOrganisationRepo).addCollaboratorToOrganisation(*[OrganisationId], *)

    }

    "removeCollaborator" should {
      "return Left when Organisation does not exist" in new Setup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(None))

        await(inTest.removeCollaborator(organisationId, removeCollaboratorRequest)) match {
          case Left(x: GetOrganisationFailedResult) => x.message shouldBe s"Failed to get organisation for Id: ${organisationId.value.toString}"
          case _                                    => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verifyZeroInteractions(mockThirdPartyDeveloperConnector)

      }

      "return Left collaborator does not exist on Organisation" in new Setup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))

        await(inTest.removeCollaborator(organisationId, removeCollaboratorRequest)) match {
          case Left(x: ValidateCollaboratorFailureResult) => x.message shouldBe s"Collaborator not found on Organisation"
          case _                                          => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verifyZeroInteractions(mockThirdPartyDeveloperConnector)
      }

      "return Left update organisation fails" in new Setup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithCollaborators)))
        when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Left(new BadRequestException("Organisation does not exist"))))

        await(inTest.removeCollaborator(organisationId, removeCollaboratorRequest)) match {
          case Left(x: UpdateCollaboratorFailedResult) => x.message shouldBe s"Organisation does not exist"
          case _                                       => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verify(mockOrganisationRepo).createOrUpdate(*)

      }

      "return Right updated organisation" in new Setup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithCollaborators)))
        val updatedCollaborators = organisationWithCollaborators.collaborators.filterNot(_.email.equalsIgnoreCase(emailOne))
        val updatedOrganisation = organisationWithCollaborators.copy(collaborators = updatedCollaborators)
        when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Right(updatedOrganisation)))

        await(inTest.removeCollaborator(organisationId, removeCollaboratorRequest)) match {
          case Right(organisation: Organisation) => organisation.collaborators should contain only collaboratorTwo
          case _                                 => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verify(mockOrganisationRepo).createOrUpdate(*)

      }
    }
  }

  "handleAddCollaboratorToOrgByVendorId" should {

    "return Right when Organisation exists and get User is successful and update Org is successful" in new Setup {
      val organisationWithCollaborator = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(coreUserDetail.userId, coreUserDetail.email))
      when(mockOrganisationRepo.addCollaboratorByVendorId(*[VendorId], *)).thenReturn(Future.successful(Right(organisationWithCollaborator)))

      await(inTest.handleAddCollaboratorToOrgByVendorId(emailOne, userId1, vendorId)) match {
        case Right(org: Organisation) => org.collaborators should contain only collaboratorOne
        case _                        => fail
      }

      verify(mockOrganisationRepo).addCollaboratorByVendorId(*[VendorId], *)

    }

    "return Left when Organisation exists and get User is successful but update Org fails" in new Setup {
      when(mockOrganisationRepo.addCollaboratorByVendorId(*[VendorId], *)).thenReturn(Future.successful(Left(new BadRequestException("Organisation does not exist"))))

      await(inTest.handleAddCollaboratorToOrgByVendorId(emailOne, userId1, vendorId)) match {
        case Left(x: UpdateCollaboratorFailedResult) => x.message shouldBe s"Organisation does not exist"
        case _                                       => fail
      }

      verify(mockOrganisationRepo).addCollaboratorByVendorId(*[VendorId], *)

    }
  }

  "getOrganisationUserByOrganisationId" should {

    "return converted Organisation Users when organisation exists and users are retrieved" in new Setup {

      when(mockOrganisationRepo.findByOrgId(eqTo(organisationId))).thenReturn(Future.successful(Some(organisationWithCollaborators)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailOne)))(*)).thenReturn(Future.successful(Right(List(userResponse1))))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailTwo)))(*)).thenReturn(Future.successful(Right(List(userResponse2))))

      val result = await(inTest.getOrganisationUserByOrganisationId(organisationId))
      result should contain only (OrganisationUser(organisationId, userResponse1.userId, userResponse1.email, userResponse1.firstName, userResponse1.lastName, xmlApis = Nil),
      OrganisationUser(organisationId, userResponse2.userId, userResponse2.email, userResponse2.firstName, userResponse2.lastName, xmlApis = Nil))
    }

    "return empty list when organisation not found" in new Setup {
      when(mockOrganisationRepo.findByOrgId(eqTo(organisationId))).thenReturn(Future.successful(None))

      await(inTest.getOrganisationUserByOrganisationId(organisationId)) shouldBe Nil

    }

    "return empty list when no results returned from third party developer" in new Setup {

      when(mockOrganisationRepo.findByOrgId(eqTo(organisationId))).thenReturn(Future.successful(Some(organisationWithCollaborators)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailOne)))(*)).thenReturn(Future.successful(Right(List.empty)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailTwo)))(*)).thenReturn(Future.successful(Right(List.empty)))

      await(inTest.getOrganisationUserByOrganisationId(organisationId)) shouldBe Nil

    }

    "return empty list when no results returned from third party developer and no collaborators linked to organisation" in new Setup {

      when(mockOrganisationRepo.findByOrgId(eqTo(organisationId))).thenReturn(Future.successful(Some(organisation)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailOne)))(*)).thenReturn(Future.successful(Right(List.empty)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailTwo)))(*)).thenReturn(Future.successful(Right(List.empty)))

      await(inTest.getOrganisationUserByOrganisationId(organisationId)) shouldBe Nil

    }

    "return empty list when errors returned from third party developer" in new Setup {

      when(mockOrganisationRepo.findByOrgId(eqTo(organisationId))).thenReturn(Future.successful(Some(organisationWithCollaborators)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailOne)))(*)).thenReturn(Future.successful(Left(UpstreamErrorResponse(
        "",
        INTERNAL_SERVER_ERROR,
        INTERNAL_SERVER_ERROR
      ))))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailTwo)))(*)).thenReturn(Future.successful(Right(List.empty)))

      await(inTest.getOrganisationUserByOrganisationId(organisationId)) shouldBe Nil

    }

  }

}
