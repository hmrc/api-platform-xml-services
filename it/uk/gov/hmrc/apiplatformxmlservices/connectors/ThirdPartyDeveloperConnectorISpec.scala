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

package uk.gov.hmrc.apiplatformxmlservices.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatformxmlservices.models.CoreUserDetail
import uk.gov.hmrc.apiplatformxmlservices.models.DeleteUserFailureResult
import uk.gov.hmrc.apiplatformxmlservices.models.DeleteUserRequest
import uk.gov.hmrc.apiplatformxmlservices.models.DeleteUserSuccessResult
import uk.gov.hmrc.apiplatformxmlservices.models.GetOrCreateUserIdRequest
import uk.gov.hmrc.apiplatformxmlservices.models.JsonFormatters._
import uk.gov.hmrc.apiplatformxmlservices.models.UserId
import uk.gov.hmrc.apiplatformxmlservices.support.AwaitTestSupport
import uk.gov.hmrc.apiplatformxmlservices.support.ServerBaseISpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.http.Upstream5xxResponse

import java.{util => ju}

class ThirdPartyDeveloperConnectorISpec extends ServerBaseISpec with BeforeAndAfterEach with AwaitTestSupport {

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.third-party-developer.host" -> wireMockHost,
        "microservice.services.third-party-developer.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort
      )

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val email = "foo@bar.com"
    val userId: UserId = UserId(ju.UUID.randomUUID())
    val getOrCreateUserIdRequest = GetOrCreateUserIdRequest(email)
    val deleteUserRequest = DeleteUserRequest(gatekeeperUserId = Some("John Doe"), emailAddress = email)

    val underTest: ThirdPartyDeveloperConnector = app.injector.instanceOf[ThirdPartyDeveloperConnector]
  }

  "getOrCreateUserId" should {

    "return Right when backend returns a user" in new Setup {
      stubFor(
        post(urlEqualTo("/developers/user-id"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(CoreUserDetail(userId, email)).toString)
              .withHeader("Content-Type", "application/json")
          )
      )

      val result = await(underTest.getOrCreateUserId(getOrCreateUserIdRequest))

      result.map(x => x.userId mustBe userId)

      verify(postRequestedFor(urlMatching(s"/developers/user-id"))
        .withRequestBody(equalToJson(Json.toJson(GetOrCreateUserIdRequest(email)).toString())))
    }

    "return Left when backend does not return a user" in new Setup {
      stubFor(
        post(urlEqualTo("/developers/user-id"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
              .withHeader("Content-Type", "application/json")
          )
      )

      val result = await(underTest.getOrCreateUserId(getOrCreateUserIdRequest))

      result match {
        case Left(e: InternalServerException) => e.message mustBe ("Could not find or create user")
        case _                                => fail
      }

      verify(postRequestedFor(urlMatching(s"/developers/user-id"))
        .withRequestBody(equalToJson(Json.toJson(GetOrCreateUserIdRequest(email)).toString())))
    }

    "return Left when backend returns Error" in new Setup {
      stubFor(
        post(urlEqualTo("/developers/user-id"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withHeader("Content-Type", "application/json")
          )
      )

      val result = await(underTest.getOrCreateUserId(getOrCreateUserIdRequest))

      result match {
        case Left(e: Upstream5xxResponse) => succeed
        case _                            => fail
      }

      verify(postRequestedFor(urlMatching(s"/developers/user-id"))
        .withRequestBody(equalToJson(Json.toJson(GetOrCreateUserIdRequest(email)).toString())))
    }
  }

  "deleteUser" should {

    "return DeleteCollaboratorSuccessResult when backend returns a 204" in new Setup {
      stubFor(
        post(urlEqualTo("/developers/delete"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
              .withHeader("Content-Type", "application/json")
          )
      )

      val result = await(underTest.deleteUser(deleteUserRequest))

      result mustBe DeleteUserSuccessResult

      verify(postRequestedFor(urlMatching(s"/developers/delete"))
        .withRequestBody(equalToJson(Json.toJson(deleteUserRequest).toString())))
    }

    "return DeleteCollaboratorFailureResult when backend returns a 404" in new Setup {
      stubFor(
        post(urlEqualTo("/developers/delete"))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
              .withHeader("Content-Type", "application/json")
          )
      )

      val result = await(underTest.deleteUser(deleteUserRequest))

      result mustBe DeleteUserFailureResult

      verify(postRequestedFor(urlMatching(s"/developers/delete"))
        .withRequestBody(equalToJson(Json.toJson(deleteUserRequest).toString())))
    }

  }
}
