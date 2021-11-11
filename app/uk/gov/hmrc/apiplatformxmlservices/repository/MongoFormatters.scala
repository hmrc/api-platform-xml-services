/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json._
import uk.gov.hmrc.apiplatformxmlservices.models.Organisation
import uk.gov.hmrc.apiplatformxmlservices.models.OrganisationId
import uk.gov.hmrc.apiplatformxmlservices.models.VendorId

object MongoFormatters {

  implicit val organisationIdFormats: Format[OrganisationId] = Json.valueFormat[OrganisationId]
  implicit val vendorIdFormats: Format[VendorId] = Json.valueFormat[VendorId]
  implicit val organisationFormats: OFormat[Organisation] = Json.format[Organisation]
}