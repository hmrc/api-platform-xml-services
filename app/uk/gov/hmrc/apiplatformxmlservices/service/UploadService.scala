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
import cats.data.Validated
import cats.syntax.traverse._
import cats.instances.list._
import cats.syntax.either._
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
    validateParsedUser(parsedUser, rowNumber).flatMap {
      case e: ValidUserResult                => handleCreateOrGetUserResult(parsedUser, rowNumber)
      case invalidResult: ValidateUserResult => Future.successful(InvalidUserResult(invalidResult.message))
    }

  }

  private def handleCreateOrGetUserResult(parsedUser: ParsedUser, rowNumber: Int)(implicit hc: HeaderCarrier): Future[UploadUserResult] = {
    createOrGetUser(parsedUser, rowNumber) map {
      case result: CreatedUserResult => handleAddCollaboratorToOrgs(result, parsedUser.vendorIds, rowNumber)
      case result: RetrievedUserResult => handleAddCollaboratorToOrgs(result, parsedUser.vendorIds, rowNumber)
    }
  }

  private def handleAddCollaboratorToOrgs(result: CreateVerifiedUserSuccessResult, vendors: List[VendorId], rowNumber: Int)
  (implicit hc: HeaderCarrier) = {
    def mapSuccessResult(result: CreateVerifiedUserSuccessResult) = {
      result match {
        case CreatedUserResult(userResponse: UserResponse)   => UploadCreatedUserSuccessResult(rowNumber, userResponse)
        case RetrievedUserResult(userResponse: UserResponse) => UploadExistingUserSuccessResult(rowNumber, userResponse)
      }
    }

    val results: Future[List[Either[AddUserToOrgFailureResult, UploadSuccessResult]]] =
      Future.sequence(vendors.map(vendorId => {
        organisationService.addCollaboratorByVendorId(vendorId, result.userResponse.email, result.userResponse.userId)
          .map {
            case Right(organisation: Organisation)      => Right(mapSuccessResult(result))
            case Left(errorResult: ManageCollaboratorResult) =>
              Left(AddUserToOrgFailureResult(s"RowNumber:$rowNumber - failed to add user ${result.userResponse.userId.value} to vendorId ${vendorId.value} : ${errorResult.message}"))
          }
      }))
    // check list for lefts and return combined messages

    // from https://stackoverflow.com/questions/56501107/convert-listeithera-b-to-eitherlista-listb
    //xs.traverse(_.toValidated.bimap(List(_), identity)).toEither

    // List(Left("error1"), Left("error2")) => Left(List("error1", "error2"))
    // List(Right(10), Right(20))           => Right(List(10, 20))
    // List(Right(10), Left("error2"))      => Left(List("error2"))

    results.map(x => x.traverse(_.toValidated.bimap(List(_), identity)).toEither match {
      case Left(errors: List[AddUserToOrgFailureResult]) => AddUserToOrgFailureResult(errors.map(_.message).mkString(" | "))
      case Right(successes: List[UploadSuccessResult])   => successes.head
    })

  }

  private def createOrGetUser(parsedUser: ParsedUser, rowNumber: Int)(implicit hc: HeaderCarrier): Future[UploadUserResult] = {
    thirdPartyDeveloperConnector.createVerifiedUser(ImportUserRequest(parsedUser.email, parsedUser.firstName, parsedUser.lastName))
    
  }

  private def validateParsedUser(user: ParsedUser, rowNumber: Int): Future[ValidateUserResult] = {
    //TODO when vendor id is not string and services not just big string do validation
    // check vendor Ids exists, check service names are valid

    user.vendorIds match {
      case Nil                       => Future.successful(MissingVendorIdResult(s"RowNumber:$rowNumber - missing vendorIds on user"))
      case vendorIds: List[VendorId] => validateVendorIds(vendorIds) map {
          case true  => ValidUserResult("ok")
          case false => InvalidVendorIdResult(s"RowNumber:$rowNumber - Invalid vendorId(s)")
        }

    }
  }

  private def validateVendorIds(vendorIds: List[VendorId]) = {

    Future.sequence(vendorIds.map(organisationService.findByVendorId(_)))
      .map(x => x.flatten.size == vendorIds.size && vendorIds.nonEmpty)

  }
}
