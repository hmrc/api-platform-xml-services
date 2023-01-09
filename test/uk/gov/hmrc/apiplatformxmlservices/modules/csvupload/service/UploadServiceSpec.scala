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

package uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.service

import org.mockito.scalatest.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators.{GetOrganisationFailedResult, ManageCollaboratorResult, OrganisationAlreadyHasCollaboratorResult}
import uk.gov.hmrc.apiplatformxmlservices.models.common.{ApiCategory, ServiceName}
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.{CoreUserDetail, EmailPreferences, GetOrCreateUserIdRequest, ImportUserRequest, UserResponse}
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.{AddUserToOrgFailureResult, CreateOrGetUserFailedResult, CreateVerifiedUserFailedResult, CreatedUserResult, InvalidUserResult, ParsedUser, RetrievedUserResult, UploadCreatedUserSuccessResult, UploadExistingUserSuccessResult}
import uk.gov.hmrc.apiplatformxmlservices.service.{OrganisationService, TeamMemberService, XmlApiService}
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UploadServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockThirdPartyDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
  val mockOrganisationService: OrganisationService = mock[OrganisationService]
  val mockTeamMemberService: TeamMemberService = mock[TeamMemberService]
  val mockXmlApiService: XmlApiService = mock[XmlApiService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockThirdPartyDeveloperConnector)
    reset(mockOrganisationService)
    reset(mockTeamMemberService)
    reset(mockXmlApiService)
  }

  trait Setup {
    val inTest = new UploadService(mockThirdPartyDeveloperConnector, mockOrganisationService, mockXmlApiService, mockTeamMemberService)

    val uuid = UUID.fromString("dcc80f1e-4798-11ec-81d3-0242ac130003")
    val vendorId1 = VendorId(9000)
    val vendorId2 = VendorId(9001)

    def getUuid() = UUID.randomUUID()

    val organisationId = OrganisationId(uuid)
    val organisation1 = Organisation(organisationId = organisationId, vendorId = vendorId1, name = OrganisationName("Organisation Name"))
    val organisation2 = organisation1.copy(vendorId = vendorId2)

    val userId = UserId(UUID.randomUUID())
    val emailOne = "foo@bar.com"
    val firstName = "Joe"
    val lastName = "Bloggs"
    val services = List(ServiceName("import-control-system"), ServiceName("charities-online"))
    val invalidServices = List(ServiceName("service1"), ServiceName("charities-online"))
    val vendorIds = ""

    val emailTwo = "anotheruser@bar.com"
    val collaboratorOne = Collaborator(userId, emailOne)
    val collaboratorTwo = Collaborator(UserId(UUID.randomUUID()), emailTwo)
    val collaborators = List(collaboratorOne, collaboratorTwo)
    val organisationWithCollaborators = organisation1.copy(collaborators = collaborators)
    val gatekeeperUserId = "John Doe"
    val getOrCreateUserIdRequest = GetOrCreateUserIdRequest(emailOne)
    val coreUserDetail = CoreUserDetail(userId, emailOne)
    val emailPreferences: Map[ApiCategory, List[ServiceName]] = Map(
    ApiCategory.CHARITIES -> List(ServiceName("charities-online")),
    ApiCategory.CUSTOMS -> List(ServiceName("import-control-system"))
    )

    val xmlApiCharities = XmlApi(name = "Charities Online",
      serviceName = ServiceName("charities-online"),
      context = "context",
      description = "description",
      categories = Some(Seq(ApiCategory.CHARITIES)))
    val xmlApiImport = xmlApiCharities.copy(
      name = "Import Control System",
      serviceName = ServiceName("import-control-system"),
      categories = Some(Seq(ApiCategory.CUSTOMS))
    )
    val stableXmlApis = List(xmlApiCharities, xmlApiImport)

    when(mockXmlApiService.getStableApis()).thenReturn(stableXmlApis)

    val parsedUser = ParsedUser(
      email = emailOne,
      firstName = firstName,
      lastName = lastName,
      services = services,
      vendorIds = List(vendorId1, vendorId2)
    )

    val userResponse = UserResponse(
      email = emailOne,
      firstName = firstName,
      lastName = lastName,
      verified = true,
      userId = userId,
      emailPreferences = EmailPreferences.noPreferences
    )

    val importUserRequestObj = ImportUserRequest(email = emailOne, firstName = firstName, lastName = lastName, emailPreferences = emailPreferences)

    def primeMocksForAddCollaboratorToOrgFailure(response1: Either[ManageCollaboratorResult, Organisation], response2: Either[ManageCollaboratorResult, Organisation]) = {
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(importUserRequestObj))(*)).thenReturn(Future.successful(CreatedUserResult(userResponse)))
      when(mockOrganisationService.findByVendorId(vendorId1)).thenReturn(Future.successful(Some(organisation1)))
      when(mockOrganisationService.findByVendorId(vendorId2)).thenReturn(Future.successful(Some(organisation2)))
      when(mockTeamMemberService.handleAddCollaboratorToOrgByVendorId(userResponse.email, userResponse.userId, vendorId1)).thenReturn(Future.successful(response1))
      when(mockTeamMemberService.handleAddCollaboratorToOrgByVendorId(userResponse.email, userResponse.userId, vendorId2)).thenReturn(Future.successful(response2))
    }

    def verifyAddCollaboratorToOrgFailure(){
      verify(mockOrganisationService, times(2)).findByVendorId(*[VendorId])
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(eqTo(importUserRequestObj))(*)
      verify(mockTeamMemberService, times(2)).handleAddCollaboratorToOrgByVendorId( *, *[UserId], *[VendorId])

    }
  }

  "uploadUsers" should {

    def errorMessageForVendorIdUserId(vendorId: VendorId, userId: UserId) =
      s"RowNumber:1 - failed to add user ${userId.value} to vendorId ${vendorId.value} : Failed to get organisation for Vendor Id: ${vendorId.value}"

    "return UploadCreatedUserSuccessResult when vendorId validation successful and user is created and successfully returned from the connector" in new Setup {
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(importUserRequestObj))(*)).thenReturn(Future.successful(CreatedUserResult(userResponse)))
      when(mockOrganisationService.findByVendorId(vendorId1)).thenReturn(Future.successful(Some(organisation1)))
      when(mockOrganisationService.findByVendorId(vendorId2)).thenReturn(Future.successful(Some(organisation2)))
      when(mockTeamMemberService.handleAddCollaboratorToOrgByVendorId(userResponse.email, userResponse.userId, vendorId1)).thenReturn(Future.successful(Right(organisation1)))
      when(mockTeamMemberService.handleAddCollaboratorToOrgByVendorId(userResponse.email, userResponse.userId, vendorId2)).thenReturn(Future.successful(Right(organisation2)))

      val results = await(inTest.uploadUsers(List(parsedUser)))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case UploadCreatedUserSuccessResult(rowNumber: Int, response: UserResponse) =>
          response shouldBe userResponse
          rowNumber shouldBe 1
        case _ => fail
      }
      verify(mockOrganisationService, times(2)).findByVendorId(*[VendorId])
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(eqTo(importUserRequestObj))(*)
      verify(mockTeamMemberService, times(2)).handleAddCollaboratorToOrgByVendorId( *, *[UserId], *[VendorId])

    }

    "return UploadExistingUserSuccessResult when vendorId validation successful and user is retrieved and successfully returned from the connector" in new Setup {
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(importUserRequestObj))(*)).thenReturn(Future.successful(RetrievedUserResult(userResponse)))
      when(mockOrganisationService.findByVendorId(vendorId1)).thenReturn(Future.successful(Some(organisation1)))
      when(mockOrganisationService.findByVendorId(vendorId2)).thenReturn(Future.successful(Some(organisation2)))
      when(mockTeamMemberService.handleAddCollaboratorToOrgByVendorId(userResponse.email, userResponse.userId, vendorId1)).thenReturn(Future.successful(Right(organisation1)))
      when(mockTeamMemberService.handleAddCollaboratorToOrgByVendorId(userResponse.email, userResponse.userId, vendorId2)).thenReturn(Future.successful(Right(organisation2)))

      val results = await(inTest.uploadUsers(List(parsedUser)))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case UploadExistingUserSuccessResult(rowNumber: Int, response: UserResponse) =>
          response shouldBe userResponse
          rowNumber shouldBe 1
        case _                                                                       => fail
      }

      verify(mockOrganisationService, times(2)).findByVendorId(*[VendorId])
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(eqTo(importUserRequestObj))(*)
      verify(mockTeamMemberService, times(2)).handleAddCollaboratorToOrgByVendorId(*, *[UserId], *[VendorId])

    }

  "return InvalidUserResult when a service and a vendorId are invalid and validation fails" in new Setup {
      when(mockOrganisationService.findByVendorId(vendorId1)).thenReturn(Future.successful(Some(organisation1)))
      when(mockOrganisationService.findByVendorId(vendorId2)).thenReturn(Future.successful(None))
      val results = await(inTest.uploadUsers(List(parsedUser.copy(services = invalidServices))))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case InvalidUserResult(message: String) => message shouldBe "RowNumber:1 - Invalid vendorId(s) | RowNumber:1 - Invalid service(s)"
        case _                                  => fail
      }

      verify(mockOrganisationService, times(2)).findByVendorId(*[VendorId])
      verifyZeroInteractions(mockThirdPartyDeveloperConnector)

    }
    

    "return InvalidUserResult when vendorId is not found and so validation fails" in new Setup {
      when(mockOrganisationService.findByVendorId(vendorId1)).thenReturn(Future.successful(Some(organisation1)))
      when(mockOrganisationService.findByVendorId(vendorId2)).thenReturn(Future.successful(None))

      val results = await(inTest.uploadUsers(List(parsedUser)))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case InvalidUserResult(message: String) => message shouldBe "RowNumber:1 - Invalid vendorId(s)"
        case _                                  => fail
      }

      verify(mockOrganisationService, times(2)).findByVendorId(*[VendorId])
      verifyZeroInteractions(mockThirdPartyDeveloperConnector)

    }

    "return InvalidUserResult when vendorId is missing and so validation fails" in new Setup {

      val results = await(inTest.uploadUsers(List(parsedUser.copy(vendorIds = List.empty))))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case InvalidUserResult(message: String) => message shouldBe "RowNumber:1 - missing vendorIds on user"
        case _                                  => fail
      }

      verifyZeroInteractions(mockOrganisationService)
      verifyZeroInteractions(mockThirdPartyDeveloperConnector)

    }

    "return CreateOrGetUserFailedResult when vendorId validation successful and createVerifiedUser user fails " in new Setup {
      when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(importUserRequestObj))(*)).thenReturn(
        Future.successful(CreateVerifiedUserFailedResult("Unable to register user"))
      )
      when(mockOrganisationService.findByVendorId(vendorId1)).thenReturn(Future.successful(Some(organisation1)))
      when(mockOrganisationService.findByVendorId(vendorId2)).thenReturn(Future.successful(Some(organisation2)))

      val results = await(inTest.uploadUsers(List(parsedUser)))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case e: CreateOrGetUserFailedResult => e.message shouldBe s"RowNumber:1 - failed to get or create User: Unable to register user"
        case _                              => fail
      }

      verify(mockOrganisationService, times(2)).findByVendorId(*[VendorId])
      verify(mockThirdPartyDeveloperConnector).createVerifiedUser(eqTo(importUserRequestObj))(*)
    }

  "return Sucesss when addCollaboratorByVendorId returns collaborator allready linked to org error" in new Setup {

      primeMocksForAddCollaboratorToOrgFailure(
        Left(OrganisationAlreadyHasCollaboratorResult(message = s"Failed to get organisation for Vendor Id: ${vendorId1.value}")),
        Right(organisation2)
      )

      val results = await(inTest.uploadUsers(List(parsedUser)))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case UploadCreatedUserSuccessResult(rowNumber: Int, response: UserResponse) =>
          response shouldBe userResponse
          rowNumber shouldBe 1
          case _ =>  fail
      }

      verifyAddCollaboratorToOrgFailure()

    }

    "return AddUserToOrgFailureResult when addCollaboratorByVendorId fails for first vendorId but successful for the second vendorId" in new Setup {

      primeMocksForAddCollaboratorToOrgFailure(
        Left(GetOrganisationFailedResult(message = s"Failed to get organisation for Vendor Id: ${vendorId1.value}")),
        Right(organisation2)
      )

      val results = await(inTest.uploadUsers(List(parsedUser)))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case AddUserToOrgFailureResult(error: String) => error shouldBe errorMessageForVendorIdUserId(vendorId1, userId)
        case _                                        => fail
      }

      verifyAddCollaboratorToOrgFailure()
    }

    "return AddUserToOrgFailureResult when addCollaboratorByVendorId fails for second vendorId but successful for the first vendorId" in new Setup {

      primeMocksForAddCollaboratorToOrgFailure(
        Right(organisation1),
        Left(GetOrganisationFailedResult(message = s"Failed to get organisation for Vendor Id: ${vendorId2.value}"))
      )

      val results = await(inTest.uploadUsers(List(parsedUser)))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case AddUserToOrgFailureResult(error: String) => error shouldBe errorMessageForVendorIdUserId(vendorId2, userId)
        case _                                        => fail
      }

      verifyAddCollaboratorToOrgFailure()
    }

    "return AddUserToOrgFailureResult when addCollaboratorByVendorId fails for first and second vendorIds" in new Setup {

      primeMocksForAddCollaboratorToOrgFailure(
        Left(GetOrganisationFailedResult(message = s"Failed to get organisation for Vendor Id: ${vendorId1.value}")),
        Left(GetOrganisationFailedResult(message = s"Failed to get organisation for Vendor Id: ${vendorId2.value}"))
      )

      val results = await(inTest.uploadUsers(List(parsedUser)))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case AddUserToOrgFailureResult(error: String) => error shouldBe s"${errorMessageForVendorIdUserId(vendorId1, userId)} | ${errorMessageForVendorIdUserId(vendorId2, userId)}"
        case _                                        => fail
      }

     verifyAddCollaboratorToOrgFailure()
    }

    
  }

}
