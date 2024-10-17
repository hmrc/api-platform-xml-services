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

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import cats.data.EitherT
import cats.implicits._

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.UserResponse
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.apiplatformxmlservices.util.ApplicationLogger

@Singleton
class TeamMemberService @Inject() (
    organisationRepository: OrganisationRepository,
    override val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    override val xmlApiService: XmlApiService
  )(implicit val ec: ExecutionContext
  ) extends UserFunctions with ApplicationLogger {

  def removeCollaborator(organisationId: OrganisationId, request: RemoveCollaboratorRequest): Future[Either[ManageCollaboratorResult, Organisation]] = {
    (for {
      organisation        <- EitherT(handleFindByOrgId(organisationId))
      _                   <- EitherT(collaboratorCanBeDeleted(organisation, request.email))
      updatedOrganisation <- EitherT(handleRemoveCollaboratorFromOrg(organisation, request.email))
    } yield updatedOrganisation).value
  }

  def removeAllCollaboratorsForUserId(request: RemoveAllCollaboratorsForUserIdRequest): Future[List[UpdateOrganisationResult]] = {
    for {
      organisations        <- organisationRepository.findByUserId(request.userId)
      updatedOrganisations <- Future.traverse(organisations)(handleRemoveCollaboratorFromOrg(request.userId))
      _                     = logger.info(s"Removed all XML vender collaborators for userId $request.userId")
    } yield updatedOrganisations
  }

  private def handleRemoveCollaboratorFromOrg(userId: UserId)(organisation: Organisation) = {
    organisationRepository.removeCollaboratorFromOrganisation(organisation.organisationId, userId)
  }

  def addCollaborator(
      organisationId: OrganisationId,
      email: LaxEmailAddress,
      firstName: String,
      lastName: String
    )(implicit hc: HeaderCarrier
    ): Future[Either[ManageCollaboratorResult, Organisation]] = {
    (for {
      organisation        <- EitherT(handleFindByOrgId(organisationId))
      _                   <- EitherT(collaboratorCanBeAdded(organisation, email))
      coreUserDetail      <- EitherT(handleGetOrCreateUser(email, firstName, lastName))
      updatedOrganisation <- EitherT(handleAddCollaboratorToOrg(coreUserDetail.email, coreUserDetail.userId, organisation))
    } yield updatedOrganisation).value
  }

  def getOrganisationUserByOrganisationId(organisationId: OrganisationId)(implicit hc: HeaderCarrier): Future[List[OrganisationUser]] = {
    getOrganisationById(organisationId).flatMap {
      case None                             => Future.successful(List.empty)
      case Some(organisation: Organisation) => handleGetOrganisationUsers(organisationId, organisation.collaborators).map(x => if (x.nonEmpty) x.flatten.distinct else List.empty)
    }
  }

  private def handleGetOrganisationUsers(organisationId: OrganisationId, collaborators: List[Collaborator])(implicit hc: HeaderCarrier): Future[List[List[OrganisationUser]]] = {
    collaborators.map(x => getDeveloperByEmail(organisationId, x.email)).sequence
  }

  private def getDeveloperByEmail(organisationId: OrganisationId, email: LaxEmailAddress)(implicit hc: HeaderCarrier) = {
    def mapResult(results: Future[Either[Throwable, List[UserResponse]]]): Future[List[OrganisationUser]] = results map {
      case Right(Nil)                       => List(OrganisationUser(organisationId, None, email, "", "", List.empty[XmlApi])) // User not found in TPD - just return minimum data
      case Right(users: List[UserResponse]) => List(toOrganisationUser(organisationId, users.head))
      case _                                => List.empty[OrganisationUser]
    }

    mapResult(thirdPartyDeveloperConnector.getByEmail(List(email)))
  }

  private def getOrganisationById(organisationId: OrganisationId): Future[Option[Organisation]] = {
    organisationRepository.findByOrgId(organisationId)
  }

  private def handleFindByOrgId(organisationId: OrganisationId): Future[Either[ManageCollaboratorResult, Organisation]] = {
    getOrganisationById(organisationId).map {
      case None                             => Left(GetOrganisationFailedResult(s"Failed to get organisation for Id: ${organisationId.value}"))
      case Some(organisation: Organisation) => Right(organisation)
    }
  }

  def handleAddCollaboratorToOrgByVendorId(email: LaxEmailAddress, userId: UserId, vendorId: VendorId): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.addCollaboratorByVendorId(vendorId, Collaborator(userId, email)).map {
      case Right(organisation: Organisation) => Right(organisation)
      case Left(value)                       => Left(UpdateCollaboratorFailedResult(value.getMessage))
    }
  }

  private def handleAddCollaboratorToOrg(email: LaxEmailAddress, userId: UserId, organisation: Organisation): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.addCollaboratorToOrganisation(organisation.organisationId, Collaborator(userId, email)).map {
      case Right(organisation: Organisation) => Right(organisation)
      case Left(value)                       => Left(UpdateCollaboratorFailedResult(value.getMessage))
    }
  }

  private def handleRemoveCollaboratorFromOrg(organisation: Organisation, emailAddress: LaxEmailAddress): Future[Either[ManageCollaboratorResult, Organisation]] = {
    val updatedOrg = organisation.copy(collaborators =
      organisation.collaborators.filterNot(_.email == emailAddress)
    )
    handleUpdateOrganisation(updatedOrg)
  }

  private def handleUpdateOrganisation(organisation: Organisation): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.createOrUpdate(organisation).map {
      case Left(value)              => Left(UpdateCollaboratorFailedResult(value.getMessage))
      case Right(org: Organisation) => Right(org)
    }
  }

  private def organisationHasCollaborator(organisation: Organisation, emailAddress: LaxEmailAddress): Boolean = {
    organisation.collaborators.exists(_.email == emailAddress)
  }

  private def collaboratorCanBeDeleted(organisation: Organisation, emailAddress: LaxEmailAddress): Future[Either[ManageCollaboratorResult, Organisation]] = {
    if (organisationHasCollaborator(organisation, emailAddress)) successful(Right(organisation))
    else successful(Left(ValidateCollaboratorFailureResult("Collaborator not found on Organisation")))
  }

  private def collaboratorCanBeAdded(organisation: Organisation, emailAddress: LaxEmailAddress): Future[Either[ManageCollaboratorResult, Organisation]] = {
    if (organisationHasCollaborator(organisation, emailAddress)) successful(Left(OrganisationAlreadyHasCollaboratorResult("")))
    else successful(Right(organisation))
  }

}
