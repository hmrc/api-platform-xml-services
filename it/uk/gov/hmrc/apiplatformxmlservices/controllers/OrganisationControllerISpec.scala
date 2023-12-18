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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.BeforeAndAfterEach

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, CREATED, INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import uk.gov.hmrc.apiplatformxmlservices.common.data.CommonTestData
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.{EmailPreferences, UserResponse}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.support.{MongoApp, ServerBaseISpec}

class OrganisationControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with MongoApp[Organisation] {

  override protected def repository: PlayMongoRepository[Organisation] = app.injector.instanceOf[OrganisationRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
  }

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "organisation.vendorId.startingValue"              -> 9000,
        "microservice.services.auth.port"                  -> wireMockPort,
        "metrics.enabled"                                  -> true,
        "auditing.enabled"                                 -> false,
        "auditing.consumer.baseUri.host"                   -> wireMockHost,
        "auditing.consumer.baseUri.port"                   -> wireMockPort,
        "microservice.services.third-party-developer.host" -> wireMockHost,
        "microservice.services.third-party-developer.port" -> wireMockPort,
        "mongodb.uri"                                      -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  val url = s"http://localhost:$port/api-platform-xml-services"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val validHeaders: List[(String, String)] = List(CONTENT_TYPE -> "application/json")

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
      .delete()
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

  trait Setup extends CommonTestData {
    def orgRepo: OrganisationRepository = app.injector.instanceOf[OrganisationRepository]

    val gatekeeperUserId = "John Doe"

    val organisationWithCollaborators = anOrganisation.copy(collaborators = List(aCollaborator))
    val organisation2                 = Organisation(organisationId = OrganisationId.random, vendorId = VendorId(2002), name = OrganisationName("Organisation Name2"))
    val updatedOrgWithDuplicate       = Organisation(organisationId = anOrganisationId, organisation2.vendorId, name = OrganisationName("Updated Organisation Name"))
    val createOrganisationRequest2    = CreateOrganisationRequest(organisationName = anOrganisationName, anEmailAddress, aFirstName, aLastName)
    val addCollaboratorRequest        = AddCollaboratorRequest(anEmailAddress, aFirstName, aLastName)
    val removeCollaboratorRequest     = RemoveCollaboratorRequest(anEmailAddress, gatekeeperUserId)
    val organisationIdValue           = anOrganisationId.value
    val vendorIdValue                 = aVendorId.value
    val orgAsJsonString               = Json.toJson(anOrganisation).toString

    val invalidOrgString                  =
      """{
        |    "organisationId": "dd5bda96-46da-11ec-81d3-0242ac130003",
        |    "vendorId": INVALID_VENDOR_ID,
        |    "name": "Organisation Name 3"
        |}""".stripMargin
    val createOrganisationRequestAsString = Json.toJson(createOrganisationRequest2).toString
    val addCollaboratorRequestAsString    = Json.toJson(addCollaboratorRequest).toString
    val removeCollaboratorRequestAsString = Json.toJson(removeCollaboratorRequest).toString

    def stubThirdPartyDeveloperConnectorWithoutBody(status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo("/developers/user-id"))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody("{}")
              .withHeader("Content-Type", "application/json")
          )
      )
    }

    def stubThirdPartyDeveloperConnectorWithBody(userId: UserId, email: LaxEmailAddress, firstName: String, lastName: String, status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo("/import-user"))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(Json.toJson(UserResponse(email, firstName, lastName, verified = true, userId, EmailPreferences.noPreferences)).toString)
              .withHeader("Content-Type", "application/json")
          )
      )
    }

  }

  "OrganisationController" when {

    "GET /organisations/:organisationId" should {

      "respond with 200 and return Organisation" in new Setup {
        await(orgRepo.createOrUpdate(anOrganisation))
        val result = callGetEndpoint(s"$url/organisations/${organisationIdValue}")
        result.status mustBe OK
        result.body mustBe Json.toJson(anOrganisation).toString
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
        await(orgRepo.createOrUpdate(anOrganisation))
        val result = callGetEndpoint(s"$url/organisations?vendorId=$vendorIdValue&sortBy=ORGANISATION_NAME")
        result.status mustBe OK
        result.body mustBe Json.toJson(List(anOrganisation)).toString
      }

      "respond 200 and return matches when valid userId provided" in new Setup {
        await(orgRepo.createOrUpdate(organisationWithCollaborators))
        val result = callGetEndpoint(s"$url/organisations?userId=$aUserId&sortBy=ORGANISATION_NAME")
        result.status mustBe OK
        result.body mustBe Json.toJson(List(organisationWithCollaborators)).toString
      }

      "respond 400 invalid userId provided" in new Setup {
        await(orgRepo.createOrUpdate(organisationWithCollaborators))
        val result = callGetEndpoint(s"$url/organisations?userId=12343222&sortBy=ORGANISATION_NAME")
        result.status mustBe BAD_REQUEST
      }

      "respond 400 and when sortBy param is invalid" in new Setup {
        await(orgRepo.createOrUpdate(anOrganisation))
        val result = callGetEndpoint(s"$url/organisations?vendorId=$vendorIdValue&sortBy=UNKNOWN")
        result.status mustBe BAD_REQUEST

      }

      "respond 200 and return matches when valid partial organisation name provided" in new Setup {
        await(orgRepo.createOrUpdate(anOrganisation))
        await(orgRepo.createOrUpdate(organisation2))

        val result = callGetEndpoint(s"$url/organisations?organisationName=$anOrganisationName")
        result.status mustBe OK
        result.body mustBe Json.toJson(List(anOrganisation)).toString
      }

      "respond 200 and return matches when valid full organisation name provided" in new Setup {
        await(orgRepo.createOrUpdate(anOrganisation))
        await(orgRepo.createOrUpdate(organisation2))

        val result = callGetEndpoint(s"$url/organisations?organisationName=${anOrganisationName.value}")
        result.status mustBe OK
        result.body mustBe Json.toJson(List(anOrganisation)).toString
      }

      "respond with 200 and return all Organisations when no vendorId is provided" in new Setup {
        await(orgRepo.createOrUpdate(anOrganisation))
        await(orgRepo.createOrUpdate(organisation2))
        val result        = callGetEndpoint(s"$url/organisations")
        result.status mustBe OK
        val organisations = Json.parse(result.body).as[List[Organisation]]
        organisations must contain only (anOrganisation, organisation2)
      }

      "respond with 404 when VendorId not found" in new Setup {
        val result = callGetEndpoint(s"$url/organisations?vendorId=${vendorIdValue}")
        result.status mustBe NOT_FOUND
        result.body mustBe s"XML Organisation with vendorId ${vendorIdValue} not found."
      }

      "respond 200 and empty list when OrganisationName not found" in new Setup {
        await(orgRepo.createOrUpdate(organisation2))
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
        await(orgRepo.createOrUpdate(anOrganisation))
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
        stubThirdPartyDeveloperConnectorWithBody(aUserId, anEmailAddress, aFirstName, aLastName, OK)
        val result = callPostEndpoint(s"$url/organisations", createOrganisationRequestAsString)

        result.status mustBe CREATED

        val createdOrganisation = Json.parse(result.body).as[Organisation]
        createdOrganisation.name.value mustBe createOrganisationRequest.organisationName.value.trim()
        createdOrganisation.vendorId mustBe VendorId(9000)
      }

      "respond with 400 if request body is not json" in new Setup {
        val result = callPostEndpoint(s"$url/organisations", "{\"someinvalidkey\": \"something\"}")
        result.status mustBe BAD_REQUEST
        withClue(s"response body not as expected: ${result.body}") {
          result.body.startsWith("Invalid CreateOrganisationRequest payload:") mustBe true
        }
      }
    }

    "PUT /organisations" should {

      "respond with 200 if Organisation was updated" in new Setup {
        await(orgRepo.createOrUpdate(anOrganisation))
        val result = callPutEndpoint(s"$url/organisations", orgAsJsonString)
        result.status mustBe OK
      }

      "respond with 404 if attempt to update Organisation with another Organisations VendorId" in new Setup {
        await(orgRepo.createOrUpdate(anOrganisation))
        await(orgRepo.createOrUpdate(organisation2))
        val result = callPutEndpoint(s"$url/organisations", Json.toJson(updatedOrgWithDuplicate).toString)
        result.status mustBe NOT_FOUND
      }

      "respond with 400 if request body is not json" in new Setup {
        val result = callPutEndpoint(s"$url/organisations", "{\"someinvalidkey\": \"something\"}")
        result.status mustBe BAD_REQUEST
        withClue(s"response body not as expected: ${result.body}") {
          result.body.startsWith("Invalid Organisation payload:") mustBe true
        }
      }

      "respond with 400 if request body is invalid" in new Setup {
        val result = callPutEndpoint(s"$url/organisations", invalidOrgString)
        result.status mustBe BAD_REQUEST
        withClue(s"response body not as expected: ${result.body}") {
          result.body.contains("Invalid Json: Unrecognized token 'INVALID_VENDOR_ID'") mustBe true
        }
      }

      "respond with 200 and do upsert if Organisation does not exist" in new Setup {
        val result = callPutEndpoint(s"$url/organisations", orgAsJsonString)
        result.status mustBe OK
      }
    }

    "POST /organisations/:organisationId" should {
      "respond with 200 when update details is successful" in new Setup {
        await(orgRepo.createOrUpdate(anOrganisation))
        val result = callPostEndpoint(s"$url/organisations/$anOrganisationId", "{\"organisationName\": \"newName\"}")
        result.status mustBe OK

      }

      "respond with 500 when update fails" in new Setup {
        val result = callPostEndpoint(s"$url/organisations/$anOrganisationId", "{\"organisationName\": \"newName\"}")
        result.status mustBe INTERNAL_SERVER_ERROR

      }
    }

  }
}
