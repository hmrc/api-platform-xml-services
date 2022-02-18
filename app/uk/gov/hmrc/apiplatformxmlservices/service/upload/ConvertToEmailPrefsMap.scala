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

package uk.gov.hmrc.apiplatformxmlservices.service.upload

import uk.gov.hmrc.apiplatformxmlservices.models.ParsedUser
import uk.gov.hmrc.apiplatformxmlservices.models.XmlApi
import uk.gov.hmrc.apiplatformxmlservices.models.ApiCategory
import uk.gov.hmrc.apiplatformxmlservices.models.ServiceName

trait ConvertToEmailPrefsMap {

  def extractEmailPreferencesFromUser(parsedUser: ParsedUser, xmlApis: Seq[XmlApi]): Map[ApiCategory, List[ServiceName]] = {
    import cats.implicits._
    def apiListToMap(apis: List[XmlApi]): Map[ApiCategory, List[ServiceName]] = {
      val categoryList = apis.flatMap(api => api.categories.getOrElse(List.empty)).distinct
      categoryList.flatMap(category =>
        apis.map(api => {
          if (api.categories.getOrElse(List.empty).contains(category)) {
            Map(category -> List(api.serviceName))
          }
          else Map.empty[ApiCategory, List[ServiceName]]
        })).reduce((x, y) => x.combine(y))
    }

    if (parsedUser.services.nonEmpty && xmlApis.nonEmpty) {
      val apis: List[XmlApi] = parsedUser.services
        .map(service => xmlApis.filter(x => x.serviceName == service).head)
      apiListToMap(apis)
    } else {
      Map.empty[ApiCategory, List[ServiceName]]
    }

  }
}
