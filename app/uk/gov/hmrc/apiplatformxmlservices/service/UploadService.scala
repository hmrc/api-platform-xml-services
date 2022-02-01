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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.GetByEmailsRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.UserResponse
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.EmailPreferences
import java.{util => ju}
import uk.gov.hmrc.apiplatformxmlservices.models.UserId
import play.api.Logging
import cats.data.EitherT
import cats.data.EitherT
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.GetOrCreateUserIdRequest
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.RegistrationRequest

@Singleton
class UploadService @Inject() (
    thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    uuidService: UuidService
  )(implicit val ec: ExecutionContext)
    extends Logging {

  def uploadUsers(users: List[ParsedUser])(implicit hc: HeaderCarrier) = {

    Future.sequence(users.zipWithIndex.map(x => uploadUser(x._1, x._2 + 1)))

  }

  private def uploadUser(parsedUser: ParsedUser, rowNumber: Int)(implicit hc: HeaderCarrier): Future[Either[UploadUserResult, CreatedOrUpdatedUser]] = {
    // Given a user that does exist in the api platform (dev hub / tpd)
    // When I import an unknown email address in the csv
    // Then the users account is untouched

    {
      for {
        validParsedUser <- EitherT(validateParsedUser(parsedUser))
        parsedUserWithUserId <- EitherT(createOrGetUser(validParsedUser, rowNumber))

        // TODO <- handle update organisations with user id, email
        // TODO <- merge / update email preferences
      } yield parsedUserWithUserId
    }.value

  }

  private def createOrGetUser(parsedUser: ParsedUser, rowNumber: Int)(implicit hc: HeaderCarrier): Future[Either[UploadUserResult, CreatedOrUpdatedUser]] = {

    def updateOrgAndUser(user: UserResponse): Future[Either[UploadUserResult, CreatedOrUpdatedUser]] = {
      // TODO: Add user to Organisation(s).
      // Map Services on User to XML Services in Json ready for email preferences
      // Merge any new Email preferences with old ones on User and update User

      Future.successful(Right(CreatedOrUpdatedUser.create(rowNumber, parsedUser, user, true)))
    }

    def createUser(parsedUser: ParsedUser)(implicit hc: HeaderCarrier): Future[Either[UploadUserResult, CreatedOrUpdatedUser]] = {

      thirdPartyDeveloperConnector.getOrCreateUserId(GetOrCreateUserIdRequest(parsedUser.email)).flatMap {
        case Right(user: CoreUserDetail) => {
          // Do we need to call register user after this????, false))
          thirdPartyDeveloperConnector.register(RegistrationRequest(user.email, uuidService.newUuid.toString, parsedUser.firstName, parsedUser.lastName, None)).map {
            case Right(_)           => {
              Right(CreatedOrUpdatedUser.create(
                rowNumber,
                parsedUser,
                UserResponse(parsedUser.email, parsedUser.firstName, parsedUser.lastName, true, EmailPreferences.noPreferences, user.userId),
                isExisting = false
              ))
            }
            case Left(e: Throwable) => Left(UploadUserFailedResult(s"Unable to register user on csv row number $rowNumber"))
          }

        }
        case _                           => Future.successful(Left(UploadUserFailedResult(s"Unable to create user on csv row number $rowNumber")))
      }

    }

    thirdPartyDeveloperConnector.getByEmail(GetByEmailsRequest(List(parsedUser.email))).flatMap {
      case Right(Nil)                       => createUser(parsedUser)
      case Right(users: List[UserResponse]) => updateOrgAndUser(users.head)
      case _                                => Future.successful(Left(UploadUserFailedResult(s"Error when retrieving user by email on csv row number $rowNumber")))
    }
  }

  private def validateParsedUser(user: ParsedUser): Future[Either[UploadUserResult, ParsedUser]] = {
    //TODO when vendor id is not string and services not just big string do validation
    // check vendor Ids exists, check service names are valid
    Future.successful(Right(user))
  }

}
