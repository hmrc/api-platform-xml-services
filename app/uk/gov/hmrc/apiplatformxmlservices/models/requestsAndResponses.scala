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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}

/* Organisation request responses */

case class CreateOrganisationRequest(organisationName: OrganisationName, email: LaxEmailAddress, firstName: String, lastName: String)

object CreateOrganisationRequest {
  given formatCreateOrganisationRequest: OFormat[CreateOrganisationRequest] = Json.format[CreateOrganisationRequest]
}

case class UpdateOrganisationDetailsRequest(organisationName: OrganisationName)

object UpdateOrganisationDetailsRequest {
  given formatUpdateOrganisationDetailsRequest: OFormat[UpdateOrganisationDetailsRequest] = Json.format[UpdateOrganisationDetailsRequest]
}

/* User request responses */

case class AddCollaboratorRequest(email: LaxEmailAddress, firstName: String, lastName: String)

object AddCollaboratorRequest {
  given formatAddCollaboratorRequest: OFormat[AddCollaboratorRequest] = Json.format[AddCollaboratorRequest]
}

case class RemoveCollaboratorRequest(email: LaxEmailAddress, gatekeeperUserId: String)

object RemoveCollaboratorRequest {
  given formatRemoveCollaboratorRequest: OFormat[RemoveCollaboratorRequest] = Json.format[RemoveCollaboratorRequest]
}

case class RemoveAllCollaboratorsForUserIdRequest(userId: UserId, gatekeeperUserId: String)

object RemoveAllCollaboratorsForUserIdRequest {
  given formatRemoveAllCollaboratorsForUserIdRequest: OFormat[RemoveAllCollaboratorsForUserIdRequest] = Json.format[RemoveAllCollaboratorsForUserIdRequest]
}

case class ErrorResponseMessage(message: String)

object ErrorResponseMessage {
  given formatErrorResponseMessage: OFormat[ErrorResponseMessage] = Json.format[ErrorResponseMessage]
}

case class ErrorResponse(errors: List[ErrorResponseMessage])

object ErrorResponse {
  given formatErrorResponse: OFormat[ErrorResponse] = Json.format[ErrorResponse]
}
