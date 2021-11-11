/*
 * Copyright 2021 HM Revenue & Customs
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

import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import uk.gov.hmrc.apiplatformxmlservices.models.XmlApi
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatformxmlservices.models.JsonFormatters._

@Singleton()
class ApiController @Inject() (cc: ControllerComponents)
    extends BackendController(cc) {

  def getAll(): Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(Json.toJson(XmlApi.xmlApis)))
  }

  def getApi(name: String): Action[AnyContent] = Action.async { implicit request =>
    XmlApi.xmlApis.find(_.name == name) match {
      case Some(xmlApi) => Future.successful(Ok(Json.toJson(xmlApi)))
      case _ => Future.successful(NotFound(s"XML API with name $name not found."))
    }
  }
}