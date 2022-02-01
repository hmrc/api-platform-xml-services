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
import play.api.http.Status._
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
import uk.gov.hmrc.http.UpstreamErrorResponse
import org.mongodb.scala.{MongoCommandException, ServerAddress}
import org.mongodb.scala.bson.BsonDocument

class UploadServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockOrganisationRepo: OrganisationRepository = mock[OrganisationRepository]
  val mockUuidService: UuidService = mock[UuidService]
  val mockThirdPartyDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockOrganisationRepo)
    reset(mockUuidService)
    reset(mockThirdPartyDeveloperConnector)
  }

  trait Setup {
    val inTest = new UploadService(mockOrganisationRepo, mockUuidService, mockThirdPartyDeveloperConnector)

    val uuid = UUID.fromString("dcc80f1e-4798-11ec-81d3-0242ac130003")
    val vendorId = VendorId(9000)

    def getUuid() = UUID.randomUUID()

    val organisationId = OrganisationId(uuid)
    val organisation = Organisation(organisationId = organisationId, vendorId = vendorId, name = OrganisationName("Organisation Name"))

    val userId = UserId(UUID.randomUUID())
    val emailOne = "foo@bar.com"
    val oldFirstName = "John"
    val oldLastName = "Doe"
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
      firstName = oldFirstName,
      lastName = oldLastName,
      verified = true,
      emailPreferences = EmailPreferences.noPreferences,
      id = userId
    )

    val createdOrUpdatedUser = CreatedOrUpdatedUser(1, parsedUser, userResponse, true)

  }

  "uploadUsers" should {
    "returns Right(CreatedOrUpdatedUser) when user exists in tpd" in new Setup {
      when(mockThirdPartyDeveloperConnector.getByEmail(eqTo(GetByEmailsRequest(emails = List(emailOne))))(*)).thenReturn(Future.successful(Right(List(userResponse))))

      await(inTest.uploadUser(parsedUser)(*))
    }
  }

    // "returns Right(CreatedOrUpdatedUser) when user not found in tpd and create user is successful" in new Setup {}

    // "returns Left(UploadUserResult)" in new Setup {}
  
}
