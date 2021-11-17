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

import org.mockito.scalatest.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.service.OrganisationService
import uk.gov.hmrc.apiplatformxmlservices.models.JsonFormatters._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrganisationControllerSpec extends WordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfterEach {

  private val fakeRequest = FakeRequest("GET", "/organisations")

  private val mockOrgService = mock[OrganisationService]

  private val controller = new OrganisationController(
    mockOrgService,
    Helpers.stubControllerComponents()
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockOrgService)
  }

  trait Setup {

    val jsonMediaType = "application/json"
    def getUuid() = UUID.randomUUID()

    val organisation = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(2001), name = "Organisation Name")
    val organisationIdValue = organisation.organisationId.value
    val vendorIdValue = organisation.vendorId.value
    val orgAsJsonString = Json.toJson(organisation).toString
    val invalidOrgString = """{
                             |    "organisationId": "dd5bda96-46da-11ec-81d3-0242ac130003",
                             |    "vendorId": INVALID_VENDOR_ID,
                             |    "name": "Organisation Name 3"
                             |}""".stripMargin


    val fakeDeleteRequest = FakeRequest("DELETE", s"/organisations/${organisationIdValue}")

  }

  "GET /organisations/:organisationId" should {
    "return 200" in new Setup {
      when(mockOrgService.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(Some(organisation)))
      val result: Future[Result] = controller.findByOrgId(organisation.organisationId)(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return 404 when no results returned" in new Setup {
      when(mockOrgService.findByOrgId(*[OrganisationId])).thenReturn(Future.successful(None))
      val result: Future[Result] = controller.findByOrgId(OrganisationId(getUuid))(fakeRequest)
      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "GET /organisations/:vendorId" should {
    "return 200" in new Setup {
      when(mockOrgService.findByVendorId(*[VendorId])).thenReturn(Future.successful(Some(organisation)))
      val result: Future[Result] = controller.findByVendorId(organisation.vendorId)(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return 404 when no results returned" in new Setup {
      when(mockOrgService.findByVendorId(*[VendorId])).thenReturn(Future.successful(None))
      val result: Future[Result] = controller.findByVendorId(VendorId(9000))(fakeRequest)
      status(result) shouldBe Status.NOT_FOUND
    }
  }

}
