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

package uk.gov.hmrc.apiplatformxmlservices.controllers

import org.scalatest.BeforeAndAfterEach
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{NOT_FOUND, OK}
import uk.gov.hmrc.apiplatformxmlservices.models.InternalXmlApi
import uk.gov.hmrc.apiplatformxmlservices.models.ExternalXmlApi._
import uk.gov.hmrc.apiplatformxmlservices.support.ServerBaseISpec

class ApiControllerISpec extends ServerBaseISpec with BeforeAndAfterEach   {

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]
  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort
      )

  val url = s"http://localhost:$port/api-platform-xml-services"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def callGetEndpoint(url: String): WSResponse =
    wsClient
      .url(url)
      .withFollowRedirects(false)
      .get()
      .futureValue

  "ApiController" when {

    "GET /xml/apis" should {

      "respond with 200 and return all stable Apis" in {
        val result = callGetEndpoint(s"$url/xml/apis")
        result.status mustBe OK
        result.body mustBe Json.toJson(stableExternalXmlApis).toString
      }

      "respond with 404 when invalid path" in {
        val result = callGetEndpoint(s"$url/xml")
        result.status mustBe NOT_FOUND
      }

    }

    "GET /xml/api/:name" should {
      val stableApiName = "Charities Online"
      val charitiesOnlineApi = stableExternalXmlApis.find(_.name == stableApiName)
      val retiredApiName = "Employment intermediaries"
      val employmentIntermediariesApi = InternalXmlApi.toExternalXmlApi(InternalXmlApi.xmlApis.find(_.name == retiredApiName).get)

      "respond with 200 and return the stable API" in {
        val result = callGetEndpoint(s"$url/xml/api/$stableApiName")
        result.status mustBe OK
        result.body mustBe Json.toJson(charitiesOnlineApi).toString
      }

      "respond with 200 and return the retired API" in {
        val result = callGetEndpoint(s"$url/xml/api/$retiredApiName")
        result.status mustBe OK
        result.body mustBe Json.toJson(employmentIntermediariesApi).toString
      }

      "respond with 404 when api not found" in {
        val result = callGetEndpoint(s"$url/xml/api/INVALID_API_NAME")
        result.status mustBe NOT_FOUND
      }
    }

    "GET /xml/api?serviceName=charities-online" should {
      val stableApiName = "charities-online"
      val charitiesOnlineApi = stableExternalXmlApis.find(_.serviceName.value == stableApiName)
      val retiredApiServiceName = "employment-intermediaries"
      val employmentIntermediariesApi = InternalXmlApi.toExternalXmlApi(InternalXmlApi.xmlApis.find(_.serviceName.value == retiredApiServiceName).get)

      "respond with 200 and return the stable API" in {
        val result = callGetEndpoint(s"$url/xml/api?serviceName=$stableApiName")
        result.status mustBe OK
        result.body mustBe Json.toJson(charitiesOnlineApi).toString
      }

      "respond with 200 and return the retired API" in {
        val result = callGetEndpoint(s"$url/xml/api?serviceName=$retiredApiServiceName")
        result.status mustBe OK
        result.body mustBe Json.toJson(employmentIntermediariesApi).toString
      }

      "respond with 404 when api not found" in {
        val result = callGetEndpoint(s"$url/xml/api?serviceName=INVALID_API_NAME")
        result.status mustBe NOT_FOUND
      }
    }
  }
}
