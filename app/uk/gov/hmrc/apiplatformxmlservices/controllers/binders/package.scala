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

import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.apiplatformxmlservices.models.{OrganisationId, VendorId}

import java.util.UUID
import scala.util.Try

package object binders {

  private def organisationIdFromString(text: String): Either[String, OrganisationId] = {
    Try(UUID.fromString(text))
      .toOption
      .toRight(s"Cannot accept $text as OrganisationId")
      .map(OrganisationId(_))
  }

  implicit def organisationIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[OrganisationId] = new PathBindable[OrganisationId] {
    override def bind(key: String, value: String): Either[String, OrganisationId] = {
      textBinder.bind(key, value).flatMap(organisationIdFromString)
    }

    override def unbind(key: String, organisationId: OrganisationId): String = {
      textBinder.unbind(key, organisationId.value.toString)
    }
  }

  private def vendorIdFromString(text: String): Either[String, VendorId] = {
    Try(text.toLong)
      .toOption
      .toRight(s"Cannot accept $text as VendorId")
      .map(VendorId(_))
  }

  implicit def vendorIdQueryStringBindable(implicit textBinder: QueryStringBindable[String]): QueryStringBindable[VendorId] =
    new QueryStringBindable[VendorId] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, VendorId]] = {
        textBinder.bind(key, params).map {
          case Right(vendorId) => vendorIdFromString(vendorId)
          case Left(_) => Left("Unable to bind vendorId")
        }
      }

      override def unbind(key: String, vendorId: VendorId): String = {
        textBinder.unbind(key, vendorId.value.toString)
      }
    }

}
