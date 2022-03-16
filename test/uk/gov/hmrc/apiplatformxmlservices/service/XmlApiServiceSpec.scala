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


class XmlApiServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {


  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  trait Setup {
    val inTest = new XmlApiService
    val unfilteredApis = inTest.getUnfilteredApis()
    val retiredApi = unfilteredApis.filter(_.serviceName.value == "employment-intermediaries").head
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
}
