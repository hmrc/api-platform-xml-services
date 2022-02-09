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

package uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper

import play.api.libs.json.Json
import uk.gov.hmrc.apiplatformxmlservices.models.UserId

object JsonFormatters {
  implicit val formatUserId = Json.valueFormat[UserId]
  implicit val formatGetOrCreateUserIdRequest = Json.format[GetOrCreateUserIdRequest]

  implicit val formatUserIdResponse = Json.format[UserIdResponse]

  implicit  val formatUserResponse = Json.format[UserResponse]
  implicit  val formatImportUserRequest = Json.format[ImportUserRequest]

  implicit val formatCoreUserDetail = Json.format[CoreUserDetail]
}
