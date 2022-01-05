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
import uk.gov.hmrc.apiplatformxmlservices.models.{
  Collaborator,
  CoreUserDetail,
  GetOrCreateUserIdFailedResult,
  GetOrCreateUserIdRequest,
  GetOrganisationFailedResult,
  Organisation,
  OrganisationId,
  OrganisationName,
  UpdateOrganisationFailedResult,
  UserId,
  UserIdResponse,
  VendorId
}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID
import scala.concurrent.Future
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, NotFoundException}
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.apiplatformxmlservices.models.ValidateCollaboratorFailureResult
import uk.gov.hmrc.apiplatformxmlservices.models.DeleteUserRequest
import uk.gov.hmrc.apiplatformxmlservices.models.DeleteUserFailureResult
import uk.gov.hmrc.apiplatformxmlservices.models.DeleteCollaboratorFailureResult
import uk.gov.hmrc.apiplatformxmlservices.models.DeleteUserSuccessResult

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
    val organisationToPersist = Organisation(organisationId = OrganisationId(uuid), vendorId = vendorId, name = OrganisationName("Organisation Name"))

  }

  trait ManageCollaboratorSetup extends Setup {
    val organisationId = OrganisationId(uuid)
    val organisation = Organisation(organisationId = organisationId, vendorId = vendorId, name = OrganisationName("Organisation Name"))

    val userId = UserId(UUID.randomUUID())
    val emailOne = "foo@bar.com"
    val emailTwo = "anotheruser@bar.com"
    val collaboratorOne = Collaborator(userId, emailOne)
    val collaboratorTwo = Collaborator(UserId(UUID.randomUUID()), emailTwo)
    val collaborators = List(collaboratorOne, collaboratorTwo)
    val organisationWithCollaborators = organisation.copy(collaborators = collaborators)
    val gatekeeperUserId = Some("John Doe")
    val getOrCreateUserIdRequest = GetOrCreateUserIdRequest(emailOne)
    val coreUserDetail = CoreUserDetail(userId, emailOne)
    val deleteUserRequest = DeleteUserRequest(gatekeeperUserId, emailOne)
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
      val updatedOrganisation = organisationToPersist.copy(name = OrganisationName("New Organisation Name"))
      when(mockOrganisationRepo.update(*)).thenReturn(Future.successful(Right(updatedOrganisation)))

      val result = await(inTest.update(updatedOrganisation))
      result shouldBe Right(updatedOrganisation)

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

  "addCollaborator" should {
    "return Left when Organisation does not exist" in new ManageCollaboratorSetup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(None))

      await(inTest.addCollaborator(organisationId, emailOne)) match {
        case Left(x: GetOrganisationFailedResult) => x.message shouldBe s"Failed to get organisation for Id: ${organisationId.value.toString}"
        case Right(_)                             => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verifyZeroInteractions(mockThirdPartyDeveloperConnector)

    }

    "return Left when Organisation exists but fails to get or create user" in new ManageCollaboratorSetup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))
      when(mockThirdPartyDeveloperConnector.getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)).thenReturn(Future.successful(Left(new InternalServerException("Not found"))))

      await(inTest.addCollaborator(organisationId, emailOne)) match {
        case Left(x: GetOrCreateUserIdFailedResult) => x.message shouldBe s"Not found"
        case Right(_)                               => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)

    }

    "return Left when Organisation exists and get User is successful but update Org fails" in new ManageCollaboratorSetup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))
      when(mockThirdPartyDeveloperConnector.getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)).thenReturn(Future.successful(Right(coreUserDetail)))
      when(mockOrganisationRepo.update(*)).thenReturn(Future.successful(Left(new BadRequestException("Organisation does not exist"))))

      await(inTest.addCollaborator(organisationId, emailOne)) match {
        case Left(x: UpdateOrganisationFailedResult) => x.message shouldBe s"Organisation does not exist"
        case Right(_)                                => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)
      verify(mockOrganisationRepo).update(*)

    }

    "return Right when collaborator successfully added to organisation" in new ManageCollaboratorSetup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))
      when(mockThirdPartyDeveloperConnector.getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)).thenReturn(Future.successful(Right(coreUserDetail)))
      val organisationWithCollaborator = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(coreUserDetail.userId, coreUserDetail.email))
      when(mockOrganisationRepo.update(*)).thenReturn(Future.successful(Right(organisationWithCollaborator)))

      await(inTest.addCollaborator(organisationId, emailOne)) match {
        case Right(organisation: Organisation) => organisation.collaborators should contain only collaboratorOne
        case Left(_) => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)
      verify(mockOrganisationRepo).update(*)

    }

    "removeCollaborator" should {
      "return Left when Organisation does not exist" in new ManageCollaboratorSetup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(None))

        await(inTest.removeCollaborator(organisationId, gatekeeperUserId, emailOne)) match {
          case Left(x: GetOrganisationFailedResult) => x.message shouldBe s"Failed to get organisation for Id: ${organisationId.value.toString}"
          case Right(_)                             => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verifyZeroInteractions(mockThirdPartyDeveloperConnector)

      }

      "return Left collaborator does not exist on Organisation" in new ManageCollaboratorSetup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))

        await(inTest.removeCollaborator(organisationId, gatekeeperUserId, emailOne)) match {
          case Left(x: ValidateCollaboratorFailureResult) => x.message shouldBe s"Collaborator not found on Organisation"
          case Right(_)                                   => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verifyZeroInteractions(mockThirdPartyDeveloperConnector)
      }

      "return Left when delete user from TPD fails" in new ManageCollaboratorSetup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithCollaborators)))
        when(mockThirdPartyDeveloperConnector.deleteUser(eqTo(deleteUserRequest))(*)).thenReturn(Future.successful(DeleteUserFailureResult))

        await(inTest.removeCollaborator(organisationId, gatekeeperUserId, emailOne)) match {
          case Left(x: DeleteCollaboratorFailureResult) => x.message shouldBe s"Failed to delete user"
          case Right(_)                                 => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verify(mockThirdPartyDeveloperConnector).deleteUser(eqTo(deleteUserRequest))(*)

      }

      "return Left update organisation fails" in new ManageCollaboratorSetup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithCollaborators)))
        when(mockThirdPartyDeveloperConnector.deleteUser(eqTo(deleteUserRequest))(*)).thenReturn(Future.successful(DeleteUserSuccessResult))
        when(mockOrganisationRepo.update(*)).thenReturn(Future.successful(Left(new BadRequestException("Organisation does not exist"))))

        await(inTest.removeCollaborator(organisationId, gatekeeperUserId, emailOne)) match {
          case Left(x: UpdateOrganisationFailedResult) => x.message shouldBe s"Organisation does not exist"
          case Right(_)                                => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verify(mockThirdPartyDeveloperConnector).deleteUser(eqTo(deleteUserRequest))(*)
        verify(mockOrganisationRepo).update(*)

      }

      "return Right updated organisation" in new ManageCollaboratorSetup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithCollaborators)))
        when(mockThirdPartyDeveloperConnector.deleteUser(eqTo(deleteUserRequest))(*)).thenReturn(Future.successful(DeleteUserSuccessResult))
        val updatedCollaborators = organisationWithCollaborators.collaborators.filterNot(_.email.equalsIgnoreCase(emailOne))
        val updatedOrganisation = organisationWithCollaborators.copy(collaborators = updatedCollaborators)
        when(mockOrganisationRepo.update(*)).thenReturn(Future.successful(Right(updatedOrganisation)))

        await(inTest.removeCollaborator(organisationId, gatekeeperUserId, emailOne)) match {
          case Right(organisation: Organisation) => organisation.collaborators should contain only collaboratorTwo
          case Left(_)                           => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verify(mockThirdPartyDeveloperConnector).deleteUser(eqTo(deleteUserRequest))(*)
        verify(mockOrganisationRepo).update(*)

      }
    }
  }
}
