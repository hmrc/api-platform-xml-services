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

package uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.controllers

import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.BAD_REQUEST
import play.api.test.Helpers.OK
import uk.gov.hmrc.apiplatformxmlservices.models.{Collaborator, JsonFormatters, Organisation, OrganisationId, OrganisationName, OrganisationWithNameAndVendorId, UserId, VendorId}
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators.RemoveCollaboratorRequest
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.{BulkUploadOrganisationsRequest, CSVJsonFormats}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.support.MongoApp
import uk.gov.hmrc.apiplatformxmlservices.support.ServerBaseISpec
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.UUID

class CsvUploadControllerISpec extends ServerBaseISpec with BeforeAndAfterEach  with MongoApp[Organisation] with JsonFormatters with CSVJsonFormats {

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
        "microservice.services.third-party-developer.host" -> wireMockHost,
        "microservice.services.third-party-developer.port" -> wireMockPort,
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

    val userId: UserId = UserId(getUuid())

    val email = "foo@bar.com"
    val gatekeeperUserId = "John Doe"
    val organisation = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(2001), name = OrganisationName("I am the first"))
    val organisationWithCollaborators = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(userId, email))
    val organisation2 = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(2002), name = OrganisationName("Organisation Name2"))
    val updatedOrgWithDuplicate = Organisation(organisationId = organisation.organisationId, organisation2.vendorId, name = OrganisationName("Updated Organisation Name"))

    val removeCollaboratorRequest = RemoveCollaboratorRequest(email, gatekeeperUserId)
    val organisationIdValue = organisation.organisationId.value
    val vendorIdValue = organisation.vendorId.value
    val orgAsJsonString = Json.toJson(organisation).toString

    val invalidOrgString =
      """{
        |    "organisationId": "dd5bda96-46da-11ec-81d3-0242ac130003",
        |    "vendorId": INVALID_VENDOR_ID,
        |    "name": "Organisation Name 3"
        |}""".stripMargin

  }

  "CsvUploadController" when {

    "POST /csvupload/bulk" should {

      "respond with 400 if request body is not valid" in new Setup {

        val result = callPostEndpoint(s"$url/csvupload/bulkorganisations", "\"organisations\": [{}]")

        result.status mustBe BAD_REQUEST

        withClue(s"response body not as expected: ${result.body}") {
          result.body.contains("Invalid Json: Unexpected character") mustBe true
        }
      }

      "respond with 200 if request body is valid" in new Setup {

        val orgOne = OrganisationWithNameAndVendorId(name = OrganisationName("OrgOne"), vendorId = VendorId(1))
        val orgTwo = OrganisationWithNameAndVendorId(name = OrganisationName("OrgTwo"), vendorId = VendorId(2))
        val bulkUploadOrganisationsRequest = BulkUploadOrganisationsRequest(Seq(orgOne, orgTwo))
        val payload = Json.toJson(bulkUploadOrganisationsRequest).toString

        val result = callPostEndpoint(s"$url/csvupload/bulkorganisations", payload)
        result.status mustBe OK
      }
    }
  }

  "POST /csvupload/bulkusers" should {

    "respond with 400 if request body is not valid" in new Setup {

      val result = callPostEndpoint(s"$url/csvupload/bulkusers", "\"organisations\": [{}]")

      result.status mustBe BAD_REQUEST

      withClue(s"response body not as expected: ${result.body}") {
        result.body.contains("Invalid Json: Unexpected character") mustBe true
      }
    }
  }
}
