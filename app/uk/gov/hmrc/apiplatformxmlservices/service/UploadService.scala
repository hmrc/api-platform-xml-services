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

@Singleton
class UploadService @Inject() (
    organisationRepository: OrganisationRepository,
    uuidService: UuidService,
    thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext)
    extends Logging {

  def uploadUsers(users: Seq[ParsedUser])(implicit hc: HeaderCarrier) = {

    users.map(handleUploadUser)

  }

  private def handleUploadUser(parsedUser: ParsedUser)(implicit hc: HeaderCarrier) = {
    // Given a user that does exist in the api platform (dev hub / tpd)
    // When I import an unknown email address in the csv
    // Then the users account is untouched

      for{
        validParsedUser <- EitherT(validateParsedUser(parsedUser))
        parsedUserWithUserId <- EitherT(createOrGetUser(validParsedUser))

        // TODO <- handle update organisations with user id, email
        // TODO <- merge / update email preferences
      } yield parsedUserWithUserId


  }

  private def createOrGetUser(parsedUser: ParsedUser)(implicit hc: HeaderCarrier): Future[Either[UploadUserResult, UserDto]] ={

        def updateOrgAndUser(user: UserResponse): Either[UploadUserResult, UserDto] = {
      // TODO: Add user to Organisation(s).
      // Map Services on User to XML Services in Json
      // Merge any new Email preferences with old ones on User and update User

      logger.debug(s"*** User email ${user.email} - has gone down Update User route")
      Right(UserDto.fromParsedUser(parsedUser, user.id))
    }

    def createUser(parsedUser: ParsedUser): Either[UploadUserResult, UserDto]  = {
       logger.debug(s"*** User email ${parsedUser.email} - has gone down Create User route")

       //TODO  call TPD to create user
     val user =UserResponse(parsedUser.email, parsedUser.firstName, parsedUser.lastName, true, EmailPreferences.noPreferences, UserId(ju.UUID.randomUUID()))
      Right(UserDto.fromParsedUser(parsedUser, user.id))
    }

    thirdPartyDeveloperConnector.getByEmail(GetByEmailsRequest(List(parsedUser.email))).map {
          case Right(users: List[UserResponse]) if users.nonEmpty => updateOrgAndUser(users.head)
          case _                                                  => createUser(parsedUser)
        }
  }

  private def validateParsedUser(user: ParsedUser): Future[Either[UploadUserResult, ParsedUser]] = {
    //TODO when vendor id is not string and services not just big string do validation
    Future.successful(Right(user))
  }

}
