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
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.InternalServerException

class UploadServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockThirdPartyDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockThirdPartyDeveloperConnector)
  }

  trait Setup {
    val inTest = new UploadService(mockThirdPartyDeveloperConnector)

    val uuid = UUID.fromString("dcc80f1e-4798-11ec-81d3-0242ac130003")
    val vendorId = VendorId(9000)

    def getUuid() = UUID.randomUUID()

    val organisationId = OrganisationId(uuid)
    val organisation = Organisation(organisationId = organisationId, vendorId = vendorId, name = OrganisationName("Organisation Name"))

    val userId = UserId(UUID.randomUUID())
    val emailOne = "foo@bar.com"
    val firstName = "Joe"
    val lastName = "Bloggs"
    val services = ""
    val vendorIds = ""

    val emailTwo = "anotheruser@bar.com"
    val collaboratorOne = Collaborator(userId, emailOne)
    val collaboratorTwo = Collaborator(UserId(UUID.randomUUID()), emailTwo)
    val collaborators = List(collaboratorOne, collaboratorTwo)
    val organisationWithCollaborators = organisation.copy(collaborators = collaborators)
    val gatekeeperUserId = "John Doe"
    val getOrCreateUserIdRequest = GetOrCreateUserIdRequest(emailOne)
    val coreUserDetail = CoreUserDetail(userId, emailOne)

    val parsedUser = ParsedUser(
      email = emailOne,
      firstName = firstName,
      lastName = lastName,
      services = services,
      vendorIds = vendorIds
    )

    val userResponse = UserResponse(
      email = emailOne,
      firstName = firstName,
      lastName = lastName,
      verified = true,
      emailPreferences = EmailPreferences.noPreferences,
      id = userId
    )

    val expectedExistingUser = CreatedOrUpdatedUser(1, parsedUser, userResponse, true)
    val expectedCreatedUser = expectedExistingUser.copy(isExisting = false)

    val createXmlUserRequestObj = CreateXmlUserRequest(email = emailOne, firstName = firstName, lastName = lastName, organisation = None)

  }

  "uploadUsers" should {
    "return Right(CreatedOrUpdatedUser) when user exists in tpd" in new Setup {
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailOne)))(*)).thenReturn(Future.successful(Right(List(userResponse))))

      val results = await(inTest.uploadUsers(List(parsedUser)))

      results.nonEmpty shouldBe true
      results.size shouldBe 1
      results.head match {
        case Left(_)                                           => fail
        case Right(createdOrUpdatedUser: CreatedOrUpdatedUser) => createdOrUpdatedUser shouldBe expectedExistingUser
      }

    }
  }

  "returns Right(CreatedOrUpdatedUser) when user not found in tpd and createVerifiedUser are successful" in new Setup {
    when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(parsedUser.email)))(*)).thenReturn(Future.successful(Right(Nil)))
    when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(createXmlUserRequestObj))(*)).thenReturn(Future.successful(Right(userResponse)))

    val results = await(inTest.uploadUsers(List(parsedUser)))

    results.nonEmpty shouldBe true
    results.size shouldBe 1
    results.head match {
      case Left(_)                                           => fail
      case Right(createdOrUpdatedUser: CreatedOrUpdatedUser) => createdOrUpdatedUser shouldBe expectedCreatedUser
    }

    verify(mockThirdPartyDeveloperConnector).getByEmail(eqTo(List(parsedUser.email)))(*)
    verify(mockThirdPartyDeveloperConnector).createVerifiedUser(eqTo(createXmlUserRequestObj))(*)

  }

  "returns Left(UploadUserResult) when getByEmail returns a Left" in new Setup {
    when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(emailOne)))(*)).thenReturn(Future.successful(
      Left(new InternalServerException("could not get users by email"))
    ))

    val results = await(inTest.uploadUsers(List(parsedUser)))

    results.nonEmpty shouldBe true
    results.size shouldBe 1
    results.head match {
      case Left(e: UploadUserFailedResult) => e.message shouldBe s"Error when retrieving user by email on csv row number 1"
      case _                       => fail
    }
  }

  "returns Left(UploadUserResult) when createVerifiedUser user fails" in new Setup {
    when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(List(parsedUser.email)))(*)).thenReturn(Future.successful(Right(Nil)))
     when(mockThirdPartyDeveloperConnector.createVerifiedUser(eqTo(createXmlUserRequestObj))(*)).thenReturn(Future.successful(Left(new InternalServerException("Unable to register user"))))

    val results = await(inTest.uploadUsers(List(parsedUser)))

    results.nonEmpty shouldBe true
    results.size shouldBe 1
    results.head match {
      case Left(e: UploadUserFailedResult) => e.message shouldBe s"Unable to register user on csv row number 1"
      case _                                           => fail
    }

    verify(mockThirdPartyDeveloperConnector).getByEmail(eqTo(List(parsedUser.email)))(*)
    verify(mockThirdPartyDeveloperConnector).createVerifiedUser(eqTo(createXmlUserRequestObj))(*)
  }


}
