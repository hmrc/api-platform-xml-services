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

package uk.gov.hmrc.apiplatformxmlservices.controllers

import org.scalatest.BeforeAndAfterEach

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, NOT_FOUND, OK}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiCategory

import uk.gov.hmrc.apiplatformxmlservices.models.{ErrorResponse, XmlApi}
import uk.gov.hmrc.apiplatformxmlservices.service.XmlApiService
import uk.gov.hmrc.apiplatformxmlservices.support.ServerBaseISpec

class ApiControllerISpec extends ServerBaseISpec with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled"                 -> true,
        "auditing.enabled"                -> false,
        "auditing.consumer.baseUri.host"  -> wireMockHost,
        "auditing.consumer.baseUri.port"  -> wireMockPort
      )

  val url = s"http://localhost:$port/api-platform-xml-services"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def callGetEndpoint(url: String, queryParams: List[(String, String)] = List.empty): WSResponse =
    wsClient
      .url(url)
      .withFollowRedirects(false)
      .withQueryStringParameters(queryParams: _*)
      .get()
      .futureValue

  trait Setup {

    val xmlApiService = new XmlApiService

    val stableApis                   = xmlApiService.getStableApis
    val unfilteredApis               = xmlApiService.getUnfilteredApis()
    val employmentIntermediariesJson = Json.toJson(unfilteredApis.filter(_.serviceName.value == "employment-intermediaries").head).toString
    val charitiesOnlineJson          = Json.toJson(stableApis.filter(_.serviceName.value == "charities-online").head).toString
  }

  "ApiController" when {

    "GET /xml/apis" should {

      "respond with 200 and return all stable Apis" in new Setup {
        val result = callGetEndpoint(s"$url/xml/apis")
        result.status mustBe OK
        result.body mustBe Json.toJson(stableApis).toString
      }

      "respond with 404 when invalid path" in {
        val result = callGetEndpoint(s"$url/xml")
        result.status mustBe NOT_FOUND
      }

    }

    "GET /xml/apis/filtered" should {

      "respond with 200 and return APIs for selected category" in new Setup {
        val result                     = callGetEndpoint(s"$url/xml/apis/filtered", List("categoryFilter" -> "PAYE"))
        result.status mustBe OK
        val expectedApis: List[XmlApi] = stableApis.filter(_.categories.getOrElse(Seq.empty).contains(ApiCategory.PAYE))
        result.body mustBe Json.toJson(expectedApis).toString
      }

      "respond with 200 and return nothing when category is not found" in new Setup {
        val result = callGetEndpoint(s"$url/xml/apis/filtered", List("categoryFilter" -> "VAT"))
        result.status mustBe OK

        result.body mustBe Json.toJson(List.empty[XmlApi]).toString
      }

      "respond with 400 when invalid category" in {
        val badCategory = "bad"
        val result      = callGetEndpoint(s"$url/xml/apis/filtered", List("categoryFilter" -> badCategory))

        result.status mustBe BAD_REQUEST
        val response = Json.parse(result.body).as[ErrorResponse]
        response.errors.head.message mustBe s"Unable to bind category $badCategory"
      }

    }

    "GET /xml/api/:name" should {
      val stableApiName  = "Charities Online"
      val retiredApiName = "Employment intermediaries"

      "respond with 200 and return the stable API" in new Setup {
        val result = callGetEndpoint(s"$url/xml/api/$stableApiName")
        result.status mustBe OK
        result.body mustBe charitiesOnlineJson
      }

      "respond with 200 and return the retired API" in new Setup {
        val result = callGetEndpoint(s"$url/xml/api/$retiredApiName")
        result.status mustBe OK
        result.body mustBe employmentIntermediariesJson
      }

      "respond with 404 when api not found" in {
        val result = callGetEndpoint(s"$url/xml/api/INVALID_API_NAME")
        result.status mustBe NOT_FOUND
      }
    }

    "GET /xml/api?serviceName=charities-online" should {
      val stableApiName         = "charities-online"
      val retiredApiServiceName = "employment-intermediaries"

      "respond with 200 and return the stable API" in new Setup {
        val result = callGetEndpoint(s"$url/xml/api?serviceName=$stableApiName")
        result.status mustBe OK
        result.body mustBe charitiesOnlineJson
      }

      "respond with 200 and return the retired API" in new Setup {
        val result = callGetEndpoint(s"$url/xml/api?serviceName=$retiredApiServiceName")
        result.status mustBe OK
        result.body mustBe employmentIntermediariesJson
      }

      "respond with 404 when api not found" in {
        val result = callGetEndpoint(s"$url/xml/api?serviceName=INVALID_API_NAME")
        result.status mustBe NOT_FOUND
      }
    }
  }
}
