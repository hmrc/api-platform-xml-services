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


import uk.gov.hmrc.apiplatformxmlservices.models.UserId
import uk.gov.hmrc.apiplatformxmlservices.models.ApiCategory
import uk.gov.hmrc.apiplatformxmlservices.models.ServiceName

case class GetOrCreateUserIdRequest(email: String)

case class UserIdResponse(userId: UserId)


case class CoreUserDetail(userId: UserId, email: String)

case class ImportUserRequest(email: String,
                               firstName: String,
                               lastName: String,
                               emailPreferences: Map[ApiCategory, List[ServiceName]]
                              )

case class UserResponse(email: String,
                        firstName: String,
                        lastName: String,
                        verified: Boolean = false,
                        userId: UserId)
