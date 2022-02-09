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

import cats.data.EitherT
import play.api.Logging
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.ImportUserRequest
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.UserResponse
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class UploadService @Inject() (
    thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    organisationService: OrganisationService
  )(implicit val ec: ExecutionContext)
    extends Logging {

  def uploadUsers(users: List[ParsedUser])(implicit hc: HeaderCarrier) = {

    Future.sequence(users.zipWithIndex.map(x => uploadUser(x._1, x._2 + 1)))

  }

  private def uploadUser(parsedUser: ParsedUser, rowNumber: Int)(implicit hc: HeaderCarrier): Future[UploadUserResult] = {
    // Given a user that does exist in the api platform (dev hub / tpd)
    // When I import an unknown email address in the csv
      // Then the users account is untouched
    validateParsedUser(parsedUser, rowNumber).flatMap{
      case e : ValidUserResult => createOrGetUser(parsedUser, rowNumber)
      case invalidResult : ValidateUserResult => Future.successful(InvalidUserResult(invalidResult.message))
    }
  
  }

  private def createOrGetUser(parsedUser: ParsedUser, rowNumber: Int)(implicit hc: HeaderCarrier): Future[UploadUserResult] = {

      // TODO: Add user to Organisation(s).
      // Map Services on User to XML Services in Json ready for email preferences
      // Merge any new Email preferences with old ones on User and update User

          thirdPartyDeveloperConnector.createVerifiedUser(ImportUserRequest(parsedUser.email, parsedUser.firstName, parsedUser.lastName)).map {
            case CreatedUserResult(userResponse: UserResponse)           => {
             UploadCreatedUserSuccessResult(CreatedOrUpdatedUser.create(
                rowNumber,
                parsedUser,
                userResponse
              ))
            }
            case RetrievedUserResult(userResponse: UserResponse)           => {
             UploadExistingUserSuccessResult(CreatedOrUpdatedUser.create(
                rowNumber,
                parsedUser,
                userResponse
              ))
            }
            case CreateVerifiedUserFailedResult(message: String) => CreateOrGetUserFailedResult(s"RowNumber:$rowNumber - Unable to get or create user - $message")
          }
    
    }

  private def validateParsedUser(user: ParsedUser, rowNumber: Int): Future[ValidateUserResult] = {
    //TODO when vendor id is not string and services not just big string do validation
    // check vendor Ids exists, check service names are valid
  
      user.vendorIds match {
      case Nil =>    Future.successful(MissingVendorIdResult(s"RowNumber:$rowNumber - missing vendorIds on user")) 
      case vendorIds: List[VendorId] => validateVendorIds(vendorIds) map {
        case true  => ValidUserResult("ok")
        case false => InvalidVendorIdResult(s"RowNumber:$rowNumber - Invalid vendorId(s)")
      }
     
    }
  }

  private def validateVendorIds(vendorIds: List[VendorId]) = {

    Future.sequence(vendorIds.map(organisationService.findByVendorId(_)))
    .map(x => x.flatten.size==vendorIds.size && vendorIds.nonEmpty)
    
  }
}
