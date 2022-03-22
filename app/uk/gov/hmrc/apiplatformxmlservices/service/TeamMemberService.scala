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
import cats.implicits._
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.UserResponse
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamMemberService @Inject()(organisationRepository: OrganisationRepository,
                                  override val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                                  override val xmlApiService: XmlApiService
                                 )(implicit val ec: ExecutionContext) extends UserFunctions {

  def removeCollaborator(organisationId: OrganisationId, request: RemoveCollaboratorRequest): Future[Either[ManageCollaboratorResult, Organisation]] = {
    (for {
      organisation <- EitherT(handleFindByOrgId(organisationId))
      _ <- EitherT(collaboratorCanBeDeleted(organisation, request.email))
      updatedOrganisation <- EitherT(handleRemoveCollaboratorFromOrg(organisation, request.email))
    } yield updatedOrganisation).value
  }

  def addCollaborator(organisationId: OrganisationId, email: String, firstName: String, lastName: String)
                     (implicit hc: HeaderCarrier): Future[Either[ManageCollaboratorResult, Organisation]] = {
    (for {
      organisation <- EitherT(handleFindByOrgId(organisationId))
      _ <- EitherT(collaboratorCanBeAdded(organisation, email))
      coreUserDetail <- EitherT(handleGetOrCreateUser(email, firstName, lastName))
      updatedOrganisation <- EitherT(handleAddCollaboratorToOrg(coreUserDetail.email, coreUserDetail.userId, organisation))
    } yield updatedOrganisation).value
  }


  def getOrganisationUserByOrganisationId(organisationId: OrganisationId)(implicit hc: HeaderCarrier): Future[List[OrganisationUser]] = {
    getOrganisationById(organisationId).flatMap {
      case None => Future.successful(List.empty)
      case Some(organisation: Organisation) => handleGetOrganisationUsers(organisation.collaborators).map(x => if(x.nonEmpty) x.flatten.distinct else List.empty)
    }.map(users => users.map(user => toOrganisationUser(organisationId, user)))
  }

  private def handleGetOrganisationUsers(collaborators: List[Collaborator])(implicit hc: HeaderCarrier): Future[List[List[UserResponse]]] = {
    def mapResults(results: Future[Either[Throwable, List[UserResponse]]]): Future[List[UserResponse]] = results map {
      case Right(Nil) => List.empty[UserResponse]
      case Right(users: List[UserResponse]) => List(users.head)
      case _ => List.empty[UserResponse]
    }

    collaborators.map(x => thirdPartyDeveloperConnector.getByEmail(List(x.email)))
      .map(mapResults).sequence
  }


  private def getOrganisationById(organisationId: OrganisationId): Future[Option[Organisation]] = {
    organisationRepository.findByOrgId(organisationId)
  }

  private def handleFindByOrgId(organisationId: OrganisationId): Future[Either[ManageCollaboratorResult, Organisation]] = {
    getOrganisationById(organisationId).map {
      case None => Left(GetOrganisationFailedResult(s"Failed to get organisation for Id: ${organisationId.value.toString}"))
      case Some(organisation: Organisation) => Right(organisation)
    }
  }

  def handleAddCollaboratorToOrgByVendorId(email: String, userId: UserId, vendorId: VendorId): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.addCollaboratorByVendorId(vendorId, Collaborator(userId, email)).map {
      case Right(organisation: Organisation) => Right(organisation)
      case Left(value) => Left(UpdateCollaboratorFailedResult(value.getMessage))
    }
  }

  private def handleAddCollaboratorToOrg(email: String, userId: UserId, organisation: Organisation): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.addCollaboratorToOrganisation(organisation.organisationId, Collaborator(userId, email)).map {
      case Right(organisation: Organisation) => Right(organisation)
      case Left(value) => Left(UpdateCollaboratorFailedResult(value.getMessage))
    }
  }

  private def handleRemoveCollaboratorFromOrg(organisation: Organisation, emailAddress: String): Future[Either[ManageCollaboratorResult, Organisation]] = {
    val updatedOrg = organisation.copy(collaborators =
      organisation.collaborators.filterNot(_.email.equalsIgnoreCase(emailAddress)))
    handleUpdateOrganisation(updatedOrg)
  }

  private def handleUpdateOrganisation(organisation: Organisation): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.createOrUpdate(organisation).map {
      case Left(value) => Left(UpdateCollaboratorFailedResult(value.getMessage))
      case Right(org: Organisation) => Right(org)
    }
  }

  private def organisationHasCollaborator(organisation: Organisation, emailAddress: String): Boolean = {
    organisation.collaborators.exists(_.email.equalsIgnoreCase(emailAddress))
  }

  private def collaboratorCanBeDeleted(organisation: Organisation, emailAddress: String): Future[Either[ManageCollaboratorResult, Organisation]] = {
    if (organisationHasCollaborator(organisation, emailAddress)) successful(Right(organisation))
    else successful(Left(ValidateCollaboratorFailureResult("Collaborator not found on Organisation")))
  }

  private def collaboratorCanBeAdded(organisation: Organisation, emailAddress: String): Future[Either[ManageCollaboratorResult, Organisation]] = {
    if (organisationHasCollaborator(organisation, emailAddress)) successful(Left(OrganisationAlreadyHasCollaboratorResult("")))
    else successful(Right(organisation))
  }


}
