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

package uk.gov.hmrc.apiplatformxmlservices.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import play.api.Logging
import play.api.http.Status.{CREATED, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, InternalServerException, StringContextOps}

import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector.Config
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper._
import uk.gov.hmrc.apiplatformxmlservices.models.{CreateVerifiedUserFailedResult, CreateVerifiedUserResult, CreatedUserResult, RetrievedUserResult}

@Singleton
class ThirdPartyDeveloperConnector @Inject() (http: HttpClientV2, config: Config)(implicit val ec: ExecutionContext) extends Logging {

  def getOrCreateUserId(getOrCreateUserIdRequest: GetOrCreateUserIdRequest)(implicit hc: HeaderCarrier): Future[Either[Throwable, CoreUserDetail]] = {
    http.post(url"${config.thirdPartyDeveloperUrl}/developers/user-id")
      .withBody(Json.toJson(getOrCreateUserIdRequest))
      .execute[Option[UserIdResponse]]
      .map {
        case Some(response) => Right(CoreUserDetail(response.userId, getOrCreateUserIdRequest.email))
        case _              => Left(new InternalServerException("Could not find or create user"))
      }.recover {
        case NonFatal(e) =>
          logger.error(e.getMessage)
          Left(e)
      }
  }

  def getByEmail(emails: List[LaxEmailAddress])(implicit hc: HeaderCarrier): Future[Either[Throwable, List[UserResponse]]] = {
    http.post(url"${config.thirdPartyDeveloperUrl}/developers/get-by-emails")
      .withBody(Json.toJson(emails))
      .execute[List[UserResponse]]
      .map(x => Right(x)).recover {
        case NonFatal(e) =>
          logger.error(e.getMessage)
          Left(e)
      }
  }

  def createVerifiedUser(request: CreateUserRequest)(implicit hc: HeaderCarrier): Future[CreateVerifiedUserResult] = {
    http.post(url"${config.thirdPartyDeveloperUrl}/import-user")
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case CREATED => CreatedUserResult(response.json.as[UserResponse])
          case OK      => RetrievedUserResult(response.json.as[UserResponse])
          case _       => CreateVerifiedUserFailedResult("Could not get or create user")
        }
      }.recover {
        case NonFatal(e) => CreateVerifiedUserFailedResult(e.getMessage)
      }
  }

}

object ThirdPartyDeveloperConnector {
  case class Config(thirdPartyDeveloperUrl: String)
}
