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

import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.UserResponse

case class CreateOrganisationRequest(organisationName: OrganisationName, email: String)

case class UpdateOrganisationDetailsRequest(organisationName: OrganisationName)

case class AddCollaboratorRequest(email: String)
case class RemoveCollaboratorRequest(email: String, gatekeeperUserId: String)

case class OrganisationWithNameAndVendorId(name: OrganisationName, vendorId: VendorId)
case class BulkUploadOrganisationsRequest(organisations: Seq[OrganisationWithNameAndVendorId])

case class ParsedUser(email: String, firstName: String, lastName: String, services: String, vendorIds: List[VendorId])
case class BulkAddUsersRequest(users: Seq[ParsedUser])

// case class CreatedOrUpdatedUser(csvRowNumber: Int, parsedUser: ParsedUser, userResponse: UserResponse)
// object CreatedOrUpdatedUser {
//     def create(csvRowNumber: Int,parsedUser: ParsedUser, userResponse: UserResponse): CreatedOrUpdatedUser ={
//         CreatedOrUpdatedUser(csvRowNumber, parsedUser, userResponse)
//     }
// }

sealed trait ManageCollaboratorResult{
    val message: String
}
case class OrganisationAlreadyHasCollaboratorResult(message: String) extends ManageCollaboratorResult
case class GetOrganisationFailedResult(message: String) extends ManageCollaboratorResult
case class GetOrCreateUserIdFailedResult(message: String) extends ManageCollaboratorResult
case class UpdateCollaboratorFailedResult(message: String) extends ManageCollaboratorResult
case class ValidateCollaboratorFailureResult(message: String) extends ManageCollaboratorResult

sealed trait CreateOrganisationResult
case class CreateOrganisationSuccessResult(organisation: Organisation) extends CreateOrganisationResult
case class CreateOrganisationFailedResult(message: String) extends CreateOrganisationResult
case class CreateOrganisationFailedDuplicateIdResult(message: String) extends CreateOrganisationResult


sealed trait UpdateOrganisationResult
case class UpdateOrganisationSuccessResult(organisation: Organisation) extends UpdateOrganisationResult
case class UpdateOrganisationFailedResult() extends UpdateOrganisationResult

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
case class MissingVendorIdResult(message: String) extends ValidateUserResult
//case class InvalidServiceResult(message: String) extends ValidateUserResult


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

