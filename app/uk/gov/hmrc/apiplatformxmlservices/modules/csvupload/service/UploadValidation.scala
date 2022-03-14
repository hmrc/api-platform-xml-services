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

package uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.service

import cats.data.Validated._
import cats.data.ValidatedNel
import cats.implicits._

import uk.gov.hmrc.apiplatformxmlservices.models.{Organisation, VendorId, ExternalXmlApi}
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.ParsedUser

import scala.concurrent.{ExecutionContext, Future}

trait UploadValidation {

  def validateParsedUser(user: ParsedUser, rowNumber: Int, vendorIdExistsFunc: VendorId => Future[Option[Organisation]])
                        (implicit ec: ExecutionContext): Future[ValidatedNel[String, List[ParsedUser]]] = {


    Future.sequence(List(
      validateVendorIds(user, rowNumber, vendorIdExistsFunc),
      validateServiceNames(user, rowNumber)))
      .map(x => x.sequence)
  }


  def validateVendorIds(parsedUser: ParsedUser, rowNumber: Int, vendorIdExistsFunc: VendorId => Future[Option[Organisation]])
                       (implicit ec: ExecutionContext): Future[ValidatedNel[String, ParsedUser]] = {
    parsedUser.vendorIds match {
      case Nil => Future.successful(s"RowNumber:$rowNumber - missing vendorIds on user".invalidNel)
      case vendorIds: List[VendorId] => Future.sequence(vendorIds.map(vendorIdExistsFunc))
        .map(x => x.flatten.size == vendorIds.size && vendorIds.nonEmpty) map {
        case true => Valid(parsedUser)
        case false => s"RowNumber:$rowNumber - Invalid vendorId(s)".invalidNel
      }
    }

  }


  def validateServiceNames(user: ParsedUser, rowNumber: Int)(implicit ec: ExecutionContext): Future[ValidatedNel[String, ParsedUser]] = {
    val allServiceNames = ExternalXmlApi.stableExternalXmlApis.map(x => x.serviceName.value)

    Future.successful(user.services.isEmpty || user.services.forall(x => allServiceNames.contains(x.value))) map {
      case true => Valid(user)
      case false => s"RowNumber:$rowNumber - Invalid service(s)".invalidNel
    }
  }
}
