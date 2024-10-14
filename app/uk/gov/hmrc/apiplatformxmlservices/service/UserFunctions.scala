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

package uk.gov.hmrc.apiplatformxmlservices.service

import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.TaxRegimeInterests.hasAllApis
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.{CreateUserRequest, UserResponse}
import uk.gov.hmrc.apiplatformxmlservices.models.{CreateVerifiedUserFailedResult, CreateVerifiedUserSuccessResult, GetOrCreateUserFailedResult, OrganisationId, OrganisationUser}

trait UserFunctions {
  val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  val xmlApiService: XmlApiService

  def handleGetOrCreateUser(
      email: LaxEmailAddress,
      firstName: String,
      lastName: String
    )(implicit hc: HeaderCarrier,
      ec: ExecutionContext
    ): Future[Either[GetOrCreateUserFailedResult, UserResponse]] = {

    thirdPartyDeveloperConnector.createVerifiedUser(CreateUserRequest(email, firstName, lastName, Map.empty)).map {
      case x: CreateVerifiedUserSuccessResult    => Right(x.userResponse)
      case error: CreateVerifiedUserFailedResult => Left(GetOrCreateUserFailedResult(error.message))
    }

  }

  def toOrganisationUser(organisationId: OrganisationId, user: UserResponse): OrganisationUser = {
    val stableApis = xmlApiService.getStableApis()

    val xmlServiceNames: Set[ServiceName] = stableApis.map(_.serviceName).toSet

    val stableXmlApisThatUserSelectedSpecifically = for {
      filteredInterests <- user.emailPreferences.interests.filter(_.services.intersect(xmlServiceNames).nonEmpty)
      serviceName       <- filteredInterests.services.intersect(xmlServiceNames)
      xmlApi            <- xmlApiService.getStableApiByServiceName(serviceName)
    } yield xmlApi

    val stableXmlApisWhereUserSelectedAllForCategory = user.emailPreferences.interests
      .filter(hasAllApis).flatMap(x => xmlApiService.getStableApisByCategory(x.regime))

    val combinedApis = stableXmlApisWhereUserSelectedAllForCategory ++ stableXmlApisThatUserSelectedSpecifically
    OrganisationUser(organisationId, Some(user.userId), user.email, user.firstName, user.lastName, combinedApis)
  }

}
