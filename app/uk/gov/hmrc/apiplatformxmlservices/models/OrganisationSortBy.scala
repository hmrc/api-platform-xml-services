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

package uk.gov.hmrc.apiplatformxmlservices.models

sealed trait OrganisationSortBy

object OrganisationSortBy {

  case object VENDOR_ID         extends OrganisationSortBy
  case object ORGANISATION_NAME extends OrganisationSortBy
  val values = List(VENDOR_ID, ORGANISATION_NAME)

  def apply(text: String): Option[OrganisationSortBy] = OrganisationSortBy.values.find(_.toString == text.toUpperCase)

  // Not yet required but when library exists, we probably will need them.
  // $COVERAGE-OFF$
  def unsafeApply(text: String): OrganisationSortBy = apply(text).getOrElse(throw new RuntimeException(s"$text is not a sort by value"))
  // $COVERAGE-ON$

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting

  implicit val format: Format[OrganisationSortBy] = SealedTraitJsonFormatting.createFormatFor[OrganisationSortBy]("Organisation sort by", apply)

}
