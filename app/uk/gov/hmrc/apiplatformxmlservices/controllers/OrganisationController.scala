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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.apiplatformxmlservices.models.JsonFormatters._
import uk.gov.hmrc.apiplatformxmlservices.models.{CreateOrganisationRequest, Organisation, OrganisationId, VendorId}
import uk.gov.hmrc.apiplatformxmlservices.service.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.apiplatformxmlservices.models.OrganisationName
import uk.gov.hmrc.apiplatformxmlservices.models.GetOrCreateUserIdRequest

@Singleton
class OrganisationController @Inject() (organisationService: OrganisationService, cc: ControllerComponents)(implicit val ec: ExecutionContext) extends BackendController(cc) {

  def findByOrgId(organisationId: OrganisationId): Action[AnyContent] = Action.async {
    organisationService.findByOrgId(organisationId) map {
      case Some(organisation: Organisation) => Ok(Json.toJson(organisation))
      case _                                => NotFound(s"XML Organisation with organisationId ${organisationId.value} not found.")
    }
  }

  def findByParams(vendorId: Option[VendorId] = None, organisationName: Option[OrganisationName] = None): Action[AnyContent] = Action.async { request =>
    (vendorId, organisationName) match {
      case (Some(v: VendorId), None)            => organisationService.findByVendorId(v) map {
          case Some(organisation: Organisation) => Ok(Json.toJson(Seq(organisation)))
          case _                                => NotFound(s"XML Organisation with vendorId ${v.value} not found.")
        }
      case (None, Some(orgName: OrganisationName)) => organisationService.findByOrganisationName(orgName)
          .map(x => Ok(Json.toJson(x)))
      case _                                       => organisationService.findAll().map(x => Ok(Json.toJson(x)))
    }

  }

  def deleteByOrgId(organisationId: OrganisationId): Action[AnyContent] = Action.async {
    organisationService.deleteByOrgId(organisationId) map {
      case true => NoContent
      case _    => NotFound(s"XML Organisation with organisationId ${organisationId.value} not found.")
    }
  }

  // def addCollaborator(organisationId: OrganisationId): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
  //   val getOrCreateUserIdRequest = request.body.as[GetOrCreateUserIdRequest]
  //   organisationService.getOrCreateUserId(organisationId, getOrCreateUserIdRequest).map {
  //     case Right(coreUserDetail)            => Ok(Json.toJson(coreUserDetail))
  //     case Left(_) => BadRequest(s"Could not add team member ${getOrCreateUserIdRequest.email}")
  //   }
  // }

  def create(): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    //TODO - the parsing of the request needs better error handling
    val createOrganisationRequest = request.body.as[CreateOrganisationRequest]
    organisationService.create(createOrganisationRequest.organisationName).map {
      case Right(organisation)            => Created(Json.toJson(organisation))
      //TODO do we need a deeper pattern match on below to check the mongo code is the duplicate id / index violation error?
      case Left(_: MongoCommandException) => Conflict(s"Could not create Organisation with name ${createOrganisationRequest.organisationName} - Duplicate ID")
      case Left(e: Exception)             => BadRequest(s"Could not create Organisation with name ${createOrganisationRequest.organisationName} - ${e.getMessage}")
    }
  }
  
  def update(): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    //TODO - the parsing of the request needs better error handling
    val organisation = request.body.as[Organisation]
    organisationService.update(organisation).map {
      case Right(_)                    => Ok
      //TODO do we need a deeper pattern match on below to check the mongo code is the duplicate id / index violation error?
      case Left(_: MongoCommandException) => Conflict(s"Could not update Organisation with ID ${organisation.organisationId.value} - Duplicate ID")
      case _                              => NotFound(s"Could not find Organisation with ID ${organisation.organisationId.value}")
    }
  }
}
