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

package uk.gov.hmrc.apiplatformxmlservices.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.apache.pekko.stream.Materializer
import org.scalatest.BeforeAndAfterEach

import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatformxmlservices.common.data.CommonTestData
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper._
import uk.gov.hmrc.apiplatformxmlservices.stubs.ThirdPartyDeveloperStub
import uk.gov.hmrc.apiplatformxmlservices.support.ServerBaseISpec

class ThirdPartyDeveloperConnectorISpec extends ServerBaseISpec with BeforeAndAfterEach with ThirdPartyDeveloperStub with CommonTestData {

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  implicit def mat: Materializer = app.injector.instanceOf[Materializer]

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.third-party-developer.host" -> wireMockHost,
        "microservice.services.third-party-developer.port" -> wireMockPort,
        "metrics.enabled"                                  -> true,
        "auditing.enabled"                                 -> false,
        "auditing.consumer.baseUri.host"                   -> wireMockHost,
        "auditing.consumer.baseUri.port"                   -> wireMockPort
      )

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val getOrCreateUserIdRequest: GetOrCreateUserIdRequest = GetOrCreateUserIdRequest(anEmailAddress)

    val userResponse: UserResponse = UserResponse(
      email = anEmailAddress,
      firstName = aFirstName,
      lastName = aLastName,
      verified = true,
      userId = aUserId,
      EmailPreferences.noPreferences
    )

    val underTest: ThirdPartyDeveloperConnector = app.injector.instanceOf[ThirdPartyDeveloperConnector]

    def stubPostWithRequestBody(url: String, status: Int, expectedRequestBody: String, responseBodyAsString: String): StubMapping = {
      stubFor(
        post(urlEqualTo(url))
          .withRequestBody(equalTo(expectedRequestBody))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(responseBodyAsString)
              .withHeader("Content-Type", "application/json")
          )
      )
    }
  }

  "getOrCreateUserId" should {

    "return Right when backend returns a user" in new Setup {
      stubCreateOrGetUserIdReturnsResponse(anEmailAddress, Json.toJson(CoreUserDetail(aUserId, anEmailAddress)).toString)

      val result: Either[Throwable, CoreUserDetail] = await(underTest.getOrCreateUserId(getOrCreateUserIdRequest))

      result.map(x => x.userId mustBe aUserId)

      verify(postRequestedFor(urlMatching(s"/developers/user-id"))
        .withRequestBody(equalToJson(Json.toJson(GetOrCreateUserIdRequest(anEmailAddress)).toString())))
    }

    "return Left when backend does not return a user" in new Setup {

      stubCreateOrGetUserIdReturnsNoResponse(anEmailAddress, NO_CONTENT)

      val result: Either[Throwable, CoreUserDetail] = await(underTest.getOrCreateUserId(getOrCreateUserIdRequest))

      result match {
        case Left(e: InternalServerException) => e.message mustBe "Could not find or create user"
        case _                                => fail()
      }

      verify(postRequestedFor(urlMatching(s"/developers/user-id"))
        .withRequestBody(equalToJson(Json.toJson(GetOrCreateUserIdRequest(anEmailAddress)).toString())))
    }

    "return Left when backend returns Error" in new Setup {
      stubCreateOrGetUserIdReturnsNoResponse(anEmailAddress, INTERNAL_SERVER_ERROR)

      val result: Either[Throwable, CoreUserDetail] = await(underTest.getOrCreateUserId(getOrCreateUserIdRequest))

      result match {
        case Left(UpstreamErrorResponse(_, INTERNAL_SERVER_ERROR, _, _)) => succeed
        case _                                                           => fail()
      }

      verify(postRequestedFor(urlMatching(s"/developers/user-id"))
        .withRequestBody(equalToJson(Json.toJson(GetOrCreateUserIdRequest(anEmailAddress)).toString())))
    }
  }

  "getByEmail" should {
    val emails = List(anEmailAddress, LaxEmailAddress("b@c.com"))

    val validResponseString =
      Json.toJson(List(UserResponse(anEmailAddress, aFirstName, aLastName, verified = true, UserId.random, EmailPreferences.noPreferences))).toString

    "return Right with users when users are returned" in new Setup {
      stubGetByEmailsReturnsResponse(emails, validResponseString)

      val result: Either[Throwable, List[UserResponse]] = await(underTest.getByEmail(emails))

      result match {
        case Right(_: List[UserResponse]) => succeed
        case _                            => fail()
      }
    }

    "return Right when no users are returned" in new Setup {
      stubGetByEmailsReturnsResponse(emails, "[]")

      val result: Either[Throwable, List[UserResponse]] = await(underTest.getByEmail(emails))

      result match {
        case Right(_: List[UserResponse]) => succeed
        case _                            => fail()
      }
    }

    "return Left when not found returned" in new Setup {
      stubGetByEmailsReturnsNoResponse(emails, NOT_FOUND)
      val result: Either[Throwable, List[UserResponse]] = await(underTest.getByEmail(emails))

      result match {
        case Left(_: NotFoundException) => succeed
        case _                          => fail()
      }
    }

    "return Left when internal server error returned" in new Setup {
      stubGetByEmailsReturnsNoResponse(emails, INTERNAL_SERVER_ERROR)

      val result: Either[Throwable, List[UserResponse]] = await(underTest.getByEmail(emails))

      result match {
        case Left(UpstreamErrorResponse(_, INTERNAL_SERVER_ERROR, _, _)) => succeed
        case _                                                           => fail()
      }
    }

  }

}
