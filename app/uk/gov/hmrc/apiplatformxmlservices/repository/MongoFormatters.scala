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

package uk.gov.hmrc.apiplatformxmlservices.repository

//import play.api.libs.json.*
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId

import uk.gov.hmrc.apiplatformxmlservices.controllers.binders.RouteModels.SimpleUserId
import uk.gov.hmrc.apiplatformxmlservices.models.*
//import ServiceName.given_Format_ServiceName

object MongoFormatters {
//  given formatServiceName: Format[ServiceName]                                          = Json.format[ServiceName]
  given formatVendorId: Format[VendorId]                                                = Json.valueFormat[VendorId]
  given formatOrganisationName: Format[OrganisationName]                                = Json.valueFormat[OrganisationName]
  given formatOrganisationWithNameAndVendorId: OFormat[OrganisationWithNameAndVendorId] = Json.format[OrganisationWithNameAndVendorId]
  given organisationIdFormats: Format[XmlOrganisationId]                                = Json.valueFormat[XmlOrganisationId]
  given userIdFormats: Format[SimpleUserId]                                             = Json.valueFormat[SimpleUserId]
  given collaboratorFormats: OFormat[Collaborator]                                      = Json.format[Collaborator]
  given organisationFormats: OFormat[Organisation]                                      = Json.format[Organisation]
}
