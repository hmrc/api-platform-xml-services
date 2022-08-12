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

package uk.gov.hmrc.apiplatformxmlservices.service

import org.mockito.scalatest.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.apiplatformxmlservices.models.common.ApiCategory


class XmlApiServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {


  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  /*
   * The data for this test is production data in the file $project_dir/resources/xml_apis.json
   * The data is not stored in a database because it is relatively static.
   * Tests may break as the data changes over time.
   */  trait Setup {
    val inTest = new XmlApiService
    val unfilteredApis = inTest.getUnfilteredApis()
    val stableApis = inTest.getStableApis()

    val stableApiServiceName = "paye-online"
    val stableApi = unfilteredApis.filter(_.serviceName.value == stableApiServiceName).head

    val stableApiCategories = stableApi.categories.getOrElse(fail("Data has changed. Please choose another stable API."))
    val stableApiCategory = stableApiCategories.head  // PAYE

    val retiredApiServiceName = "employment-intermediaries"
    val retiredApi = unfilteredApis.filter(_.serviceName.value == retiredApiServiceName).head
  }

  "getUnfilteredApis" should {
    "return retired apis" in new Setup {

      val result = inTest.getUnfilteredApis
      result should contain (retiredApi)

    }
  }

  "getStableApis" should {
    "not return retired apis" in new Setup {

      val result = inTest.getStableApis
      result should not contain (retiredApi)

    }
  }
  
  "getStableApisByServiceName" should {
    "return correct xml api when called with a valid service name" in new Setup {
      
      val result = inTest.getStableApiByServiceName(stableApiServiceName)
      result should contain (stableApi)
    }

    "return nothing when called with a retired API" in new Setup {
      
      val result = inTest.getStableApiByServiceName(retiredApiServiceName)
      result shouldBe None
    }
  }
  
  "getStableApisForCategory" should {
    "return correct xml apis for the category" in new Setup {

      val result = inTest.getStableApisByCategory(stableApiCategory.toString)
      val expectedApis = result.filter(_.categories.getOrElse(Seq.empty).contains(stableApiCategory))
      
      result.size shouldBe 2  // PAYE has two stable APIs and one retired API
      result should contain only (expectedApis: _*)
    }

    "return nothing if given a category with no xml apis" in new Setup {
      
      val result = inTest.getStableApisByCategory(ApiCategory.VAT.toString)
      result shouldBe Nil
    }
  }
}
