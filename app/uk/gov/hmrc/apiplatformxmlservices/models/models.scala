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

package uk.gov.hmrc.apiplatformxmlservices.models

import enumeratum._
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatformxmlservices.models.common.{ApiCategory, ServiceName}

import java.{util => ju}
import scala.io.Source


sealed trait OrganisationSortBy extends EnumEntry

object OrganisationSortBy extends Enum[OrganisationSortBy] {

  val values = findValues

  case object VENDOR_ID extends OrganisationSortBy
  case object ORGANISATION_NAME extends OrganisationSortBy

}


case class UserId(value: ju.UUID)


case class XmlApi(name: String, serviceName: ServiceName, context: String, description: String, categories: Option[Seq[ApiCategory]] = None)

object XmlApi extends JsonFormatters {

  def xmlApis: List[XmlApi] =
    Json.parse(Source.fromInputStream(getClass.getResourceAsStream("/xml_apis.json")).mkString).as[List[XmlApi]]
}

case class OrganisationUser(organisationId: OrganisationId, userId: UserId, email: String, firstName:String, lastName: String, serviceNames: List[String])

case class OrganisationId(value: ju.UUID) extends AnyVal

case class VendorId(value: Long) extends AnyVal

case class OrganisationName(value: String) extends AnyVal

case class Collaborator(userId: UserId, email: String)

case class Organisation(organisationId: OrganisationId, vendorId: VendorId, name: OrganisationName, collaborators: List[Collaborator] = List.empty, services: List[ServiceName] =List.empty)