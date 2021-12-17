/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, CONFLICT, CREATED, NOT_FOUND, OK}
import uk.gov.hmrc.apiplatformxmlservices.models.JsonFormatters._
import uk.gov.hmrc.apiplatformxmlservices.models.{CreateOrganisationRequest, Organisation, OrganisationId, VendorId}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.support.{AwaitTestSupport, MongoApp, ServerBaseISpec}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.UUID
import uk.gov.hmrc.apiplatformxmlservices.models.OrganisationName

class OrganisationControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with AwaitTestSupport with MongoApp[Organisation] {

  override protected def repository: PlayMongoRepository[Organisation] = app.injector.instanceOf[OrganisationRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
  }

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "organisation.vendorId.startingValue" -> 9000,
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  val url = s"http://localhost:$port/api-platform-xml-services"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val validHeaders = List(CONTENT_TYPE -> "application/json")

  def callGetEndpoint(url: String): WSResponse =
    wsClient
      .url(url)
      .withFollowRedirects(false)
      .get()
      .futureValue

  def callDeleteEndpoint(url: String): WSResponse =
    wsClient
      .url(url)
      .withFollowRedirects(false)
      .delete
      .futureValue

  def callPostEndpoint(url: String, body: String): WSResponse =
    wsClient
      .url(url)
      .withHttpHeaders(validHeaders: _*)
      .withFollowRedirects(false)
      .post(body)
      .futureValue

  def callPutEndpoint(url: String, body: String): WSResponse =
    wsClient
      .url(url)
      .withHttpHeaders(validHeaders: _*)
      .withFollowRedirects(false)
      .put(body)
      .futureValue

  trait Setup {
    def orgRepo: OrganisationRepository = app.injector.instanceOf[OrganisationRepository]

    def getUuid() = UUID.randomUUID()

    val organisation = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(2001), name = OrganisationName("I am the first"))
    val organisation2 = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(2002), name = OrganisationName("Organisation Name2"))
    val updatedOrgWithDuplicate = Organisation(organisationId = organisation.organisationId, organisation2.vendorId, name = OrganisationName("Updated Organisation Name"))
    val createOrganisationRequest = CreateOrganisationRequest(organisationName = OrganisationName("Organisation Name"))
    val organisationIdValue = organisation.organisationId.value
    val vendorIdValue = organisation.vendorId.value
    val orgAsJsonString = Json.toJson(organisation).toString

    val invalidOrgString =
      """{
        |    "organisationId": "dd5bda96-46da-11ec-81d3-0242ac130003",
        |    "vendorId": INVALID_VENDOR_ID,
        |    "name": "Organisation Name 3"
        |}""".stripMargin
    val createOrganisationRequestAsString = Json.toJson(createOrganisationRequest).toString
  }

  "OrganisationController" when {

    "GET /organisations/:organisationId" should {

      "respond with 200 and return Organisation" in new Setup {
        await(orgRepo.create(organisation))
        val result = callGetEndpoint(s"$url/organisations/${organisationIdValue}")
        result.status mustBe OK
        result.body mustBe Json.toJson(organisation).toString
      }

      "respond with 404 when OrganisationId not found" in new Setup {
        val result = callGetEndpoint(s"$url/organisations/${organisationIdValue}")
        result.status mustBe NOT_FOUND
        result.body mustBe s"XML Organisation with organisationId ${organisationIdValue} not found."
      }

      "respond with 400 when invalid organisationId" in {
        val result = callGetEndpoint(s"$url/organisations/2233-3322-2222")
        result.status mustBe BAD_REQUEST
      }
    }

    "GET /organisations with query parameters" should {

      "respond 200 and return matches when valid vendorID provided" in new Setup {
        await(orgRepo.create(organisation))
        val result = callGetEndpoint(s"$url/organisations?vendorId=$vendorIdValue")
        result.status mustBe OK
        result.body mustBe Json.toJson(List(organisation)).toString
      }

      "respond 200 and return matches when valid partial organisation name provided" in new Setup {
        await(orgRepo.create(organisation))
        await(orgRepo.create(organisation2))

        val result = callGetEndpoint(s"$url/organisations?organisationName=I am the")
        result.status mustBe OK
        result.body mustBe Json.toJson(List(organisation)).toString
      }

      "respond 200 and return matches when valid full organisation name provided" in new Setup {
        await(orgRepo.create(organisation))
        await(orgRepo.create(organisation2))

        val result = callGetEndpoint(s"$url/organisations?organisationName=${organisation.name.value}")
        result.status mustBe OK
        result.body mustBe Json.toJson(List(organisation)).toString
      }

      "respond with 200 and return all Organisations when no vendorId is provided" in new Setup {
        await(orgRepo.create(organisation))
        await(orgRepo.create(organisation2))
        val result = callGetEndpoint(s"$url/organisations")
        result.status mustBe OK
        result.body mustBe Json.toJson(List(organisation, organisation2)).toString
      }

      "respond with 404 when VendorId not found" in new Setup {
        val result = callGetEndpoint(s"$url/organisations?vendorId=${vendorIdValue}")
        result.status mustBe NOT_FOUND
        result.body mustBe s"XML Organisation with vendorId ${vendorIdValue} not found."
      }

      "respond 200 and empty list when OrganisationName not found" in new Setup {
        await(orgRepo.create(organisation2))
        val result = callGetEndpoint(s"$url/organisations?organisationName=unknown")
        result.status mustBe OK
        result.body mustBe "[]"
      }

      "respond with 400 when invalid vendorId" in {
        val result = callGetEndpoint(s"$url/organisations?vendorId=INVALID-LONG")
        result.status mustBe BAD_REQUEST
      }
    }

    "DELETE /organisations/:organisationId" should {

      "respond with 204 if Organisation was deleted" in new Setup {
        await(orgRepo.create(organisation))
        val result = callDeleteEndpoint(s"$url/organisations/${organisationIdValue}")
        result.status mustBe 204
      }

      "respond with 404 when OrganisationId not found" in new Setup {
        val result = callDeleteEndpoint(s"$url/organisations/${organisationIdValue}")
        result.status mustBe NOT_FOUND
        result.body mustBe s"XML Organisation with organisationId ${organisationIdValue} not found."
      }
    }

    "POST /organisations" should {

      "respond with 201 if Organisation was created" in new Setup {
        val result = callPostEndpoint(s"$url/organisations", createOrganisationRequestAsString)

        result.status mustBe CREATED
        val createdOrganisation = Json.parse(result.body).as[Organisation]
        createdOrganisation.name mustBe createOrganisationRequest.organisationName
        createdOrganisation.vendorId mustBe VendorId(9000)
      }

      "respond with 400 if request body is not json" in new Setup {
        val result = callPostEndpoint(s"$url/organisations", "INVALID BODY")
        result.status mustBe BAD_REQUEST
        result.body contains "Invalid Json: Unrecognized token 'INVALID'"
      }
    }

    "PUT /organisations" should {

      "respond with 200 if Organisation was updated" in new Setup {
        await(orgRepo.create(organisation))
        val result = callPutEndpoint(s"$url/organisations", orgAsJsonString)
        result.status mustBe OK
      }

      "respond with 409 if attempt to update Organisation with another Organisations VendorId" in new Setup {
        await(orgRepo.create(organisation))
        await(orgRepo.create(organisation2))
        val result = callPutEndpoint(s"$url/organisations", Json.toJson(updatedOrgWithDuplicate).toString)
        result.status mustBe CONFLICT
      }

      "respond with 400 if request body is not json" in new Setup {
        val result = callPutEndpoint(s"$url/organisations", "INVALID BODY")
        result.status mustBe BAD_REQUEST
        result.body contains "Invalid Json: Unrecognized token 'INVALID'"
      }
      "respond with 400 if request body is invalid" in new Setup {
        val result = callPutEndpoint(s"$url/organisations", invalidOrgString)
        result.status mustBe BAD_REQUEST
        result.body contains "Invalid Json: Unrecognized token 'INVALID_VENDOR_ID'"
      }
      "respond with 404 if Organisation does not exist" in new Setup {
        val result = callPutEndpoint(s"$url/organisations", orgAsJsonString)
        result.status mustBe NOT_FOUND
        result.body mustBe s"Could not find Organisation with ID ${organisation.organisationId.value}"
      }
    }
  }
}
