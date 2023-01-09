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

package uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.service

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import uk.gov.hmrc.apiplatformxmlservices.models.XmlApi
import uk.gov.hmrc.apiplatformxmlservices.models.common.{ApiCategory, ServiceName}
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.ParsedUser
import uk.gov.hmrc.apiplatformxmlservices.service.XmlApiService

class ConvertToEmailPrefMapSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach with ConvertToEmailPrefsMap {

  val xmlApiService = new XmlApiService()
  val stableXmlApis = xmlApiService.getStableApis()

  "extractEmailPreferencesFromUser" should {
    val api1Name        = ServiceName("api-1")
    val services        = List(ServiceName("import-control-system"), ServiceName("charities-online"), api1Name)
    val validParsedUser = ParsedUser(
      email = "email",
      firstName = "firstName",
      lastName = "lastName",
      services = services,
      vendorIds = List.empty
    )

    "correctly map servicenames to email preferences when a users api is in multiple categories" in {
      val extraApiInMultipleCategories = XmlApi("name", api1Name, "context", "description", Some(Seq(ApiCategory.CUSTOMS, ApiCategory.VAT)))
      val result                       = extractEmailPreferencesFromUser(validParsedUser, stableXmlApis ++ Seq(extraApiInMultipleCategories))
      result.keySet.toList should contain only (ApiCategory.CHARITIES, ApiCategory.CUSTOMS, ApiCategory.VAT)
      result.getOrElse(ApiCategory.CHARITIES, List.empty) should contain only ServiceName("charities-online")
      result.getOrElse(ApiCategory.CUSTOMS, List.empty) should contain only (ServiceName("import-control-system"), api1Name)
      result.getOrElse(ApiCategory.VAT, List.empty) should contain only api1Name
    }

    "return empty map when apis passed in is empty" in { // should this throw an exception??
      val result = extractEmailPreferencesFromUser(validParsedUser, List.empty)
      result shouldBe Map.empty
    }

    "return empty map when user has no services" in { // should this throw an exception??
      val result = extractEmailPreferencesFromUser(validParsedUser.copy(services = List.empty), stableXmlApis)
      result shouldBe Map.empty
    }

    "return correct email preferences when one api has no categories" in { // should this throw an exception??
      val extraApiInMultipleCategories = XmlApi("name", api1Name, "context", "description", None)
      val result                       = extractEmailPreferencesFromUser(validParsedUser, stableXmlApis ++ List(extraApiInMultipleCategories))
      result.keySet.toList should contain only (ApiCategory.CHARITIES, ApiCategory.CUSTOMS)
      result.getOrElse(ApiCategory.CHARITIES, List.empty) should contain only ServiceName("charities-online")
      result.getOrElse(ApiCategory.CUSTOMS, List.empty) should contain only ServiceName("import-control-system")

    }
  }

}
