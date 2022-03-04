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
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatformxmlservices.models.UserId
import uk.gov.hmrc.apiplatformxmlservices.models.common.{ApiCategory, ServiceName}
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.JsonFormatters._
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.{CreateVerifiedUserFailedResult, CreateVerifiedUserResult, CreatedUserResult, RetrievedUserResult}
import uk.gov.hmrc.apiplatformxmlservices.stubs.ThirdPartyDeveloperStub
import uk.gov.hmrc.apiplatformxmlservices.support.ServerBaseISpec
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException, Upstream5xxResponse}

import java.{util => ju}

class ThirdPartyDeveloperConnectorISpec extends ServerBaseISpec with BeforeAndAfterEach  with ThirdPartyDeveloperStub {

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
    val firstName = "Joe"
    val lastName = "Bloggs"
    val getOrCreateUserIdRequest: GetOrCreateUserIdRequest = GetOrCreateUserIdRequest(email)

    val userResponse: UserResponse = UserResponse(
      email = email,
      firstName = firstName,
      lastName = lastName,
      verified = true,
      userId = userId,
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
      stubCreateOrGetUserIdReturnsResponse(email, Json.toJson(CoreUserDetail(userId, email)).toString)
     
      val result: Either[Throwable, CoreUserDetail] = await(underTest.getOrCreateUserId(getOrCreateUserIdRequest))

      result.map(x => x.userId mustBe userId)

      verify(postRequestedFor(urlMatching(s"/developers/user-id"))
        .withRequestBody(equalToJson(Json.toJson(GetOrCreateUserIdRequest(email)).toString())))
    }

    "return Left when backend does not return a user" in new Setup {

      stubCreateOrGetUserIdReturnsNoResponse(email, NO_CONTENT)

      val result: Either[Throwable, CoreUserDetail] = await(underTest.getOrCreateUserId(getOrCreateUserIdRequest))

      result match {
        case Left(e: InternalServerException) => e.message mustBe "Could not find or create user"
        case _                                => fail
      }

      verify(postRequestedFor(urlMatching(s"/developers/user-id"))
        .withRequestBody(equalToJson(Json.toJson(GetOrCreateUserIdRequest(email)).toString())))
    }

    "return Left when backend returns Error" in new Setup {
      stubCreateOrGetUserIdReturnsNoResponse(email, INTERNAL_SERVER_ERROR)

      val result: Either[Throwable, CoreUserDetail] = await(underTest.getOrCreateUserId(getOrCreateUserIdRequest))

      result match {
        case Left(_: Upstream5xxResponse) => succeed
        case _                            => fail
      }

      verify(postRequestedFor(urlMatching(s"/developers/user-id"))
        .withRequestBody(equalToJson(Json.toJson(GetOrCreateUserIdRequest(email)).toString())))
    }
  }

  "getByEmail" should {
    val emails = List("a@b.com", "b@c.com")

    val validResponseString = Json.toJson(List(UserResponse("a@b.com", "firstname", "lastName", verified = true, UserId(ju.UUID.randomUUID), EmailPreferences.noPreferences))).toString

    "return Right with users when users are returned" in new Setup {
     stubGetByEmailsReturnsResponse(emails, validResponseString)

      val result: Either[Throwable, List[UserResponse]] = await(underTest.getByEmail(emails))

      result match {
        case Right(_: List[UserResponse]) => succeed
        case _                            => fail
      }
    }

    "return Right when no users are returned" in new Setup {
      stubGetByEmailsReturnsResponse(emails, "[]")

      val result: Either[Throwable, List[UserResponse]] = await(underTest.getByEmail(emails))

      result match {
        case Right(_: List[UserResponse]) => succeed
        case _                            => fail
      }
    }

    "return Left when not found returned" in new Setup {
      stubGetByEmailsReturnsNoResponse(emails, NOT_FOUND)
      val result: Either[Throwable, List[UserResponse]] = await(underTest.getByEmail(emails))

      result match {
        case Left(_: NotFoundException) => succeed
        case _                          => fail
      }
    }

    "return Left when internal server error returned" in new Setup {
       stubGetByEmailsReturnsNoResponse(emails, INTERNAL_SERVER_ERROR)

      val result: Either[Throwable, List[UserResponse]] = await(underTest.getByEmail(emails))

      result match {
        case Left(_: Upstream5xxResponse) => succeed
        case _                            => fail
      }
    }

  }

  "createVerifiedUser" should {

    val email = "foo@bar.com"
    val firstName = "Joe"
    val lastName = "Bloggs"
    val emailPreferences: Map[ApiCategory, List[ServiceName]] = Map.empty
    val populatedEmailPreferences: Map[ApiCategory, List[ServiceName]] =
      Map(
      ApiCategory.AGENTS -> List(ServiceName("service1"), ServiceName("service2")),
      ApiCategory.CHARITIES -> List(ServiceName("service1"), ServiceName("service2"))
      )
    val importUserRequestObj = ImportUserRequest(email, firstName, lastName, emailPreferences)

    "return CreatedUserResult when call to tpd returns CREATED" in new Setup {

      
      stubCreateVerifiedUserSuccess(email, firstName, lastName, userId, populatedEmailPreferences, CREATED)

      val result: CreateVerifiedUserResult = await(underTest.createVerifiedUser(importUserRequestObj.copy(emailPreferences = populatedEmailPreferences)))

      result match {
        case response: CreatedUserResult => response.userResponse mustBe userResponse
        case _                             => fail
      }
    }


    "return RetrievedUserResult when call to tpd returns OK" in new Setup {
      stubCreateVerifiedUserSuccess(email, firstName, lastName, userId, emailPreferences, OK)

      val result: CreateVerifiedUserResult = await(underTest.createVerifiedUser(importUserRequestObj))

      result match {
        case response: RetrievedUserResult => response.userResponse mustBe userResponse
        case _                             => fail
      }
    }

    "return CreateVerifiedUserFailedResult when call to tpd returns CONFLICT" in new Setup {
      stubCreateVerifiedUserEmptyResponse(email, firstName, lastName, emailPreferences, CONFLICT)

      val result: CreateVerifiedUserResult = await(underTest.createVerifiedUser(importUserRequestObj))

      result match {
        case e: CreateVerifiedUserFailedResult => e.message mustBe s"POST of 'http://localhost:$wireMockPort/import-user' returned 409. Response body: ''"
        case _                  => fail
      }
    }

     "return CreateVerifiedUserFailedResult when call to tpd returns NO_CONTENT" in new Setup {
      stubCreateVerifiedUserEmptyResponse(email, firstName, lastName, emailPreferences, NO_CONTENT)

      val result: CreateVerifiedUserResult = await(underTest.createVerifiedUser(importUserRequestObj))

      result match {
        case e: CreateVerifiedUserFailedResult  => e.message mustBe s"Could not get or create user"
        case _                            => fail
      }
    }


    "return CreateVerifiedUserFailedResult when call to tpd returns INTERNAL_SERVER_ERROR" in new Setup {
      stubCreateVerifiedUserEmptyResponse(email, firstName, lastName, emailPreferences, INTERNAL_SERVER_ERROR)

      val result: CreateVerifiedUserResult = await(underTest.createVerifiedUser(importUserRequestObj))

      result match {
        case e: CreateVerifiedUserFailedResult  => e.message mustBe s"POST of 'http://localhost:$wireMockPort/import-user' returned 500. Response body: ''"
        case _                            => fail
      }
    }
  }

}
