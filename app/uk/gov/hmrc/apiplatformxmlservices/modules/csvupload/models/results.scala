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

package uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models

import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.UserResponse


sealed trait CreateVerifiedUserResult

abstract class CreateVerifiedUserSuccessResult() extends CreateVerifiedUserResult {
  val userResponse: UserResponse
}

case class CreatedUserResult(userResponse: UserResponse) extends CreateVerifiedUserSuccessResult
case class RetrievedUserResult(userResponse: UserResponse) extends CreateVerifiedUserSuccessResult
case class CreateVerifiedUserFailedResult(message: String) extends CreateVerifiedUserResult

sealed trait ValidateUserResult{
  val message: String
}
case class ValidUserResult(message: String) extends ValidateUserResult
case class InvalidVendorIdResult(message: String) extends ValidateUserResult
case class InvalidServiceNameResult(message: String) extends ValidateUserResult
case class MissingVendorIdResult(message: String) extends ValidateUserResult

sealed trait UploadUserResult

abstract class UploadFailedResult() extends UploadUserResult {
  val message: String
}

abstract class UploadSuccessResult() extends UploadUserResult

case class UploadCreatedUserSuccessResult(rowNumber: Int, user: UserResponse) extends UploadSuccessResult
case class UploadExistingUserSuccessResult(rowNumber: Int, user: UserResponse) extends UploadSuccessResult

case class InvalidUserResult(message: String) extends UploadFailedResult
case class AddUserToOrgFailureResult(message: String) extends UploadFailedResult
case class CreateOrGetUserFailedResult(message: String) extends UploadFailedResult

