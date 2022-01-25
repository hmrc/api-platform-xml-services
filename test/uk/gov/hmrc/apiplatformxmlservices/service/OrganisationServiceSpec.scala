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
import play.api.test.Helpers.await
import play.api.test.Helpers.defaultAwaitTimeout
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper._
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.InternalServerException

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
<<<<<<< HEAD
    val updatedOrgName = OrganisationName("Updated Organisation Name")
=======
    val createOrganisationRequest = CreateOrganisationRequest(organisationToPersist.name, "some@email.com")
>>>>>>> API-5249: code compiles and test pass

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
    val gatekeeperUserId = "John Doe"
    val getOrCreateUserIdRequest = GetOrCreateUserIdRequest(emailOne)
    val coreUserDetail = CoreUserDetail(userId, emailOne)

    val removeCollaboratorRequest = RemoveCollaboratorRequest(emailOne, gatekeeperUserId)
  }

  "findAndCreateOrUpdate" should {
    "return Right(Organisation) when vendorId exists and organisation successfully updated" in new Setup {
      when(mockOrganisationRepo.findByVendorId(*[VendorId])).thenReturn(Future.successful(Some(organisationToPersist)))
      val modifiedOrganisation = organisationToPersist.copy(name = updatedOrgName)
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Right(modifiedOrganisation)))

      await(inTest.findAndCreateOrUpdate(updatedOrgName, organisationToPersist.vendorId)) match {
        case Left(e: Exception)     => fail
        case Right(x: Organisation) => x shouldBe modifiedOrganisation
      }

      verify(mockOrganisationRepo).findByVendorId(*[VendorId])
      verify(mockOrganisationRepo).createOrUpdate(modifiedOrganisation)
      verifyZeroInteractions(mockUuidService)
    }

    "return Left(Exception) when vendorId exists and createOrUpdate returns exception" in new Setup {
      when(mockOrganisationRepo.findByVendorId(*[VendorId])).thenReturn(Future.successful(Some(organisationToPersist)))
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Left(new InternalServerException("Something went wrong"))))

      await(inTest.findAndCreateOrUpdate(updatedOrgName, organisationToPersist.vendorId)) match {
        case Left(e: Exception) => succeed
        case _                  => fail
      }

      verify(mockOrganisationRepo).findByVendorId(*[VendorId])
      verifyZeroInteractions(mockUuidService)
      verify(mockOrganisationRepo).createOrUpdate(*)
    }

    "return Right(Organisation) when vendorId does not exist and organisation successfully created" in new Setup {
      when(mockOrganisationRepo.findByVendorId(*[VendorId])).thenReturn(Future.successful(None))
      when(mockUuidService.newUuid).thenReturn(uuid)
      val newOrganisation = organisationToPersist.copy(name = updatedOrgName)
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Right(newOrganisation)))

      await(inTest.findAndCreateOrUpdate(updatedOrgName, newOrganisation.vendorId)) match {
        case Left(e: Exception)     => fail
        case Right(x: Organisation) => x shouldBe newOrganisation
      }

      verify(mockOrganisationRepo).findByVendorId(*[VendorId])
      verify(mockUuidService).newUuid
      verify(mockOrganisationRepo).createOrUpdate(newOrganisation)
    }

    "return Left(Exception) when vendorId does not exist and createOrUpdate returns exception" in new Setup {
      when(mockOrganisationRepo.findByVendorId(*[VendorId])).thenReturn(Future.successful(None))
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Left(new InternalServerException("Something went wrong"))))

      await(inTest.findAndCreateOrUpdate(updatedOrgName, organisationToPersist.vendorId)) match {
        case Left(e: Exception) => succeed
        case _                  => fail
      }

      verify(mockOrganisationRepo).findByVendorId(*[VendorId])
      verify(mockUuidService).newUuid
      verify(mockOrganisationRepo).createOrUpdate(*)
    }
  }

  "createOrganisation" should {
    "return CreateOrganisationSuccessResult when vendorIdService returns a vendorId and user exists in TPD" in new Setup {
      val userId = UserId(UUID.randomUUID())
      val organistionWithAddedCollaborator = organisationToPersist.copy(collaborators = List(Collaborator(userId, createOrganisationRequest.email)))
      when(mockUuidService.newUuid).thenReturn(uuid)
      when(mockVendorIdService.getNextVendorId).thenReturn(Future.successful(vendorId))
      when(mockThirdPartyDeveloperConnector.getOrCreateUserId(eqTo(GetOrCreateUserIdRequest(createOrganisationRequest.email)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(Right(CoreUserDetail(userId, createOrganisationRequest.email))))
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Right(organistionWithAddedCollaborator)))

<<<<<<< HEAD
      await(inTest.create(organisationToPersist.name)) match {
        case Left(e: Exception)     => fail
        case Right(x: Organisation) => x shouldBe organisationToPersist
=======

      await(inTest.create(createOrganisationRequest)) match {
        case _ : CreateOrganisationFailedResult => fail
        case CreateOrganisationSuccessResult(x: Organisation) => x shouldBe organistionWithAddedCollaborator
>>>>>>> API-5249: code compiles and test pass
      }

      verify(mockUuidService).newUuid
      verify(mockVendorIdService).getNextVendorId
      verify(mockThirdPartyDeveloperConnector).getOrCreateUserId(*[GetOrCreateUserIdRequest])(*)
      verify(mockOrganisationRepo).createOrUpdate(organistionWithAddedCollaborator)
    }

    "return CreateOrganisationFailedResult when vendorIdService does not return a vendorId" in new Setup {
      when(mockVendorIdService.getNextVendorId).thenReturn(Future.failed(new RuntimeException("some Error")))

<<<<<<< HEAD
      await(inTest.create(organisationToPersist.name)) match {
        case Left(e: Exception) => e.getMessage shouldBe "Could not get max vendorId"
        case Right(_)           => fail
=======
      await(inTest.create(createOrganisationRequest)) match {
        case e : CreateOrganisationFailedResult  => e.message shouldBe "some Error"
        case _: CreateOrganisationSuccessResult => fail
>>>>>>> API-5249: code compiles and test pass
      }

      verify(mockVendorIdService).getNextVendorId
      verifyZeroInteractions(mockUuidService)
      verifyZeroInteractions(mockOrganisationRepo)
    }
  }

  "update" should {
    "return true when update successful" in new Setup {
      val updatedOrganisation = organisationToPersist.copy(name = OrganisationName("New Organisation Name"))
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Right(updatedOrganisation)))

      val result = await(inTest.update(updatedOrganisation))
      result shouldBe Right(updatedOrganisation)

      verify(mockOrganisationRepo).createOrUpdate(updatedOrganisation)

    }
  }

  "updateOrganisationDetails" should {
    "returns the result from the repository when called" in new Setup {
      val updatedOrganisationName = OrganisationName("New Organisation Name")
      val updatedOrganisation = organisationToPersist.copy(name = updatedOrganisationName)
      when(mockOrganisationRepo.updateOrganisationDetails(eqTo(updatedOrganisation.organisationId), eqTo(updatedOrganisationName)))
        .thenReturn(Future.successful(UpdateOrganisationSuccessResult(updatedOrganisation)))

      val result = await(inTest.updateOrganisationDetails(updatedOrganisation.organisationId, updatedOrganisationName))
      result match {
        case UpdateOrganisationSuccessResult(updatedOrganisation) => succeed
        case _                                                    => fail
      }

      verify(mockOrganisationRepo).updateOrganisationDetails(eqTo(updatedOrganisation.organisationId), eqTo(updatedOrganisationName))

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
      when(mockOrganisationRepo.findAll(eqTo(Some(OrganisationSortBy.ORGANISATION_NAME)))).thenReturn(Future.successful(List(organisationToPersist)))

      val result = await(inTest.findAll(Some(OrganisationSortBy.ORGANISATION_NAME)))
      result shouldBe List(organisationToPersist)

      verify(mockOrganisationRepo).findAll(*)

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
        case _                                      => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)

    }

    "return Left when Organisation exists but user already assigned to organisation" in new ManageCollaboratorSetup {

      val organisationWithUSer = organisation.copy(collaborators = List(Collaborator(UserId(UUID.randomUUID()), emailOne)))
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithUSer)))
      when(mockThirdPartyDeveloperConnector.getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)).thenReturn(Future.successful(Left(new InternalServerException("Not found"))))

      await(inTest.addCollaborator(organisationId, emailOne)) match {
        case Left(x: OrganisationAlreadyHasCollaboratorResult) => succeed
        case _                                                 => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])

    }

    "return Left when Organisation exists and get User is successful but update Org fails" in new ManageCollaboratorSetup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))
      when(mockThirdPartyDeveloperConnector.getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)).thenReturn(Future.successful(Right(coreUserDetail)))
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Left(new BadRequestException("Organisation does not exist"))))

      await(inTest.addCollaborator(organisationId, emailOne)) match {
        case Left(x: UpdateCollaboratorFailedResult) => x.message shouldBe s"Organisation does not exist"
        case _                                       => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)
      verify(mockOrganisationRepo).createOrUpdate(*)

    }

    "return Right when collaborator successfully added to organisation" in new ManageCollaboratorSetup {
      when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))
      when(mockThirdPartyDeveloperConnector.getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)).thenReturn(Future.successful(Right(coreUserDetail)))
      val organisationWithCollaborator = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(coreUserDetail.userId, coreUserDetail.email))
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Right(organisationWithCollaborator)))

      await(inTest.addCollaborator(organisationId, emailOne)) match {
        case Right(organisation: Organisation) => organisation.collaborators should contain only collaboratorOne
        case _                                 => fail
      }

      verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
      verify(mockThirdPartyDeveloperConnector).getOrCreateUserId(eqTo(getOrCreateUserIdRequest))(*)
      verify(mockOrganisationRepo).createOrUpdate(*)

    }

    "removeCollaborator" should {
      "return Left when Organisation does not exist" in new ManageCollaboratorSetup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(None))

        await(inTest.removeCollaborator(organisationId, removeCollaboratorRequest)) match {
          case Left(x: GetOrganisationFailedResult) => x.message shouldBe s"Failed to get organisation for Id: ${organisationId.value.toString}"
          case _                                    => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verifyZeroInteractions(mockThirdPartyDeveloperConnector)

      }

      "return Left collaborator does not exist on Organisation" in new ManageCollaboratorSetup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))

        await(inTest.removeCollaborator(organisationId, removeCollaboratorRequest)) match {
          case Left(x: ValidateCollaboratorFailureResult) => x.message shouldBe s"Collaborator not found on Organisation"
          case _                                          => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verifyZeroInteractions(mockThirdPartyDeveloperConnector)
      }

      "return Left update organisation fails" in new ManageCollaboratorSetup {
        when(mockOrganisationRepo.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisationWithCollaborators)))
        when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Left(new BadRequestException("Organisation does not exist"))))

        await(inTest.removeCollaborator(organisationId, removeCollaboratorRequest)) match {
          case Left(x: UpdateCollaboratorFailedResult) => x.message shouldBe s"Organisation does not exist"
          case _                                       => fail
        }

        verify(mockOrganisationRepo).findByOrgId(*[OrganisationId])
        verify(mockOrganisationRepo).createOrUpdate(*)

      }

      "return Right updated organisation" in new ManageCollaboratorSetup {
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
}
