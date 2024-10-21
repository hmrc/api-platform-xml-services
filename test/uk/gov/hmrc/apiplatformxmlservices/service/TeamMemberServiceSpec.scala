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

import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatformxmlservices.common.data.CommonTestData
import uk.gov.hmrc.apiplatformxmlservices.common.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper._
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository

class TeamMemberServiceSpec extends AsyncHmrcSpec {

  trait Setup extends CommonTestData {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockOrganisationRepo: OrganisationRepository                   = mock[OrganisationRepository]
    val mockThirdPartyDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
    val xmlApiService                                                  = new XmlApiService()
    val inTest                                                         = new TeamMemberService(mockOrganisationRepo, mockThirdPartyDeveloperConnector, xmlApiService)

    val userId2 = UserId.random

    val emailTwo = LaxEmailAddress("anotheruser@bar.com")

    val collaboratorTwo               = Collaborator(userId2, emailTwo)
    val collaborators                 = List(aCollaborator, collaboratorTwo)
    val organisationWithCollaborators = anOrganisation.copy(collaborators = collaborators)
    val gatekeeperUserId              = "John Doe"
    val getOrCreateUserIdRequest      = GetOrCreateUserIdRequest(anEmailAddress)

    val removeCollaboratorRequest = RemoveCollaboratorRequest(anEmailAddress, gatekeeperUserId)

    val userResponse1 = UserResponse(anEmailAddress, aFirstName + 1, aLastName + 1, verified = true, aUserId, emailPreferences = EmailPreferences.noPreferences)
    val userResponse2 = UserResponse(emailTwo, aFirstName + 2, aLastName + 2, verified = true, aUserId, emailPreferences = EmailPreferences.noPreferences)
  }

  "addCollaborator" should {
    "return Left when Organisation does not exist" in new Setup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(None))

      await(inTest.addCollaborator(anOrganisationId, anEmailAddress, aFirstName, aLastName)) match {
        case Left(x: GetOrganisationFailedResult) => x.message shouldBe s"Failed to get organisation for Id: $anOrganisationId"
        case Right(_)                             => fail()
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verifyZeroInteractions(mockThirdPartyDeveloperConnector)

    }

    "return Left when Organisation exists but fails to get or create user" in new Setup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(anOrganisation)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(CreateUserRequest(anEmailAddress, aFirstName, aLastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreateVerifiedUserFailedResult("some error")))

      await(inTest.addCollaborator(anOrganisationId, anEmailAddress, aFirstName, aLastName)) match {
        case Left(x: GetOrCreateUserFailedResult) => x.message shouldBe s"some error"
        case _                                    => fail()
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(*[CreateUserRequest])(*)

    }

    "return Left when Organisation exists but user already assigned to organisation" in new Setup {

      val organisationWithUSer = anOrganisation.copy(collaborators = List(Collaborator(UserId(UUID.randomUUID()), anEmailAddress)))
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithUSer)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(CreateUserRequest(anEmailAddress, aFirstName, aLastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreatedUserResult(UserResponse(anEmailAddress, aFirstName, aLastName, verified = true, aUserId, EmailPreferences.noPreferences))))

      await(inTest.addCollaborator(anOrganisationId, anEmailAddress, aFirstName, aLastName)) match {
        case Left(_: OrganisationAlreadyHasCollaboratorResult) => succeed
        case _                                                 => fail()
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])

    }

    "return Left when Organisation exists and get User is successful but update Org fails" in new Setup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(anOrganisation)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(CreateUserRequest(anEmailAddress, aFirstName, aLastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreatedUserResult(UserResponse(anEmailAddress, aFirstName, aLastName, verified = true, aUserId, EmailPreferences.noPreferences))))

      when(mockOrganisationRepo.addCollaboratorToOrganisation(*[OrganisationId], *)).thenReturn(Future.successful(Left(new BadRequestException("Organisation does not exist"))))

      await(inTest.addCollaborator(anOrganisationId, anEmailAddress, aFirstName, aLastName)) match {
        case Left(x: UpdateCollaboratorFailedResult) => x.message shouldBe s"Organisation does not exist"
        case _                                       => fail()
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(*[CreateUserRequest])(*)
      verify(mockOrganisationRepo).addCollaboratorToOrganisation(*[OrganisationId], *)

    }

    "return Right when collaborator successfully added to organisation" in new Setup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(anOrganisation)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(CreateUserRequest(anEmailAddress, aFirstName, aLastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreatedUserResult(UserResponse(anEmailAddress, aFirstName, aLastName, verified = true, aUserId, EmailPreferences.noPreferences))))

      val organisationWithCollaborator = anOrganisation.copy(collaborators = List(aCollaborator))
      when(mockOrganisationRepo.addCollaboratorToOrganisation(*[OrganisationId], *)).thenReturn(Future.successful(Right(organisationWithCollaborator)))

      await(inTest.addCollaborator(anOrganisationId, anEmailAddress, aFirstName, aLastName)) match {
        case Right(organisation: Organisation) => organisation.collaborators should contain only aCollaborator
        case _                                 => fail()
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(*[CreateUserRequest])(*)
      verify(mockOrganisationRepo).addCollaboratorToOrganisation(*[OrganisationId], *)

    }

    "removeCollaborator" should {
      "return Left when Organisation does not exist" in new Setup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(None))

        await(inTest.removeCollaborator(anOrganisationId, removeCollaboratorRequest)) match {
          case Left(x: GetOrganisationFailedResult) => x.message shouldBe s"Failed to get organisation for Id: $anOrganisationId"
          case _                                    => fail()
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verifyZeroInteractions(mockThirdPartyDeveloperConnector)

      }

      "return Left collaborator does not exist on Organisation" in new Setup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(anOrganisation)))

        await(inTest.removeCollaborator(anOrganisationId, removeCollaboratorRequest)) match {
          case Left(x: ValidateCollaboratorFailureResult) => x.message shouldBe s"Collaborator not found on Organisation"
          case _                                          => fail()
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verifyZeroInteractions(mockThirdPartyDeveloperConnector)
      }

      "return Left update organisation fails" in new Setup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithCollaborators)))
        when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Left(new BadRequestException("Organisation does not exist"))))

        await(inTest.removeCollaborator(anOrganisationId, removeCollaboratorRequest)) match {
          case Left(x: UpdateCollaboratorFailedResult) => x.message shouldBe s"Organisation does not exist"
          case _                                       => fail()
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verify(mockOrganisationRepo).createOrUpdate(*)

      }

      "return Right updated organisation" in new Setup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithCollaborators)))
        val updatedCollaborators = organisationWithCollaborators.collaborators.filterNot(_.email == anEmailAddress)
        val updatedOrganisation  = organisationWithCollaborators.copy(collaborators = updatedCollaborators)
        when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Right(updatedOrganisation)))

        await(inTest.removeCollaborator(anOrganisationId, removeCollaboratorRequest)) match {
          case Right(organisation: Organisation) => organisation.collaborators should contain only collaboratorTwo
          case _                                 => fail()
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verify(mockOrganisationRepo).createOrUpdate(*)

      }
    }
  }

  "handleAddCollaboratorToOrgByVendorId" should {

    "return Right when Organisation exists and get User is successful and update Org is successful" in new Setup {
      val organisationWithCollaborator = anOrganisation.copy(collaborators = anOrganisation.collaborators :+ aCollaborator)
      when(mockOrganisationRepo.addCollaboratorByVendorId(*[VendorId], *)).thenReturn(Future.successful(Right(organisationWithCollaborator)))

      await(inTest.handleAddCollaboratorToOrgByVendorId(anEmailAddress, aUserId, aVendorId)) match {
        case Right(org: Organisation) => org.collaborators should contain only aCollaborator
        case _                        => fail()
      }

      verify(mockOrganisationRepo).addCollaboratorByVendorId(*[VendorId], *)

    }

    "return Left when Organisation exists and get User is successful but update Org fails" in new Setup {
      when(mockOrganisationRepo.addCollaboratorByVendorId(*[VendorId], *)).thenReturn(Future.successful(Left(new BadRequestException("Organisation does not exist"))))

      await(inTest.handleAddCollaboratorToOrgByVendorId(anEmailAddress, aUserId, aVendorId)) match {
        case Left(x: UpdateCollaboratorFailedResult) => x.message shouldBe s"Organisation does not exist"
        case _                                       => fail()
      }

      verify(mockOrganisationRepo).addCollaboratorByVendorId(*[VendorId], *)

    }
  }

  "getOrganisationUserByOrganisationId" should {

    "return converted Organisation Users when organisation exists and users are retrieved" in new Setup {

      when(mockOrganisationRepo.findByOrgId(eqTo(anOrganisationId))).thenReturn(Future.successful(Some(organisationWithCollaborators)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(anEmailAddress)))(*)).thenReturn(Future.successful(Right(List(userResponse1))))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailTwo)))(*)).thenReturn(Future.successful(Right(List(userResponse2))))

      val result = await(inTest.getOrganisationUserByOrganisationId(anOrganisationId))
      result should contain only (List(
        OrganisationUser(anOrganisationId, Some(userResponse1.userId), userResponse1.email, userResponse1.firstName, userResponse1.lastName, xmlApis = Nil),
        OrganisationUser(anOrganisationId, Some(userResponse2.userId), userResponse2.email, userResponse2.firstName, userResponse2.lastName, xmlApis = Nil)
      ): _*)
    }

    "return converted Organisation Users when organisation exists and no users are retrieved from TPD" in new Setup {

      when(mockOrganisationRepo.findByOrgId(eqTo(anOrganisationId))).thenReturn(Future.successful(Some(organisationWithCollaborators)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(anEmailAddress)))(*)).thenReturn(Future.successful(Right(List.empty)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailTwo)))(*)).thenReturn(Future.successful(Right(List.empty)))

      val result = await(inTest.getOrganisationUserByOrganisationId(anOrganisationId))
      result should contain only (List(
        OrganisationUser(anOrganisationId, None, anEmailAddress, "", "", xmlApis = Nil),
        OrganisationUser(anOrganisationId, None, emailTwo, "", "", xmlApis = Nil)
      ): _*)
    }

    "return empty list when organisation not found" in new Setup {
      when(mockOrganisationRepo.findByOrgId(eqTo(anOrganisationId))).thenReturn(Future.successful(None))

      await(inTest.getOrganisationUserByOrganisationId(anOrganisationId)) shouldBe Nil

    }

    "return empty list when no results returned from third party developer and no collaborators linked to organisation" in new Setup {

      when(mockOrganisationRepo.findByOrgId(eqTo(anOrganisationId))).thenReturn(Future.successful(Some(anOrganisation)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(anEmailAddress)))(*)).thenReturn(Future.successful(Right(List.empty)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailTwo)))(*)).thenReturn(Future.successful(Right(List.empty)))

      await(inTest.getOrganisationUserByOrganisationId(anOrganisationId)) shouldBe Nil

    }

    "return empty list when errors returned from third party developer" in new Setup {

      when(mockOrganisationRepo.findByOrgId(eqTo(anOrganisationId))).thenReturn(Future.successful(Some(organisationWithCollaborators)))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(anEmailAddress)))(*)).thenReturn(Future.successful(Left(UpstreamErrorResponse(
        "",
        INTERNAL_SERVER_ERROR,
        INTERNAL_SERVER_ERROR
      ))))
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailTwo)))(*)).thenReturn(Future.successful(Left(UpstreamErrorResponse(
        "",
        INTERNAL_SERVER_ERROR,
        INTERNAL_SERVER_ERROR
      ))))

      await(inTest.getOrganisationUserByOrganisationId(anOrganisationId)) shouldBe Nil

    }
  }

  "removeAllCollaboratorsForUserId" should {

    "return a list of UpdateOrganisationSuccessResult when successful for one organisation" in new Setup {
      when(mockOrganisationRepo.findByUserId(eqTo(aUserId))).thenReturn(Future.successful(List(organisationWithCollaborators)))
      when(mockOrganisationRepo.removeCollaboratorFromOrganisation(eqTo(organisationWithCollaborators.organisationId), eqTo(aUserId))).thenReturn(
        Future.successful(UpdateOrganisationSuccessResult(organisationWithCollaborators))
      )

      val result = await(inTest.removeAllCollaboratorsForUserId(RemoveAllCollaboratorsForUserIdRequest(aUserId, gatekeeperUserId)))
      result shouldBe List(UpdateOrganisationSuccessResult(organisationWithCollaborators))

      verify(mockOrganisationRepo).removeCollaboratorFromOrganisation(eqTo(organisationWithCollaborators.organisationId), eqTo(aUserId))
    }

    "return a list of UpdateOrganisationSuccessResult when successful for three organisations" in new Setup {
      val collaboratorThree              = Collaborator(UserId.random, LaxEmailAddress("emailThree@example.com"))
      val organisationWithCollaborators2 = anOrganisation.copy(organisationId = OrganisationId.random, collaborators = List(aCollaborator, collaboratorThree))
      val organisationWithCollaborators3 = anOrganisation.copy(organisationId = OrganisationId.random, collaborators = List(collaboratorTwo, collaboratorThree))

      when(mockOrganisationRepo.findByUserId(eqTo(aUserId))).thenReturn(Future.successful(List(
        organisationWithCollaborators,
        organisationWithCollaborators2,
        organisationWithCollaborators3
      )))
      when(mockOrganisationRepo.removeCollaboratorFromOrganisation(eqTo(organisationWithCollaborators.organisationId), eqTo(aUserId))).thenReturn(
        Future.successful(UpdateOrganisationSuccessResult(organisationWithCollaborators))
      )
      when(mockOrganisationRepo.removeCollaboratorFromOrganisation(eqTo(organisationWithCollaborators2.organisationId), eqTo(aUserId))).thenReturn(
        Future.successful(UpdateOrganisationSuccessResult(organisationWithCollaborators2))
      )
      when(mockOrganisationRepo.removeCollaboratorFromOrganisation(eqTo(organisationWithCollaborators3.organisationId), eqTo(aUserId))).thenReturn(
        Future.successful(UpdateOrganisationSuccessResult(organisationWithCollaborators3))
      )

      val result = await(inTest.removeAllCollaboratorsForUserId(RemoveAllCollaboratorsForUserIdRequest(aUserId, gatekeeperUserId)))
      result shouldBe List(
        UpdateOrganisationSuccessResult(organisationWithCollaborators),
        UpdateOrganisationSuccessResult(organisationWithCollaborators2),
        UpdateOrganisationSuccessResult(organisationWithCollaborators3)
      )

      verify(mockOrganisationRepo).removeCollaboratorFromOrganisation(eqTo(organisationWithCollaborators.organisationId), eqTo(aUserId))
      verify(mockOrganisationRepo).removeCollaboratorFromOrganisation(eqTo(organisationWithCollaborators2.organisationId), eqTo(aUserId))
      verify(mockOrganisationRepo).removeCollaboratorFromOrganisation(eqTo(organisationWithCollaborators3.organisationId), eqTo(aUserId))
    }

    "return a list of UpdateOrganisationSuccessResult when successful no organisations" in new Setup {
      when(mockOrganisationRepo.findByUserId(eqTo(aUserId))).thenReturn(Future.successful(List.empty))

      val result = await(inTest.removeAllCollaboratorsForUserId(RemoveAllCollaboratorsForUserIdRequest(aUserId, gatekeeperUserId)))
      result shouldBe List.empty

      verify(mockOrganisationRepo, never).removeCollaboratorFromOrganisation(*[OrganisationId], *[UserId])
    }

    "return a failure" in new Setup {
      when(mockOrganisationRepo.findByUserId(eqTo(aUserId))).thenReturn(Future.successful(List(organisationWithCollaborators)))
      when(mockOrganisationRepo.removeCollaboratorFromOrganisation(eqTo(organisationWithCollaborators.organisationId), eqTo(aUserId))).thenReturn(
        Future.successful(UpdateOrganisationFailedResult())
      )

      val result = await(inTest.removeAllCollaboratorsForUserId(RemoveAllCollaboratorsForUserIdRequest(aUserId, gatekeeperUserId)))
      result shouldBe List(UpdateOrganisationFailedResult())
    }
  }
}
