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

import org.mongodb.scala.MongoCommandException
import play.api.Logging
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.apiplatformxmlservices.models.JsonFormatters._
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.service.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.controller.WithJsonBody

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class OrganisationController @Inject() (organisationService: OrganisationService, cc: ControllerComponents)(implicit val ec: ExecutionContext)
    extends BackendController(cc)
    with WithJsonBody
    with Logging {

  def bulkFindAndCreateOrUpdate(): Action[JsValue] = Action.async(parse.tolerantJson) {
    println("******* In bulkFindAndCreateOrUpdate method")
    implicit request =>
      /* withJsonBody[BulkFindAndCreateOrUpdateRequest] { bulkFindAndCreateOrUpdateRequest =>
        handleFindAndCreateOrUpdate(bulkFindAndCreateOrUpdateRequest) */
        Future.successful(Ok(Json.toJson(request.toString())))
      // }
  }

  private def handleFindAndCreateOrUpdate(bulkFindAndCreateOrUpdateRequest: BulkFindAndCreateOrUpdateRequest) = {
    def process(organisation: OrganisationWithNameAndVendorId): Unit = {
      organisationService.findAndCreateOrUpdate(organisation.name, organisation.vendorId) map {
        case Right(org: Organisation) => logger.info(s"Organisation CSV import - ${org.name} successfully updated/added to database")
        case Left(e: Exception)       => logger.error(s"Organisation CSV import - ${organisation.name} could not be updated/added to the database - ${e.getMessage}")
      }
    }

    bulkFindAndCreateOrUpdateRequest.organisations.map(process)
  }

  def findByOrgId(organisationId: OrganisationId): Action[AnyContent] = Action.async {
    organisationService.findByOrgId(organisationId) map {
      case Some(organisation: Organisation) => Ok(Json.toJson(organisation))
      case _                                => NotFound(s"XML Organisation with organisationId ${organisationId.value} not found.")
    }
  }

  def findByParams(vendorId: Option[VendorId] = None, organisationName: Option[OrganisationName] = None, sortBy: Option[OrganisationSortBy]): Action[AnyContent] = Action.async {
    request =>
      (vendorId, organisationName) match {
        case (Some(v: VendorId), None)               => handleFindOrganisationByVendorId(v)
        case (None, Some(orgName: OrganisationName)) => organisationService.findByOrganisationName(orgName).map(x => Ok(Json.toJson(x)))
        case _                                       => organisationService.findAll(sortBy).map(x => Ok(Json.toJson(x)))
      }
  }

  private def handleFindOrganisationByVendorId(v: VendorId) = {
    organisationService.findByVendorId(v) map {
      case Some(organisation: Organisation) => Ok(Json.toJson(Seq(organisation)))
      case _                                => NotFound(s"XML Organisation with vendorId ${v.value} not found.")
    }
  }

  def deleteByOrgId(organisationId: OrganisationId): Action[AnyContent] = Action.async {
    organisationService.deleteByOrgId(organisationId) map {
      case true => NoContent
      case _    => NotFound(s"XML Organisation with organisationId ${organisationId.value} not found.")
    }
  }

  def addCollaborator(organisationId: OrganisationId): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[AddCollaboratorRequest] { addCollaboratorRequest =>
      organisationService.addCollaborator(organisationId, addCollaboratorRequest.email)
        .map(handleCollaboratorResult)
    }
  }

  def removeCollaborator(organisationId: OrganisationId): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[RemoveCollaboratorRequest] { removeCollaboratorRequest =>
      organisationService.removeCollaborator(organisationId, removeCollaboratorRequest)
        .map(handleCollaboratorResult)
    }
  }

  private def handleCollaboratorResult(result: Either[ManageCollaboratorResult, Organisation]) = {
    result match {
      case Right(organisation: Organisation)               => Ok(Json.toJson(organisation))
      case Left(result: GetOrganisationFailedResult)       => NotFound(s"${result.message}")
      case Left(result: GetOrCreateUserIdFailedResult)     => BadRequest(s"${result.message}")
      case Left(result: ValidateCollaboratorFailureResult) => NotFound(s"${result.message}")
      case Left(result: UpdateCollaboratorFailedResult)    => InternalServerError(s"${result.message}")
    }
  }

  def create(): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[CreateOrganisationRequest] { createOrganisationRequest =>
      organisationService.create(createOrganisationRequest.organisationName).map {
        case Right(organisation)            => Created(Json.toJson(organisation))
        //TODO do we need a deeper pattern match on below to check the mongo code is the duplicate id / index violation error?
        case Left(_: MongoCommandException) => Conflict(s"Could not create Organisation with name ${createOrganisationRequest.organisationName} - Duplicate ID")
        case Left(e: Exception)             => BadRequest(s"Could not create Organisation with name ${createOrganisationRequest.organisationName} - ${e.getMessage}")
      }
    }
  }

  def update(): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[Organisation] { organisation =>
      organisationService.update(organisation).map {
        case Right(_) => Ok(Json.toJson(organisation))
        case _        => NotFound(s"Could not find Organisation with ID ${organisation.organisationId.value}")
      }
    }
  }

  def updateOrganisationDetails(organisationId: OrganisationId): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[UpdateOrganisationDetailsRequest] { organisationDetailsRequest =>
      organisationService.updateOrganisationDetails(organisationId, organisationDetailsRequest.organisationName)
        .map {
          case UpdateOrganisationSuccessResult(organisation: Organisation) => Ok(Json.toJson(organisation))
          case _: UpdateOrganisationFailedResult                           => InternalServerError(s"Unable to update details for organisation: ${organisationId.value}")
        }
    }
  }

}
