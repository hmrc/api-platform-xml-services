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

package uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.service

import uk.gov.hmrc.apiplatformxmlservices.models.ExternalXmlApi
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.ParsedUser
import cats.implicits._
import uk.gov.hmrc.apiplatformxmlservices.models.common.{ApiCategory, ServiceName}

trait ConvertToEmailPrefsMap {

  def extractEmailPreferencesFromUser(parsedUser: ParsedUser, xmlApis: List[ExternalXmlApi]): Map[ApiCategory, List[ServiceName]] = {

    val distinctCategories = xmlApis.flatMap(api => api.categories.getOrElse(List.empty)).distinct

    def generateCategoryMaps(category: ApiCategory, apis: List[ExternalXmlApi]): List[Map[ApiCategory, List[ServiceName]]] = {
      apis.map(api => {
        if (api.categories.getOrElse(List.empty).contains(category)) {
          Map(category -> List(api.serviceName))
        }
        else Map.empty[ApiCategory, List[ServiceName]]
      })
    }

    def combineMaps(categoryMaps: List[Map[ApiCategory, List[ServiceName]]]): Map[ApiCategory, List[ServiceName]] = {
      categoryMaps.reduce((x, y) => x.combine(y))
    }

    def apiListToMap(filteredApis: List[ExternalXmlApi]): Map[ApiCategory, List[ServiceName]] = {
      val categoryMaps = for {
        distinctCategory <- distinctCategories
        categoryMaps = generateCategoryMaps(distinctCategory, filteredApis)
      } yield categoryMaps
      combineMaps(categoryMaps.flatten)
    }

    if (parsedUser.services.nonEmpty && xmlApis.nonEmpty) {
      val apis: List[ExternalXmlApi] = parsedUser.services
        .map(service => xmlApis.filter(x => x.serviceName == service).head)
      apiListToMap(apis)
    } else Map.empty[ApiCategory, List[ServiceName]]

  }
}
