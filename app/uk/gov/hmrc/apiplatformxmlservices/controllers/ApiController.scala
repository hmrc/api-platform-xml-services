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

package uk.gov.hmrc.apiplatformxmlservices.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ServiceName}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apiplatformxmlservices.service.XmlApiService

@Singleton()
class ApiController @Inject() (xmlService: XmlApiService, cc: ControllerComponents)
    extends BackendController(cc) {

  def getAll(): Action[AnyContent] = Action.async {
    Future.successful(Ok(Json.toJson(xmlService.getStableApis())))
  }

  def getApisFiltered(categoryFilter: List[ApiCategory]): Action[AnyContent] = Action.async {
    Future.successful(Ok(Json.toJson(xmlService.getStableApisForCategories(categoryFilter))))
  }

  def getApi(name: String): Action[AnyContent] = Action.async {
    xmlService.getUnfilteredApis().find(_.name == name) match {
      case Some(xmlApi) => Future.successful(Ok(Json.toJson(xmlApi)))
      case _            => Future.successful(NotFound(s"XML API with name $name not found."))
    }
  }

  def getApiByServiceName(serviceName: ServiceName): Action[AnyContent] = Action.async {
    xmlService.getUnfilteredApis().find(_.serviceName == serviceName) match {
      case Some(xmlApi) => Future.successful(Ok(Json.toJson(xmlApi)))
      case _            => Future.successful(NotFound(s"XML API with serviceName $serviceName not found."))
    }
  }
}
