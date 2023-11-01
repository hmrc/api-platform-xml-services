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

package uk.gov.hmrc.apiplatformxmlservices

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ServiceName}

import uk.gov.hmrc.apiplatformxmlservices.common.data.CommonTestData
import uk.gov.hmrc.apiplatformxmlservices.common.utils.{FixedClock, HmrcSpec, JsonFormattersSpec}
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.WrappedApiCategoryServiceMap

class WrappedApiCategoryServiceMapSpec extends HmrcSpec with FixedClock with CommonTestData with JsonFormattersSpec {

  "WrappedApiCategoryServiceMap" should {

    "convert to json" in {

      val apiCategoryServicesMap = WrappedApiCategoryServiceMap(Map(ApiCategory.VAT -> List(ServiceName("api1"), ServiceName("api2"))))
      val payload: JsValue       = Json.toJson(apiCategoryServicesMap)

      val result = Json.fromJson[WrappedApiCategoryServiceMap](payload).asOpt
      result.value.wrapped shouldBe Map(ApiCategory.VAT -> List(ServiceName("api1"), ServiceName("api2")))
    }

  }
}
