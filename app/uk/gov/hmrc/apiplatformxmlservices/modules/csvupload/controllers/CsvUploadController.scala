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

package uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.controllers

import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models._
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.service.UploadService
import uk.gov.hmrc.apiplatformxmlservices.service.OrganisationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.controller.WithJsonBody

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CsvUploadController @Inject() (
    organisationService: OrganisationService,
    uploadService: UploadService,
    cc: ControllerComponents
  )(implicit val ec: ExecutionContext)
    extends BackendController(cc)
    with WithJsonBody
      with CSVJsonFormats
    with Logging {

  def bulkUploadUsers(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>
      withJsonBody[BulkAddUsersRequest] { bulkAddUsersRequest =>
        handleUploadUsers(bulkAddUsersRequest)
        Future.successful(Ok(""))
      }
  }



  private def handleUploadUsers(bulkAddUsersRequest: BulkAddUsersRequest)(implicit hc: HeaderCarrier) = {

    def printErrors(results: List[UploadUserResult]) ={
       val errors = results.flatMap(x => x match {
         case e: UploadFailedResult => Some(e.message)
         case _ => None
       })
        errors.map(logger.error(_))
    }
    val usersToUpload = bulkAddUsersRequest.users.toList
    uploadService.uploadUsers(usersToUpload) map {
      results =>
          val successful = results.count(x => x.isInstanceOf[UploadSuccessResult])
          val created = results.count(x => x.isInstanceOf[UploadCreatedUserSuccessResult])
          val retrieved = results.count(x => x.isInstanceOf[UploadExistingUserSuccessResult])
          val failure =  results.count(x => x.isInstanceOf[UploadFailedResult])

        printErrors(results)
        logger.warn(s"Expected to upload ${usersToUpload.size} users. Successfully uploaded $successful; of which $created were created and $retrieved were retrieved. Number of users failed to upload: $failure")
    }

  }

  def bulkUploadOrganisations(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>
      withJsonBody[BulkUploadOrganisationsRequest] { bulkUploadOrganisationsRequest =>
        handleBulkUploadOrganisations(bulkUploadOrganisationsRequest)
        Future.successful(Ok(Json.toJson(request.toString())))
      }
  }

  private def handleBulkUploadOrganisations(bulkUploadOrganisationsRequest: BulkUploadOrganisationsRequest) = {
    def process(organisation: OrganisationWithNameAndVendorId): Unit = {
      organisationService.findAndCreateOrUpdate(organisation.name, organisation.vendorId) map {
        case Right(org: Organisation) => logger.info(s"Organisation CSV import - ${org.name} successfully updated/added to database")
        case Left(e: Exception)       => logger.error(s"Organisation CSV import - ${organisation.name} could not be updated/added to the database - ${e.getMessage}")
      }
    }

    bulkUploadOrganisationsRequest.organisations.map(process)
  }

}
