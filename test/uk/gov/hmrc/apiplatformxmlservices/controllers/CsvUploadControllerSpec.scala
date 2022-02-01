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

import org.mockito.scalatest.MockitoSugar
import org.mongodb.scala.{MongoCommandException, ServerAddress}
import org.mongodb.scala.bson.BsonDocument
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.service.OrganisationService
import uk.gov.hmrc.apiplatformxmlservices.models.JsonFormatters._
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.apiplatformxmlservices.service.UploadService

class CsvUploadControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfterEach {

  private val mockOrgService = mock[OrganisationService]
  private val mockUploadervice = mock[UploadService]

  private val controller = new CsvUploadController(
    mockOrgService,
    mockUploadervice,
    Helpers.stubControllerComponents()
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockOrgService)
  }

  trait Setup {

    val jsonMediaType = "application/json"
    def getUuid() = UUID.randomUUID()
    val organisationId = OrganisationId(getUuid)
    val organisation = Organisation(organisationId, vendorId = VendorId(2001), name = OrganisationName("Organisation Name"))
    val userId = UserId(UUID.randomUUID())
    val email = "foo@bar.com"
    val coreUserDetail = CoreUserDetail(userId, email)
    val addCollaboratordRequestObj = AddCollaboratorRequest(email)
    val updatedOrganisationName = OrganisationName("updated name")
    val updateOrganisationDetailsRequestObj = UpdateOrganisationDetailsRequest(updatedOrganisationName)
    val organisationWithCollaborator = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(userId, email))

    val orgOne = OrganisationWithNameAndVendorId(name = OrganisationName("OrgOne"), vendorId = VendorId(1))
    val orgTwo = OrganisationWithNameAndVendorId(name = OrganisationName("OrgTwo"), vendorId = VendorId(2))
    val bulkFindAndCreateOrUpdateRequestObj = BulkFindAndCreateOrUpdateRequest(Seq(orgOne, orgTwo))

    val bulkFindAndCreateOrUpdateRequest =
      FakeRequest("POST", s"/csvupload/bulk").withBody(Json.toJson(bulkFindAndCreateOrUpdateRequestObj))

  }

  "bulkFindAndCreateOrUpdate" should {

    "return 200 when service returns an exception" in new Setup {
      when(mockOrgService.findAndCreateOrUpdate(*[OrganisationName], *[VendorId])).thenReturn(Future.successful(Left(new InternalServerException("Organisation does not exist"))))
      val result: Future[Result] = controller.bulkFindAndCreateOrUpdate()(bulkFindAndCreateOrUpdateRequest)
      status(result) shouldBe Status.OK

      verify(mockOrgService, times(2)).findAndCreateOrUpdate(*[OrganisationName], *[VendorId])
    }

    "return 200 when service returns created Organisation" in new Setup {
      when(mockOrgService.findAndCreateOrUpdate(*[OrganisationName], *[VendorId])).thenReturn(Future.successful(Right(organisation)))
      val result: Future[Result] = controller.bulkFindAndCreateOrUpdate()(bulkFindAndCreateOrUpdateRequest)
      status(result) shouldBe Status.OK

      verify(mockOrgService, times(2)).findAndCreateOrUpdate(*[OrganisationName], *[VendorId])
    }
  }

}
