/*
 * Copyright 2020 HM Revenue & Customs
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
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector.FindUserIdResponse
import uk.gov.hmrc.apiplatformxmlservices.models.UserId
import uk.gov.hmrc.apiplatformxmlservices.support.{AwaitTestSupport, ServerBaseISpec}
import uk.gov.hmrc.http.HeaderCarrier

import java.{util => ju}

class ThirdPartyDeveloperConnectorSpec extends ServerBaseISpec with BeforeAndAfterEach with AwaitTestSupport {

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.third-party-developer.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort
      )

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val underTest: ThirdPartyDeveloperConnector = app.injector.instanceOf[ThirdPartyDeveloperConnector]
  }

  "findUserId" should {
    "return UserId and Email" in new Setup {
      val email = "foo@bar.com"
      val userId: UserId = UserId(ju.UUID.randomUUID())
      implicit val writes: OWrites[FindUserIdResponse] = Json.writes[FindUserIdResponse]

      stubFor(
        post(urlEqualTo("/developers/find-user-id"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(FindUserIdResponse(userId)).toString)
              .withHeader("Content-Type", "application/json")
          )
      )

      await(underTest.findUserId(email)) shouldBe OK

      verify(postRequestedFor(urlMatching(s"developers/find-user-id"))
        .withRequestBody(equalToJson(s"{ \"email\": \"$email\" }"))
        .withHeader(HeaderNames.CONTENT_LENGTH, equalTo("0")))
    }
  }
}
