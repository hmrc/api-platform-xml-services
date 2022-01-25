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
import com.mongodb.MongoCommandException
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper._
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future.successful
import scala.util.control.NonFatal

@Singleton
class OrganisationService @Inject() (
    organisationRepository: OrganisationRepository,
    uuidService: UuidService,
    vendorIdService: VendorIdService,
    thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext) {

  def findAndCreateOrUpdate(organisationName: OrganisationName, vendorId: VendorId) = {
    organisationRepository.findByVendorId(vendorId) flatMap {
      case Some(organisation) => organisationRepository.createOrUpdate(organisation.copy(name = organisationName))
      case None               => createOrganisation(organisationName, vendorId)
    }
  }

  def create(request: CreateOrganisationRequest)(implicit hc: HeaderCarrier): Future[CreateOrganisationResult] = {
    vendorIdService.getNextVendorId().flatMap {
      case Right(vendorId: VendorId) => handleGetOrCreateUserId(request.email).flatMap {
          case Right(user: CoreUserDetail)            => handleCreateOrganisation(request.organisationName, vendorId, List(Collaborator(user.userId, request.email)))
          case Left(e: GetOrCreateUserIdFailedResult) => successful(CreateOrganisationFailedResult(e.message))
        }
      case Left(e: Exception)        => successful(CreateOrganisationFailedResult(e.getMessage))
      case _                         => successful(CreateOrganisationFailedResult("Unexpected Result from next vendor Id"))
    }.recover {
      case NonFatal(e: Throwable) => CreateOrganisationFailedResult(e.getMessage)
    }
  }

  def handleCreateOrganisation(organisationName: OrganisationName, vendorId: VendorId, collaborators: List[Collaborator] = List.empty): Future[CreateOrganisationResult] = {

    def mapError(ex: Exception): CreateOrganisationResult = ex match {
      case ex: MongoCommandException if ex.getErrorCode == 11000 => CreateOrganisationFailedDuplicateIdResult(ex.getMessage)
      case ex: Exception                                         => CreateOrganisationFailedResult(ex.getMessage)
    }

    organisationRepository.createOrUpdate(
      Organisation(
        organisationId = generateOrganisationId(),
        name = organisationName,
        vendorId = vendorId,
        collaborators = collaborators
      )
    ).map(result =>
      result
        .fold(mapError, x => CreateOrganisationSuccessResult(x))
    )
      .recover {
        case ex: Exception => mapError(ex)
      }
  }

  def update(organisation: Organisation): Future[Either[Exception, Organisation]] =
    organisationRepository.createOrUpdate(organisation)

  def updateOrganisationDetails(organisationId: OrganisationId, organisationName: OrganisationName) = {
    organisationRepository.updateOrganisationDetails(organisationId, organisationName)
  }

  def deleteByOrgId(organisationId: OrganisationId): Future[Boolean] =
    organisationRepository.deleteByOrgId(organisationId)

  def findByOrgId(organisationId: OrganisationId): Future[Option[Organisation]] =
    organisationRepository.findByOrgId(organisationId)

  def findByVendorId(vendorId: VendorId): Future[Option[Organisation]] =
    organisationRepository.findByVendorId(vendorId)

  def findByOrganisationName(organisationName: OrganisationName): Future[List[Organisation]] =
    organisationRepository.findByOrganisationName(organisationName)

  def findAll(sortBy: Option[OrganisationSortBy] = None): Future[List[Organisation]] = organisationRepository.findAll(sortBy)

  def removeCollaborator(organisationId: OrganisationId, request: RemoveCollaboratorRequest)(implicit hc: HeaderCarrier): Future[Either[ManageCollaboratorResult, Organisation]] = {
    (for {
      organisation <- EitherT(handleFindByOrgId(organisationId))
      _ <- EitherT(collaboratorCanBeDeleted(organisation, request.email))
      updatedOrganisation <- EitherT(handleRemoveCollaboratorFromOrg(organisation, request.email))
    } yield updatedOrganisation).value
  }

  def addCollaborator(organisationId: OrganisationId, email: String)(implicit hc: HeaderCarrier): Future[Either[ManageCollaboratorResult, Organisation]] = {
    (for {
      organisation <- EitherT(handleFindByOrgId(organisationId))
      _ <- EitherT(collaboratorCanBeAdded(organisation, email))
      coreUserDetail <- EitherT(handleGetOrCreateUserId(email))
      updatedOrganisation <- EitherT(handleAddCollaboratorToOrg(coreUserDetail, organisation))
    } yield updatedOrganisation).value
  }

  private def createOrganisation(organisationName: OrganisationName, vendorId: VendorId): Future[Either[Exception, Organisation]] = {
    organisationRepository.createOrUpdate(
      Organisation(
        organisationId = generateOrganisationId,
        name = organisationName,
        vendorId = vendorId
      )
    )
  }

  private def handleUpdateOrganisation(organisation: Organisation): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.createOrUpdate(organisation).map {
      case Left(value)              => Left(UpdateCollaboratorFailedResult(value.getMessage))
      case Right(org: Organisation) => Right(org)
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

  private def organisationHasCollaborator(organisation: Organisation, emailAddress: String): Boolean = {
    organisation.collaborators.exists(_.email.equalsIgnoreCase(emailAddress))
  }

  private def collaboratorCanBeDeleted(organisation: Organisation, emailAddress: String): Future[Either[ManageCollaboratorResult, Organisation]] = {
    if (organisationHasCollaborator(organisation, emailAddress)) successful(Right(organisation))
    else successful(Left(ValidateCollaboratorFailureResult("Collaborator not found on Organisation")))
  }

  private def collaboratorCanBeAdded(organisation: Organisation, emailAddress: String): Future[Either[ManageCollaboratorResult, Organisation]] = {
    if (organisationHasCollaborator(organisation, emailAddress)) successful(Left(OrganisationAlreadyHasCollaboratorResult()))
    else successful(Right(organisation))
  }

  private def handleGetOrCreateUserId(email: String)(implicit hc: HeaderCarrier): Future[Either[ManageCollaboratorResult, CoreUserDetail]] = {
    thirdPartyDeveloperConnector.getOrCreateUserId(GetOrCreateUserIdRequest(email)).map {
      case Right(x: CoreUserDetail) => Right(x)
      case Left(e: Throwable)       => Left(GetOrCreateUserIdFailedResult(e.getMessage))
    }
  }

  private def handleFindByOrgId(organisationId: OrganisationId): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.findByOrgId(organisationId).map {
      case None                             => Left(GetOrganisationFailedResult(s"Failed to get organisation for Id: ${organisationId.value.toString}"))
      case Some(organisation: Organisation) => Right(organisation)
    }
  }

  private def generateOrganisationId(): OrganisationId = OrganisationId(uuidService.newUuid())
}
