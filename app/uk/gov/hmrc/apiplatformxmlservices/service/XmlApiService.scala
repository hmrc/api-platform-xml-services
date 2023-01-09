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

import play.api.libs.json.Json
import uk.gov.hmrc.apiplatformxmlservices.models.common.{ApiCategory, ServiceName}
import uk.gov.hmrc.apiplatformxmlservices.models.{ApiStatus, JsonFormatters, XmlApi}
import uk.gov.hmrc.apiplatformxmlservices.service.InternalXmlApi.internalToXmlApi

import javax.inject.{Inject, Singleton}
import scala.io.Source


case class InternalXmlApi(name: String, serviceName: ServiceName, context: String,
                          description: String, categories: Option[Seq[ApiCategory]] = None,
                          status: ApiStatus = ApiStatus.STABLE)

object InternalXmlApi extends JsonFormatters {

 implicit val format = Json.format[InternalXmlApi]

  def internalToXmlApi(xmlApi: InternalXmlApi) : XmlApi = {
    XmlApi(
      name = xmlApi.name,
      serviceName = xmlApi.serviceName,
      context = xmlApi.context,
      description = xmlApi.description,
      categories = xmlApi.categories
    )
  }
}

@Singleton
class XmlApiService @Inject()() {
  private def xmlApis: List[InternalXmlApi] =
    Json.parse(Source.fromInputStream(getClass.getResourceAsStream("/xml_apis.json")).mkString).as[List[InternalXmlApi]]

  private def stableXmlApis:List[InternalXmlApi] = xmlApis.filterNot(_.status == ApiStatus.RETIRED)

  def getUnfilteredApis(): List[XmlApi] = xmlApis.map(internalToXmlApi)

  def getStableApis(): List[XmlApi] = stableXmlApis.map(internalToXmlApi)

  def getStableApiByServiceName(serviceName: String): Option[XmlApi] = {
    stableXmlApis.map(internalToXmlApi).find(_.serviceName.value == serviceName)
  }

  def getStableApisForCategories(categories: List[ApiCategory]): List[XmlApi] = {
    for {
      category <- categories
      apis <- stableXmlApis.map(internalToXmlApi).filter(categoryFilter(_, category))
    } yield apis
   
  }

  def getStableApisByCategory(apiCategory: String): List[XmlApi] = {
    val categoryToMatch = ApiCategory.withName(apiCategory.toUpperCase)
    stableXmlApis.map(internalToXmlApi).filter(categoryFilter(_, categoryToMatch))
  }

  private def categoryFilter(api: XmlApi, categoryToMatch: ApiCategory): Boolean = {
    api.categories.getOrElse(Seq.empty).contains(categoryToMatch)
  }

}
