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

package uk.gov.hmrc.apiplatformxmlservices.service

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ServiceName}
import uk.gov.hmrc.apiplatformxmlservices.common.utils.AsyncHmrcSpec

class XmlApiServiceSpec extends AsyncHmrcSpec {

  /*
   * The data for this test is production data in the file $project_dir/resources/xml_apis.json
   * The data is not stored in a database because it is relatively static.
   * Tests may break as the data changes over time.
   */
  trait Setup {
    val inTest         = new XmlApiService
    val unfilteredApis = inTest.getUnfilteredApis()
    val stableApis     = inTest.getStableApis()

    val retiredApiServiceName = ServiceName("employment-intermediaries")
    val retiredApi            = unfilteredApis.filter(_.serviceName == retiredApiServiceName).head
  }

  "getUnfilteredApis" should {
    "return retired apis" in new Setup {

      val result = inTest.getUnfilteredApis()
      result should contain(retiredApi)

    }
  }

  "getStableApis" should {
    "not return retired apis" in new Setup {

      val result = inTest.getStableApis()
      result should not contain (retiredApi)

    }
  }

  "getStableApisByServiceName" should {
    "return the matching xml api when called with a valid service name" in new Setup {

      val serviceName = ServiceName("paye-online")
      val result      = inTest.getStableApiByServiceName(serviceName)
      result.map(_.serviceName).getOrElse("") shouldBe serviceName
    }

    "return nothing when called with a retired API" in new Setup {

      val result = inTest.getStableApiByServiceName(retiredApiServiceName)
      result shouldBe None
    }
  }

  "getStableApisForCategory" should {
    "return xml apis containing the selected category" in new Setup {

      val result       = inTest.getStableApisByCategory(ApiCategory.PAYE)
      val expectedApis = stableApis.filter(_.categories.getOrElse(Seq.empty).contains(ApiCategory.PAYE))

      result.size shouldBe 2 // PAYE has two stable APIs and one retired API
      result should contain only (expectedApis: _*)
    }

    "return nothing if given a category with no xml apis" in new Setup {

      val result = inTest.getStableApisByCategory(ApiCategory.VAT)
      result shouldBe Nil
    }
  }

  "getStableApisForCategories" should {
    "return correct xml apis for a list of categories" in new Setup {

      val result = inTest.getStableApisForCategories(List(ApiCategory.PAYE, ApiCategory.OTHER))

      val expectedApis = stableApis.filter { xmlApi =>
        val categories = xmlApi.categories.getOrElse(Seq.empty)
        categories.contains(ApiCategory.PAYE) || categories.contains(ApiCategory.OTHER)
      }

      result.size shouldBe 3 // PAYE has two stable APIs and one retired API, OTHER has one API
      result should contain only (expectedApis: _*)
    }

    "return empty list for unmatched category" in new Setup {

      val result = inTest.getStableApisForCategories(List(ApiCategory.VAT))
      result shouldBe Nil
    }
  }
}
