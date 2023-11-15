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

import play.api.libs.json.Json
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress

/* Organisation request responses */

case class CreateOrganisationRequest(organisationName: OrganisationName, email: LaxEmailAddress, firstName: String, lastName: String)

object CreateOrganisationRequest {
  implicit val formatCreateOrganisationRequest = Json.format[CreateOrganisationRequest]
}

case class UpdateOrganisationDetailsRequest(organisationName: OrganisationName)

object UpdateOrganisationDetailsRequest {
  implicit val formatUpdateOrganisationDetailsRequest = Json.format[UpdateOrganisationDetailsRequest]
}

/* User request responses */

case class AddCollaboratorRequest(email: LaxEmailAddress, firstName: String, lastName: String)

object AddCollaboratorRequest {
  implicit val formatAddCollaboratorRequest = Json.format[AddCollaboratorRequest]
}
case class RemoveCollaboratorRequest(email: LaxEmailAddress, gatekeeperUserId: String)

object RemoveCollaboratorRequest {
  implicit val formatRemoveCollaboratorRequest = Json.format[RemoveCollaboratorRequest]
}

case class ErrorResponseMessage(message: String)

object ErrorResponseMessage {
  implicit val formatErrorResponseMessage = Json.format[ErrorResponseMessage]
}

case class ErrorResponse(errors: List[ErrorResponseMessage])

object ErrorResponse {
  implicit val formatErrorResponse = Json.format[ErrorResponse]
}
