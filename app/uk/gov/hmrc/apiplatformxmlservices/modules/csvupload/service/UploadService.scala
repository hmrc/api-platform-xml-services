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

package uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.service

import cats.data.{NonEmptyList, Validated}
import cats.instances.list._
import cats.syntax.either._
import cats.syntax.traverse._
import play.api.Logging
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators.{ManageCollaboratorResult, OrganisationAlreadyHasCollaboratorResult}
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.{ImportUserRequest, UserResponse}
import uk.gov.hmrc.apiplatformxmlservices.modules.csvupload.models._
import uk.gov.hmrc.apiplatformxmlservices.service.{OrganisationService, TeamMemberService, XmlApiService}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadService @Inject() (
    thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    organisationService: OrganisationService,
    override val xmlApiService: XmlApiService,
    teamMemberService: TeamMemberService
  )(implicit val ec: ExecutionContext)
    extends Logging
    with UploadValidation
    with ConvertToEmailPrefsMap {

  def uploadUsers(users: List[ParsedUser])(implicit hc: HeaderCarrier): Future[List[UploadUserResult]] = {
    val batchSize = 10
    Future.sequence(users.grouped(batchSize).toList.flatMap(batchOf10Users => {
      Thread.sleep(500)
      batchOf10Users.zipWithIndex.par.map(x => {
        uploadUser(x._1, x._2 + 1)
      })
    }))

  }

  private def uploadUser(parsedUser: ParsedUser, rowNumber: Int)(implicit hc: HeaderCarrier): Future[UploadUserResult] = {

    validateParsedUser(parsedUser, rowNumber, organisationService.findByVendorId).flatMap {
      case Validated.Valid(_)                              => handleCreateOrGetUserResult(parsedUser, rowNumber)
      case Validated.Invalid(errors: NonEmptyList[String]) => Future.successful(InvalidUserResult(errors.toList.mkString(" | ")))
    }

  }

  private def handleCreateOrGetUserResult(parsedUser: ParsedUser, rowNumber: Int)(implicit hc: HeaderCarrier): Future[UploadUserResult] = {
    Thread.sleep(300)
    createOrGetUser(parsedUser) flatMap {
      case result: CreateVerifiedUserSuccessResult => handleAddCollaboratorToOrgs(result, parsedUser.vendorIds, rowNumber)
      case e: CreateVerifiedUserFailedResult       =>
        Future.successful(CreateOrGetUserFailedResult(s"RowNumber:$rowNumber - failed to get or create User: ${e.message}"))
    }
  }

  private def handleAddCollaboratorToOrgs(result: CreateVerifiedUserSuccessResult, vendors: List[VendorId], rowNumber: Int): Future[UploadUserResult] = {
    def mapSuccessResult(result: CreateVerifiedUserSuccessResult) = {
      result match {
        case CreatedUserResult(userResponse: UserResponse)   => UploadCreatedUserSuccessResult(rowNumber, userResponse)
        case RetrievedUserResult(userResponse: UserResponse) => UploadExistingUserSuccessResult(rowNumber, userResponse)
      }
    }

    val results: Future[List[Either[AddUserToOrgFailureResult, UploadSuccessResult]]] =
      Future.sequence(vendors.map(vendorId => {
        teamMemberService.handleAddCollaboratorToOrgByVendorId(result.userResponse.email, result.userResponse.userId, vendorId)
          .map {
            case Right(_: Organisation)                            => Right(mapSuccessResult(result))
            case Left(_: OrganisationAlreadyHasCollaboratorResult) => Right(mapSuccessResult(result))
            case Left(errorResult: ManageCollaboratorResult)       =>
              Left(AddUserToOrgFailureResult(s"RowNumber:$rowNumber - failed to add user " +
                s"${result.userResponse.userId.value} to vendorId ${vendorId.value} : ${errorResult.message}"))
          }
      }))
    // from https://stackoverflow.com/questions/56501107/convert-listeithera-b-to-eitherlista-listb
    results.map(x =>
      x.traverse(_.toValidated.bimap(List(_), identity)).toEither match {
        case Left(errors: List[AddUserToOrgFailureResult]) => AddUserToOrgFailureResult(errors.map(_.message).mkString(" | "))
        case Right(successes: List[UploadSuccessResult])   => successes.head
      }
    )

  }

  private def createOrGetUser(parsedUser: ParsedUser)(implicit hc: HeaderCarrier): Future[CreateVerifiedUserResult] = {
    val request = ImportUserRequest(parsedUser.email, parsedUser.firstName, parsedUser.lastName, extractEmailPreferencesFromUser(parsedUser, xmlApiService.getStableApis()))
    thirdPartyDeveloperConnector.createVerifiedUser(request)
  }

}
