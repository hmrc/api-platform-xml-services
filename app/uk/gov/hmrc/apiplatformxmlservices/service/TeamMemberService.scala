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
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper._
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamMemberService @Inject()(
    organisationRepository: OrganisationRepository,
    override val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext) extends RetrieveOrCreateUser {

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
      updatedOrganisation <- EitherT(handleAddCollaboratorToOrg(coreUserDetail, organisation))
    } yield updatedOrganisation).value
  }

  def addCollaboratorByVendorId(vendorId: VendorId, email: String, userId: UserId): Future[Either[ManageCollaboratorResult, Organisation]] ={
    //vendor id should exist and be valid at this point (import flow)
    (for {
      organisation <- EitherT(handleFindByVendorId(vendorId))
      _ <- EitherT(collaboratorCanBeAdded(organisation, email))
      updatedOrganisation <- EitherT(handleAddCollaboratorToOrg(CoreUserDetail(userId, email), organisation))
    } yield updatedOrganisation).value
  }

  private def handleFindByOrgId(organisationId: OrganisationId): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.findByOrgId(organisationId).map {
      case None                             => Left(GetOrganisationFailedResult(s"Failed to get organisation for Id: ${organisationId.value.toString}"))
      case Some(organisation: Organisation) => Right(organisation)
    }
  }

  private def handleFindByVendorId(vendorId: VendorId): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.findByVendorId(vendorId).map {
      case None                             => Left(GetOrganisationFailedResult(s"Failed to get organisation for Vendor Id: ${vendorId.value}"))
      case Some(organisation: Organisation) => Right(organisation)
    }
  }

  private def handleAddCollaboratorToOrg(coreUserDetail: CoreUserDetail, organisation: Organisation): Future[Either[ManageCollaboratorResult, Organisation]] = {
    val updatedOrg = organisation.copy(collaborators = organisation.collaborators :+ Collaborator(coreUserDetail.userId, coreUserDetail.email))
    handleUpdateOrganisation(updatedOrg)
  }

  private def handleRemoveCollaboratorFromOrg(organisation: Organisation, emailAddress: String): Future[Either[ManageCollaboratorResult, Organisation]] = {
    val updatedOrg = organisation.copy(collaborators =
      organisation.collaborators.filterNot(_.email.equalsIgnoreCase(emailAddress)))
    handleUpdateOrganisation(updatedOrg)
  }

  private def handleUpdateOrganisation(organisation: Organisation): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.createOrUpdate(organisation).map {
      case Left(value)              => Left(UpdateCollaboratorFailedResult(value.getMessage))
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
