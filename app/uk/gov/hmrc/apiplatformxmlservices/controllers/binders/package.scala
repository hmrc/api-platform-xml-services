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

import java.util.UUID
import scala.util.Try

import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId

import uk.gov.hmrc.apiplatformxmlservices.models.{OrganisationId, OrganisationSortBy, VendorId}

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

    // this is needed to compile but is not used
    // $COVERAGE-OFF$
    override def unbind(key: String, organisationId: OrganisationId): String = {
      textBinder.unbind(key, organisationId.value.toString)
    }
    // $COVERAGE-ON$
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
          case _               => Left("Unable to bind vendorId")
        }
      }

      // this is needed to compile but is not used
      // $COVERAGE-OFF$
      override def unbind(key: String, vendorId: VendorId): String = {
        textBinder.unbind(key, vendorId.value.toString)
      }
      // $COVERAGE-ON$
    }

  private def categoryFromString(text: String): Either[String, ApiCategory] = {
    ApiCategory.apply(text)
      .toRight(s"Unable to bind category $text")
  }

  implicit def categoryQueryStringBindable(implicit textBinder: QueryStringBindable[String]): QueryStringBindable[ApiCategory] =
    new QueryStringBindable[ApiCategory] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ApiCategory]] = {
        textBinder.bind(key, params).map {
          case Right(category) => categoryFromString(category)
          case _               => Left("Unable to bind category")
        }
      }

      // this is needed to compile but is not used
      // $COVERAGE-OFF$
      override def unbind(key: String, category: ApiCategory): String = {
        textBinder.unbind(key, category.toString)
      }
      // $COVERAGE-ON$

    }

  private def userIdFromString(text: String): Either[String, UserId] = {
    Try(UUID.fromString(text))
      .toOption
      .toRight(s"Cannot accept $text as UserId")
      .map(UserId(_))
  }

  implicit def userIdQueryStringBindable(implicit textBinder: QueryStringBindable[String]): QueryStringBindable[UserId] =
    new QueryStringBindable[UserId] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, UserId]] = {
        textBinder.bind(key, params).map {
          case Right(userId) => userIdFromString(userId)
          case _             => Left("Unable to bind userId")
        }
      }

      // this is needed to compile but is not used
      // $COVERAGE-OFF$
      override def unbind(key: String, userId: UserId): String = {
        textBinder.unbind(key, userId.toString())
      }
      // $COVERAGE-ON$
    }

  implicit def serviceNameQueryStringBindable(implicit textBinder: QueryStringBindable[String]): QueryStringBindable[ServiceName] =
    new QueryStringBindable[ServiceName] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ServiceName]] = {

        textBinder.bind(key, params).map {
          case Right(serviceName) => Right(ServiceName(serviceName))
          case _                  => Left("Unable to bind serviceName")
        }
      }

      // this is needed to compile but is not used
      // $COVERAGE-OFF$
      override def unbind(key: String, serviceName: ServiceName): String = {
        textBinder.unbind(key, serviceName.value)
      }
      // $COVERAGE-ON$
    }

  private def sortByFromString(text: String): Either[String, OrganisationSortBy] = {
    Try(OrganisationSortBy.unsafeApply(text))
      .toOption
      .toRight(s"Cannot accept $text as OrganisationSortBy")
  }

  implicit def organisationSortByQueryStringBindable(implicit textBinder: QueryStringBindable[String]): QueryStringBindable[OrganisationSortBy] =
    new QueryStringBindable[OrganisationSortBy] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, OrganisationSortBy]] = {
        textBinder.bind(key, params).map {
          case Right(sortBy) => sortByFromString(sortBy)
          case _             => Left("Unable to bind OrganisationSortBy")
        }
      }

      // this is needed to compile but is not used
      // $COVERAGE-OFF$
      override def unbind(key: String, sortBy: OrganisationSortBy): String = {
        textBinder.unbind(key, sortBy.toString)
      }
      // $COVERAGE-ON$
    }

}
