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

package uk.gov.hmrc.apiplatformxmlservices.service

import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.{CoreUserDetail, ImportUserRequest, UserResponse}
import uk.gov.hmrc.apiplatformxmlservices.models.{CreateVerifiedUserFailedResult, CreateVerifiedUserSuccessResult, GetOrCreateUserFailedResult}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait RetrieveOrCreateUser {
  val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector

  def handleGetOrCreateUser(email: String, firstName: String, lastName: String)
                                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[GetOrCreateUserFailedResult, CoreUserDetail]] = {

    def toCoreUserDetail(userResponse: UserResponse) ={
      CoreUserDetail(userResponse.userId, userResponse.email)
    }

    thirdPartyDeveloperConnector.createVerifiedUser(ImportUserRequest(email, firstName, lastName, Map.empty)).map{
        case x: CreateVerifiedUserSuccessResult => Right(toCoreUserDetail(x.userResponse))
        case error: CreateVerifiedUserFailedResult => Left(GetOrCreateUserFailedResult(error.message))
      }

  }



}
