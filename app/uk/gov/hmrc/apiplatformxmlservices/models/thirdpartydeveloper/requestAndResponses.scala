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

package uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper

import enumeratum.{Enum, EnumEntry, PlayJsonEnum}

import play.api.libs.json.Reads._
import play.api.libs.json._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
case class GetOrCreateUserIdRequest(email: LaxEmailAddress)

object GetOrCreateUserIdRequest {
  implicit val format = Json.format[GetOrCreateUserIdRequest]
}
case class UserIdResponse(userId: UserId)

object UserIdResponse {
  implicit val format = Json.format[UserIdResponse]
}
case class CoreUserDetail(userId: UserId, email: LaxEmailAddress)

object CoreUserDetail               {
  implicit val format = Json.format[CoreUserDetail]
}
case class WrappedApiCategoryServiceMap(wrapped: Map[ApiCategory, List[ServiceName]])

object WrappedApiCategoryServiceMap {

  def empty = WrappedApiCategoryServiceMap(Map.empty)

  val apiCategoryMapReads: Reads[WrappedApiCategoryServiceMap] = Reads { json =>
    JsSuccess(json.as[Map[String, List[String]]].map {
      case (category, serviceNames) => ApiCategory.unsafeApply(category) -> serviceNames.map(ServiceName(_))
    }).map(WrappedApiCategoryServiceMap(_))
  }

  val apiCategoryMapWrites: Writes[WrappedApiCategoryServiceMap] = new Writes[WrappedApiCategoryServiceMap] {

    def writes(wrappedObject: WrappedApiCategoryServiceMap): JsValue = {
      val wrappedMap                 = wrappedObject.wrapped
      val result: Iterable[JsObject] = wrappedMap map { case (k: ApiCategory, v: List[ServiceName]) =>
        JsObject(Seq(k.toString -> JsArray(v.map(x => JsString(x.toString)))))
      }
      JsArray.apply(result.toList)
    }
  }

  implicit val format: Format[WrappedApiCategoryServiceMap] = Format(apiCategoryMapReads, apiCategoryMapWrites)
}
case class CreateUserRequest(email: LaxEmailAddress, firstName: String, lastName: String, emailPreferences: WrappedApiCategoryServiceMap)

object CreateUserRequest {
  implicit val formats = Json.format[CreateUserRequest]
}
case class TaxRegimeInterests(regime: ApiCategory, services: Set[ServiceName])

object TaxRegimeInterests {
  implicit val format                                    = Json.format[TaxRegimeInterests]
  def hasAllApis(taxRegimeInterests: TaxRegimeInterests) = taxRegimeInterests.services.isEmpty
}

case class EmailPreferences(interests: List[TaxRegimeInterests], topics: Set[EmailTopic])

object EmailPreferences {
  implicit val format = Json.format[EmailPreferences]

  def noPreferences: EmailPreferences = EmailPreferences(List.empty, Set.empty)
}

sealed trait EmailTopic extends EnumEntry

object EmailTopic extends Enum[EmailTopic] with PlayJsonEnum[EmailTopic] {

  val values = findValues

  case object BUSINESS_AND_POLICY extends EmailTopic
  case object TECHNICAL           extends EmailTopic
  case object RELEASE_SCHEDULES   extends EmailTopic
  case object EVENT_INVITES       extends EmailTopic
}
case class UserResponse(email: LaxEmailAddress, firstName: String, lastName: String, verified: Boolean = false, userId: UserId, emailPreferences: EmailPreferences)

object UserResponse {
  implicit val format = Json.format[UserResponse]
}
