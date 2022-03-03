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

package uk.gov.hmrc.apiplatformxmlservices.controllers

import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.apiplatformxmlservices.models.{AddCollaboratorRequest, GetOrCreateUserFailedResult, GetOrganisationFailedResult, ManageCollaboratorResult, Organisation, OrganisationAlreadyHasCollaboratorResult, OrganisationId, RemoveCollaboratorRequest, UpdateCollaboratorFailedResult, ValidateCollaboratorFailureResult}
import uk.gov.hmrc.apiplatformxmlservices.models.JsonFormatters._
import uk.gov.hmrc.apiplatformxmlservices.service.TeamMemberService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.controller.WithJsonBody

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TeamMemberController @Inject()(teamMemberService: TeamMemberService, cc: ControllerComponents)(implicit val ec: ExecutionContext)
  extends BackendController(cc)
    with WithJsonBody
    with Logging {

  def addCollaborator(organisationId: OrganisationId): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[AddCollaboratorRequest] { addCollaboratorRequest =>
      teamMemberService.addCollaborator(organisationId, addCollaboratorRequest.email, addCollaboratorRequest.firstName, addCollaboratorRequest.lastName)
        .map(handleCollaboratorResult)
    }
  }

  def removeCollaborator(organisationId: OrganisationId): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[RemoveCollaboratorRequest] { removeCollaboratorRequest =>
      teamMemberService.removeCollaborator(organisationId, removeCollaboratorRequest)
        .map(handleCollaboratorResult)
    }
  }

  private def handleCollaboratorResult(result: Either[ManageCollaboratorResult, Organisation]) = {
    result match {
      case Right(organisation: Organisation) => Ok(Json.toJson(organisation))
      case Left(_: OrganisationAlreadyHasCollaboratorResult) => BadRequest(s"Organisation Already Has Collaborator")
      case Left(result: GetOrganisationFailedResult) => NotFound(s"${result.message}")
      case Left(result: GetOrCreateUserFailedResult) => BadRequest(s"${result.message}")
      case Left(result: ValidateCollaboratorFailureResult) => NotFound(s"${result.message}")
      case Left(result: UpdateCollaboratorFailedResult) => InternalServerError(s"${result.message}")
    }
  }


}