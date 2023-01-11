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
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, NOT_FOUND, OK}
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators.{AddCollaboratorRequest, RemoveCollaboratorRequest}
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.{EmailPreferences, UserResponse}
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.JsonFormatters._
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.support.{MongoApp, ServerBaseISpec}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.UUID

class TeamMemberControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with MongoApp[Organisation] with JsonFormatters {

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

  val validHeaders = List(CONTENT_TYPE -> "application/json")

  def callPostEndpoint(url: String, body: String): WSResponse =
    wsClient
      .url(url)
      .withHttpHeaders(validHeaders: _*)
      .withFollowRedirects(false)
      .post(body)
      .futureValue

  trait Setup {
    def orgRepo: OrganisationRepository = app.injector.instanceOf[OrganisationRepository]

    def getUuid() = UUID.randomUUID()

    val userId: UserId = UserId(getUuid())

    val email                         = "foo@bar.com"
    val firstName                     = "bob"
    val lastName                      = "hope"
    val gatekeeperUserId              = "John Doe"
    val organisation                  = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(2001), name = OrganisationName("I am the first"))
    val organisationWithCollaborators = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(userId, email))
    val organisation2                 = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(2002), name = OrganisationName("Organisation Name2"))
    val updatedOrgWithDuplicate       = Organisation(organisationId = organisation.organisationId, organisation2.vendorId, name = OrganisationName("Updated Organisation Name"))
    val createOrganisationRequest     = CreateOrganisationRequest(organisationName = OrganisationName("   Organisation Name   "), email, firstName, lastName)
    val addCollaboratorRequest        = AddCollaboratorRequest(email, firstName, lastName)
    val removeCollaboratorRequest     = RemoveCollaboratorRequest(email, gatekeeperUserId)
    val organisationIdValue           = organisation.organisationId.value
    val vendorIdValue                 = organisation.vendorId.value
    val orgAsJsonString               = Json.toJson(organisation).toString

    val invalidOrgString                  =
      """{
        |    "organisationId": "dd5bda96-46da-11ec-81d3-0242ac130003",
        |    "vendorId": INVALID_VENDOR_ID,
        |    "name": "Organisation Name 3"
        |}""".stripMargin
    val createOrganisationRequestAsString = Json.toJson(createOrganisationRequest).toString
    val addCollaboratorRequestAsString    = Json.toJson(addCollaboratorRequest).toString
    val removeCollaboratorRequestAsString = Json.toJson(removeCollaboratorRequest).toString

    def stubThirdPartyDeveloperConnectorWithoutBody(status: Int) = {
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

    def stubThirdPartyDeveloperConnectorWithBody(userId: UserId, email: String, firstName: String, lastName: String, status: Int) = {
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

  "TeamMemberController" when {

    "POST /organisations/:organisationId/add-collaborator" should {

      "respond with 400 if request body is not json" in new Setup {
        val result = callPostEndpoint(s"$url/organisations/${organisation.organisationId.value}/add-collaborator", "{\"someinvalidkey\": \"something\"}")
        result.status mustBe BAD_REQUEST
        withClue(s"response body not as expected: ${result.body}") {
          result.body.startsWith("Invalid AddCollaboratorRequest payload:") mustBe true
        }
      }

      "respond with 404 if organisationId is not provided" in new Setup {
        val result: WSResponse = callPostEndpoint(s"$url/organisations//add-collaborator", addCollaboratorRequestAsString)
        result.status mustBe NOT_FOUND
        result.body.contains("URI not found") mustBe true
      }

      "respond with 400 if organisationId is not a uuid" in new Setup {
        val organisationId = ":alsjdflaksjdf"
        val result         = callPostEndpoint(s"$url/organisations/$organisationId/add-collaborator", addCollaboratorRequestAsString)

        result.status mustBe BAD_REQUEST
        val response = Json.parse(result.body).as[ErrorResponse]
        response.errors.head.message mustBe s"Cannot accept $organisationId as OrganisationId"
      }

      "respond with 404 if organisationId exists" in new Setup {
        val result = callPostEndpoint(s"$url/organisations/${organisation.organisationId.value}/add-collaborator", addCollaboratorRequestAsString)
        result.body mustBe s"Failed to get organisation for Id: ${organisation.organisationId.value}"
        result.status mustBe NOT_FOUND
      }

      "respond with 400 if third party developer connector returns and error" in new Setup {
        await(orgRepo.createOrUpdate(organisation))
        stubThirdPartyDeveloperConnectorWithBody(userId, email, firstName, lastName, BAD_REQUEST)
        val result = callPostEndpoint(s"$url/organisations/${organisation.organisationId.value}/add-collaborator", addCollaboratorRequestAsString)
        result.status mustBe BAD_REQUEST
        result.body.contains("Bad Request") mustBe true
      }

      "respond with 200 if organisationId exists" in new Setup {
        await(orgRepo.createOrUpdate(organisation))
        stubThirdPartyDeveloperConnectorWithBody(userId, email, firstName, lastName, OK)
        val result = callPostEndpoint(s"$url/organisations/${organisation.organisationId.value}/add-collaborator", addCollaboratorRequestAsString)
        result.status mustBe OK
        result.body mustBe Json.toJson(organisationWithCollaborators).toString()
      }
    }
  }

  "POST /organisations/:organisationId/remove-collaborator" should {

    "respond with 200 if organisationId exists and delete successful" in new Setup {
      await(orgRepo.createOrUpdate(organisationWithCollaborators))

      val result = callPostEndpoint(s"$url/organisations/${organisationWithCollaborators.organisationId.value}/remove-collaborator", removeCollaboratorRequestAsString)
      result.status mustBe OK
      result.body mustBe Json.toJson(organisation).toString()
    }

    "respond with 404 if organisationId exists but collaborator is not associated" in new Setup {
      await(orgRepo.createOrUpdate(organisation))
      val result = callPostEndpoint(s"$url/organisations/${organisationWithCollaborators.organisationId.value}/remove-collaborator", removeCollaboratorRequestAsString)
      result.status mustBe NOT_FOUND
      result.body mustBe "Collaborator not found on Organisation"
    }

    "respond with 400 if request body is not json" in new Setup {
      val result = callPostEndpoint(s"$url/organisations/${organisation.organisationId.value}/remove-collaborator", "{\"someinvalidkey\": \"something\"}")
      result.status mustBe BAD_REQUEST
      withClue(s"response body not as expected: ${result.body}") {
        result.body.startsWith("Invalid RemoveCollaboratorRequest payload:") mustBe true
      }
    }

  }

}
