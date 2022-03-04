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
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators.GetOrCreateUserFailedResult
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.{EmailPreferences, EmailTopic, ImportUserRequest, TaxRegimeInterests, UserResponse}
import uk.gov.hmrc.apiplatformxmlservices.models.{OrganisationId, OrganisationUser, UserId, XmlApi}
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.{CreateVerifiedUserFailedResult, CreatedUserResult, RetrievedUserResult}
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserFunctionsSpec extends AnyWordSpec with Matchers with MockitoSugar
  with BeforeAndAfterEach with DefaultAwaitTimeout with FutureAwaits with UserFunctions {

  override val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup{
    val email = "a@b.com"
    val firstName = "bob"
    val lastName = "hope"
    val userId = UserId(UUID.randomUUID())
    val organisationId = OrganisationId(UUID.randomUUID())
    val response = UserResponse(email, firstName, lastName, verified = true, userId = userId, emailPreferences = EmailPreferences.noPreferences)
  }

  "handleGetOrCreateUser" should {
    "return right list of user responses when user created result returned" in new Setup {
      when(thirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(email, firstName,  lastName, Map.empty)))(*))
        .thenReturn(Future.successful(CreatedUserResult(response)))

      val result: Either[GetOrCreateUserFailedResult, UserResponse] = await(handleGetOrCreateUser(email, firstName, lastName))
      result match {
        case Right(_: UserResponse) => succeed
        case _ => fail
      }
    }

    "return right list of user responses when user retrieved result returned" in new Setup {
      when(thirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(email, firstName,  lastName, Map.empty)))(*))
        .thenReturn(Future.successful(RetrievedUserResult(response)))

      val result: Either[GetOrCreateUserFailedResult, UserResponse] = await(handleGetOrCreateUser(email, firstName, lastName))
      result match {
        case Right(_: UserResponse) => succeed
        case _ => fail
      }
    }

    "return left GetOrCreateUserFailedResult when connector returns failure" in new Setup {
      when(thirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(email, firstName,  lastName, Map.empty)))(*))
        .thenReturn(Future.successful(CreateVerifiedUserFailedResult("Some error")))

      val result: Either[GetOrCreateUserFailedResult, UserResponse] = await(handleGetOrCreateUser(email, firstName, lastName))
      result match {
        case Left(e: GetOrCreateUserFailedResult) => e.message shouldBe "Some error"
        case _ => fail
      }
    }
  }

  "toOrganisationUser" should {
    val xmlservice1 = "excise-movement-control"
    val xmlservice2 = "import-control-system"
    val xmlservice3 = "paye-online"

    "return OrganisationUser with no services when user has no xml services in preferences" in new Setup {

      val interestNoXmlServices = List(TaxRegimeInterests(regime = "CUSTOMS", services = Set("service-1", "service3")),
        TaxRegimeInterests("VAT", Set("service-4", "service-6")))
      val emailPreferencesWithNoXmlServices = EmailPreferences(interestNoXmlServices, Set(EmailTopic.BUSINESS_AND_POLICY))

      val result = toOrganisationUser(organisationId, response.copy(emailPreferences = emailPreferencesWithNoXmlServices) )
      result shouldBe OrganisationUser(organisationId, userId, email, firstName, lastName, serviceNames = Nil)
    }

    "return OrganisationUser with only xml Services when user has mix of some xml and non xml services in preferences" in new Setup {
      val interestNoXmlServices = List(TaxRegimeInterests(regime = "CUSTOMS", services = Set("service-1", "service3", xmlservice1, xmlservice2)),
        TaxRegimeInterests("PAYE", Set("service-4", "service-6", xmlservice3)))
      val emailPreferencesWithNoXmlServices = EmailPreferences(interestNoXmlServices, Set(EmailTopic.BUSINESS_AND_POLICY))

      val result = toOrganisationUser(organisationId, response.copy(emailPreferences = emailPreferencesWithNoXmlServices) )
      result shouldBe OrganisationUser(organisationId, userId, email, firstName, lastName, serviceNames = List(xmlservice1, xmlservice2, xmlservice3))
      XmlApi.xmlApis.map(_.serviceName.value).intersect(result.serviceNames) should contain only (xmlservice1, xmlservice2, xmlservice3)
    }

    "return OrganisationUser with no Services when user has empty email preferences" in new Setup {
      val result = toOrganisationUser(organisationId, response )
      result shouldBe OrganisationUser(organisationId, userId, email, firstName, lastName, serviceNames = Nil)
    }
  }
}
