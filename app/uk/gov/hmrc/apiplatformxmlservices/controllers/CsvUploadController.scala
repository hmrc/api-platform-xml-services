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

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.play.bootstrap.controller.WithJsonBody

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import uk.gov.hmrc.apiplatformxmlservices.service.UploadService

@Singleton
class CsvUploadController @Inject() (
    organisationService: OrganisationService,
    uploadService: UploadService,
    cc: ControllerComponents
  )(implicit val ec: ExecutionContext)
    extends BackendController(cc)
    with WithJsonBody
    with Logging {

  def bulkUploadUsers(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>
      withJsonBody[BulkAddUsersRequest] { bulkAddUsersRequest =>
        handleUploadUsers(bulkAddUsersRequest)
        Future.successful(Ok(""))
      }
  }

  private def handleUploadUsers(bulkAddUsersRequest: BulkAddUsersRequest) = {
    def process(parsedUser: ParsedUser, rowNumber: Int): Unit = {
      uploadService.uploadUser(parsedUser, rowNumber) map {
        case Right(user: CreatedOrUpdatedUser) => logger.info(s"Users CSV import - user on row number $rowNumber successfully updated/added to database")
        case Left(e: Exception)                => logger.error(s"Users CSV import - user on row number $rowNumber could not be updated/added to the database - ${e.getMessage}")
      }
    }
    bulkAddUsersRequest.users.zipWithIndex.map(x => process(x._1, x._2))

  }

  def bulkFindAndCreateOrUpdate(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>
      withJsonBody[BulkFindAndCreateOrUpdateRequest] { bulkFindAndCreateOrUpdateRequest =>
        handleFindAndCreateOrUpdate(bulkFindAndCreateOrUpdateRequest)
        Future.successful(Ok(Json.toJson(request.toString())))
      }
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

}
