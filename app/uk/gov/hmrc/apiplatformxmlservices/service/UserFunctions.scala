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
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators.GetOrCreateUserFailedResult
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.{ImportUserRequest, TaxRegimeInterests, UserResponse}
import uk.gov.hmrc.apiplatformxmlservices.models.{OrganisationId, OrganisationUser, XmlApiWithoutStatus}
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models.{CreateVerifiedUserFailedResult, CreateVerifiedUserSuccessResult}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait UserFunctions {
  val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector

  def handleGetOrCreateUser(email: String, firstName: String, lastName: String)
                                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[GetOrCreateUserFailedResult, UserResponse]] = {

    thirdPartyDeveloperConnector.createVerifiedUser(ImportUserRequest(email, firstName, lastName, Map.empty)).map{
        case x: CreateVerifiedUserSuccessResult => Right(x.userResponse)
        case error: CreateVerifiedUserFailedResult => Left(GetOrCreateUserFailedResult(error.message))
      }

  }

  def toOrganisationUser(organisationId: OrganisationId, user: UserResponse): OrganisationUser ={

    val xmlServiceNames: Set[String] = XmlApiWithoutStatus.liveXmlApisWithoutStatus.map(_.serviceName.value).toSet
    def getXmlApiByServiceName(serviceName: String): Option[XmlApiWithoutStatus] ={
      XmlApiWithoutStatus.liveXmlApisWithoutStatus.find(_.serviceName.value == serviceName)
    }

     val filteredXmlEmailPreferences = for { filteredInterests <- user.emailPreferences.interests.filter(x => x.services.intersect(xmlServiceNames).nonEmpty)
                serviceName <- filteredInterests.services.intersect(xmlServiceNames)
                xmlApi <-  getXmlApiByServiceName(serviceName)
            }  yield xmlApi


      if(filteredXmlEmailPreferences.isEmpty){ OrganisationUser(organisationId, user.userId, user.email, user.firstName, user.lastName, List.empty) }
      else { OrganisationUser(organisationId, user.userId, user.email, user.firstName, user.lastName, filteredXmlEmailPreferences) }
    }



}
