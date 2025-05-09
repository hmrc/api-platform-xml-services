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

package uk.gov.hmrc.apiplatform.modules.test_only.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.apiplatform.modules.test_only.services.CloneOrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.controller.WithJsonBody

import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.service.OrganisationService

@Singleton
class TestOnlyOrganisationController @Inject() (
    organisationService: OrganisationService,
    cloneOrgansationService: CloneOrganisationService,
    cc: ControllerComponents
  )(implicit val ec: ExecutionContext
  ) extends BackendController(cc)
    with WithJsonBody
    with Logging {

  def cloneOrg(organisationId: OrganisationId): Action[AnyContent] = Action.async { implicit request =>
    organisationService.findByOrgId(organisationId) flatMap {
      case Some(org: Organisation) =>
        cloneOrgansationService.cloneOrg(organisationId).map {
          case Left(_)    => InternalServerError(s"Failed to clone organisation")
          case Right(org) => Created(Json.toJson(org))
        }
      case _                       => Future.successful(NotFound(s"XML Organisation with organisationId ${organisationId.value} not found."))
    }
  }
}
