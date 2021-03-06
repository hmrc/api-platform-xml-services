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
import uk.gov.hmrc.apiplatformxmlservices.models.common.{ApiCategory, ServiceName}
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
  override val xmlApiService: XmlApiService = new XmlApiService()
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {
    val email = "a@b.com"
    val firstName = "bob"
    val lastName = "hope"
    val userId: UserId = UserId(UUID.randomUUID())
    val organisationId: OrganisationId = OrganisationId(UUID.randomUUID())
    val response: UserResponse = UserResponse(email, firstName, lastName, verified = true, userId = userId, emailPreferences = EmailPreferences.noPreferences)
  }

  "handleGetOrCreateUser" should {
    "return right list of user responses when user created result returned" in new Setup {
      when(thirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(email, firstName, lastName, Map.empty)))(*))
        .thenReturn(Future.successful(CreatedUserResult(response)))

      val result: Either[GetOrCreateUserFailedResult, UserResponse] = await(handleGetOrCreateUser(email, firstName, lastName))
      result match {
        case Right(_: UserResponse) => succeed
        case _ => fail
      }
    }

    "return right list of user responses when user retrieved result returned" in new Setup {
      when(thirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(email, firstName, lastName, Map.empty)))(*))
        .thenReturn(Future.successful(RetrievedUserResult(response)))

      val result: Either[GetOrCreateUserFailedResult, UserResponse] = await(handleGetOrCreateUser(email, firstName, lastName))
      result match {
        case Right(_: UserResponse) => succeed
        case _ => fail
      }
    }

    "return left GetOrCreateUserFailedResult when connector returns failure" in new Setup {
      when(thirdPartyDeveloperConnector.createVerifiedUser(eqTo(ImportUserRequest(email, firstName, lastName, Map.empty)))(*))
        .thenReturn(Future.successful(CreateVerifiedUserFailedResult("Some error")))

      val result: Either[GetOrCreateUserFailedResult, UserResponse] = await(handleGetOrCreateUser(email, firstName, lastName))
      result match {
        case Left(e: GetOrCreateUserFailedResult) => e.message shouldBe "Some error"
        case _ => fail
      }
    }
  }

  "toOrganisationUser" should {

    val xmlApi1 = XmlApi("Excise Movement Control System",
      ServiceName("excise-movement-control"),
      "/government/collections/excise-movement-control-system-fs31-support-for-software-developers",
      "Technical specifications for the Excise Movement Control System (EMCS).",
      Some(List(ApiCategory.CUSTOMS)))

    val xmlApi2 = XmlApi("Import Control System",
      ServiceName("import-control-system"),
      "/government/collections/import-control-system-support-for-software-developers",
      "Technical specifications for Import Control System software developers.",
      Some(List(ApiCategory.CUSTOMS)))

    val xmlApi3 = XmlApi("PAYE Online",
      ServiceName("paye-online"),
      "/government/collections/paye-online-support-for-software-developers",
      "Technical specifications for software developers working with the PAYE online service.",
      Some(List(ApiCategory.PAYE)))

    "return OrganisationUser with no services when user has no xml services in preferences" in new Setup {

      val interestNoXmlServices = List(TaxRegimeInterests(regime = "CUSTOMS", services = Set("service-1", "service3")),
        TaxRegimeInterests("VAT", Set("service-4", "service-6")))
      val emailPreferencesWithNoXmlServices: EmailPreferences = EmailPreferences(interestNoXmlServices, Set(EmailTopic.BUSINESS_AND_POLICY))

      val result: OrganisationUser = toOrganisationUser(organisationId, response.copy(emailPreferences = emailPreferencesWithNoXmlServices))
      result shouldBe OrganisationUser(organisationId, userId, email, firstName, lastName, xmlApis = Nil)
    }

    "return OrganisationUser with only xml Services when user has mix of some xml and non xml services in preferences" in new Setup {
      val interestNoXmlServices = List(TaxRegimeInterests(regime = "CUSTOMS", services = Set("service-1", "service3", xmlApi1.serviceName.value, xmlApi2.serviceName.value)),
        TaxRegimeInterests("PAYE", Set("service-4", "service-6", xmlApi3.serviceName.value)))
      val emailPreferencesWithNoXmlServices: EmailPreferences = EmailPreferences(interestNoXmlServices, Set(EmailTopic.BUSINESS_AND_POLICY))

      val result: OrganisationUser = toOrganisationUser(organisationId, response.copy(emailPreferences = emailPreferencesWithNoXmlServices))
      result shouldBe OrganisationUser(organisationId, userId, email, firstName, lastName, xmlApis = List(xmlApi1, xmlApi2, xmlApi3))
      xmlApiService.getStableApis().intersect(result.xmlApis) should contain only(xmlApi1, xmlApi2, xmlApi3)
    }

    "return OrganisationUser with no Services when user has empty email preferences" in new Setup {
      val result: OrganisationUser = toOrganisationUser(organisationId, response)
      result shouldBe OrganisationUser(organisationId, userId, email, firstName, lastName, xmlApis = Nil)
    }
  }
}
