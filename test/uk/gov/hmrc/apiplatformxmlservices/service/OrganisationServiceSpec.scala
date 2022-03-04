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
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.{MongoCommandException, ServerAddress}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators.RemoveCollaboratorRequest
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.{CoreUserDetail, EmailPreferences, GetOrCreateUserIdRequest, ImportUserRequest, UserResponse}
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.{CreateVerifiedUserFailedResult, CreatedUserResult}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}

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
    val createOrganisationRequest = CreateOrganisationRequest(organisationToPersist.name, "some@email.com", "firstName", "lastName")
    val updatedOrgName = OrganisationName("Updated Organisation Name")

  }

  trait ManageCollaboratorSetup extends Setup {
    val organisationId = OrganisationId(uuid)
    val organisation = Organisation(organisationId = organisationId, vendorId = vendorId, name = OrganisationName("Organisation Name"))

    val userId = UserId(UUID.randomUUID())
    val firstName = "bob"
    val lastName = "hope"
    val emailOne = "foo@bar.com"
    val emailTwo = "anotheruser@bar.com"
    val collaboratorOne = Collaborator(userId, emailOne)
    val collaboratorTwo = Collaborator(UserId(UUID.randomUUID()), emailTwo)
    val collaborators = List(collaboratorOne, collaboratorTwo)
    val organisationWithCollaboratorsOne = organisation.copy(collaborators = collaborators)
    val organisationWithCollaboratorsTwo = organisation.copy(collaborators = List(collaboratorOne))
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
        case Left(_: Exception)     => fail
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
        case Left(_: Exception) => succeed
        case _                  => fail
      }

      verify(mockOrganisationRepo).findByVendorId(*[VendorId])
      verifyZeroInteractions(mockUuidService)
      verify(mockOrganisationRepo).createOrUpdate(*)
    }

    "return Right(Organisation) when vendorId does not exist and organisation successfully created" in new Setup {
      when(mockOrganisationRepo.findByVendorId(*[VendorId])).thenReturn(Future.successful(None))
      when(mockUuidService.newUuid()).thenReturn(uuid)
      val newOrganisation = organisationToPersist.copy(name = updatedOrgName)
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Right(newOrganisation)))

      await(inTest.findAndCreateOrUpdate(updatedOrgName, newOrganisation.vendorId)) match {
        case Left(_: Exception)     => fail
        case Right(x: Organisation) => x shouldBe newOrganisation
      }

      verify(mockOrganisationRepo).findByVendorId(*[VendorId])
      verify(mockUuidService).newUuid()
      verify(mockOrganisationRepo).createOrUpdate(newOrganisation)
    }

    "return Left(Exception) when vendorId does not exist and createOrUpdate returns exception" in new Setup {
      when(mockOrganisationRepo.findByVendorId(*[VendorId])).thenReturn(Future.successful(None))
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Left(new InternalServerException("Something went wrong"))))

      await(inTest.findAndCreateOrUpdate(updatedOrgName, organisationToPersist.vendorId)) match {
        case Left(_: Exception) => succeed
        case _                  => fail
      }

      verify(mockOrganisationRepo).findByVendorId(*[VendorId])
      verify(mockUuidService).newUuid()
      verify(mockOrganisationRepo).createOrUpdate(*)
    }
  }

  "createOrganisation" should {
    "return CreateOrganisationSuccessResult when vendorIdService returns a vendorId and user exists in TPD" in new Setup {
      val userId = UserId(UUID.randomUUID())
      val organistionWithAddedCollaborator = organisationToPersist.copy(collaborators = List(Collaborator(userId, createOrganisationRequest.email)))
      when(mockUuidService.newUuid()).thenReturn(uuid)
      when(mockVendorIdService.getNextVendorId()).thenReturn(Future.successful(Right(vendorId)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(createOrganisationRequest.email, createOrganisationRequest.firstName, createOrganisationRequest.lastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreatedUserResult(UserResponse(createOrganisationRequest.email, createOrganisationRequest.firstName, createOrganisationRequest.lastName, verified = true, userId, EmailPreferences.noPreferences))))
      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Right(organistionWithAddedCollaborator)))

      await(inTest.create(createOrganisationRequest)) match {
        case _: CreateOrganisationFailedResult                => fail
        case CreateOrganisationSuccessResult(x: Organisation) => x shouldBe organistionWithAddedCollaborator
      }

      verify(mockUuidService).newUuid()
      verify(mockVendorIdService).getNextVendorId()
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(*[ImportUserRequest])(*)
      verify(mockOrganisationRepo).createOrUpdate(organistionWithAddedCollaborator)
    }

    "return CreateOrganisationFailed when organisation repo returns MongoCommandException" in new Setup {
      val userId = UserId(UUID.randomUUID())
      val organistionWithAddedCollaborator = organisationToPersist.copy(collaborators = List(Collaborator(userId, createOrganisationRequest.email)))
      when(mockUuidService.newUuid()).thenReturn(uuid)
      when(mockVendorIdService.getNextVendorId()).thenReturn(Future.successful(Right(vendorId)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(createOrganisationRequest.email, createOrganisationRequest.firstName, createOrganisationRequest.lastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreatedUserResult(UserResponse(createOrganisationRequest.email, createOrganisationRequest.firstName, createOrganisationRequest.lastName, verified = true, userId, EmailPreferences.noPreferences))))

      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Left(new MongoCommandException(BsonDocument("{\"code\": 11000}"), ServerAddress()))))

      await(inTest.create(createOrganisationRequest)) match {
        case _: CreateOrganisationFailedDuplicateIdResult => succeed
        case _                                            => fail
      }

      verify(mockUuidService).newUuid()
      verify(mockVendorIdService).getNextVendorId()
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(*[ImportUserRequest])(*)
      verify(mockOrganisationRepo).createOrUpdate(organistionWithAddedCollaborator)
    }

    "return CreateOrganisationFailed when organisation repo returns Exception" in new Setup {
      val userId = UserId(UUID.randomUUID())
      val organistionWithAddedCollaborator = organisationToPersist.copy(collaborators = List(Collaborator(userId, createOrganisationRequest.email)))
      when(mockUuidService.newUuid()).thenReturn(uuid)
      when(mockVendorIdService.getNextVendorId()).thenReturn(Future.successful(Right(vendorId)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(createOrganisationRequest.email, createOrganisationRequest.firstName, createOrganisationRequest.lastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreatedUserResult(UserResponse(createOrganisationRequest.email, createOrganisationRequest.firstName, createOrganisationRequest.lastName, verified = true, userId, EmailPreferences.noPreferences))))

      when(mockOrganisationRepo.createOrUpdate(*)).thenReturn(Future.successful(Left(new RuntimeException("Something went wrong"))))

      await(inTest.create(createOrganisationRequest)) match {
        case _: CreateOrganisationFailedResult => succeed
        case _                                 => fail
      }

      verify(mockUuidService).newUuid()
      verify(mockVendorIdService).getNextVendorId()
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(*[ImportUserRequest])(*)
      verify(mockOrganisationRepo).createOrUpdate(organistionWithAddedCollaborator)
    }

    "return CreateOrganisationFailed when TPD returns error" in new Setup {
      when(mockVendorIdService.getNextVendorId()).thenReturn(Future.successful(Right(vendorId)))
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(createOrganisationRequest.email, createOrganisationRequest.firstName, createOrganisationRequest.lastName, Map.empty)))(*[HeaderCarrier]))
        .thenReturn(Future.successful(CreateVerifiedUserFailedResult("error")))

      await(inTest.create(createOrganisationRequest)) match {
        case _: CreateOrganisationFailedResult => succeed
        case _                                 => fail
      }

      verify(mockVendorIdService).getNextVendorId()
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(*[ImportUserRequest])(*)
      verifyZeroInteractions(mockOrganisationRepo)
    }

    "return CreateOrganisationFailed when next vendorID returns Left" in new Setup {
      when(mockVendorIdService.getNextVendorId())
        .thenReturn(Future.successful(Left(new RuntimeException("some error"))))

      await(inTest.create(createOrganisationRequest)) match {
        case _: CreateOrganisationFailedResult => succeed
        case _                                 => fail
      }

      verify(mockVendorIdService).getNextVendorId()
      verifyZeroInteractions(mockThirdPartyDeveloperConnector)
      verifyZeroInteractions(mockOrganisationRepo)
    }

    "return CreateOrganisationFailedResult when vendorIdService does not return a vendorId" in new Setup {
      when(mockVendorIdService.getNextVendorId()).thenReturn(Future.failed(new RuntimeException("some Error")))

      await(inTest.create(createOrganisationRequest)) match {
        case e: CreateOrganisationFailedResult => e.message shouldBe "some Error"
        case _                                 => fail
      }

      verify(mockVendorIdService).getNextVendorId()
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
        case UpdateOrganisationSuccessResult(_) => succeed
        case _                                  => fail
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

  "findByUserId" should {
    "return a List of Organisations when userId matches" in new ManageCollaboratorSetup {
      when(mockOrganisationRepo.findByUserId(eqTo(collaboratorOne.userId)))
        .thenReturn(Future.successful(List(organisationWithCollaboratorsOne, organisationWithCollaboratorsTwo)))

      val result = await(inTest.findByUserId(collaboratorOne.userId))
      result shouldBe List(organisationWithCollaboratorsOne, organisationWithCollaboratorsTwo)

      verify(mockOrganisationRepo).findByUserId(eqTo(collaboratorOne.userId))

    }
  }

}
