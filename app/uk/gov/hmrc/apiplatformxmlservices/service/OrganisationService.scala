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
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class OrganisationService @Inject() (
    organisationRepository: OrganisationRepository,
    uuidService: UuidService,
    vendorIdService: VendorIdService,
    thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext) {

  def create(organisationName: OrganisationName): Future[Either[Exception, Organisation]] = {

    def createOrganisation(organisationName: OrganisationName, vendorId: VendorId): Future[Either[Exception, Organisation]] = {
      organisationRepository.createOrUpdate(
        Organisation(
          organisationId = getOrganisationId,
          name = organisationName,
          vendorId = vendorId
        )
      )
    }

    vendorIdService.getNextVendorId flatMap {
      case Some(vendorId: VendorId) => createOrganisation(organisationName, vendorId)
      case _                        => Future.successful(Left(new Exception("Could not get max vendorId")))
    }

  }

  def removeCollaborator(organisationId: OrganisationId, gatekeeperUserId: Option[String], emailAddress: String)(implicit hc: HeaderCarrier): Future[Either[ManageCollaboratorResult, Organisation]] = {
    (for {
      organisation <- EitherT(handleFindByOrgId(organisationId))
      hasCollaborator <- EitherT(validateCollaborator(organisation, emailAddress))
      updatedOrganisation <- EitherT(handleUpdateOrganisation(organisation))

    } yield updatedOrganisation).value
  }

  private def validateCollaborator(organisation: Organisation, emailAddress: String): Future[Either[ManageCollaboratorResult, Organisation]] = {
    val x = organisation.collaborators.filter(_.email.equalsIgnoreCase(emailAddress)).nonEmpty
    x match {
      case true => Future.successful(Right(organisation))
      case false => Future.successful(Left(ValidateCollaboratorFailureResult("Collaborator not found on Organisation")))
    }
    
  }

  def addCollaborator(organisationId: OrganisationId, email: String)(implicit hc: HeaderCarrier): Future[Either[ManageCollaboratorResult, Organisation]] = {
    (for {
      organisation <- EitherT(handleFindByOrgId(organisationId))
      coreUserDetail <- EitherT(handleGetOrCreateUserId(email))
      modifiedOrganisation <- EitherT(handleAddCollaboratorToOrg(coreUserDetail, organisation))
      updatedOrganisation <- EitherT(handleUpdateOrganisation(modifiedOrganisation))

    } yield updatedOrganisation).value
  }

  private def handleUpdateOrganisation(organisation: Organisation): Future[Either[ManageCollaboratorResult, Organisation]] = {
    organisationRepository.update(organisation).map {
      case Left(value)              => Left(UpdateOrganisationFailedResult(value.getMessage))
      case Right(org: Organisation) => Right(org)
    }
  }

  private def handleAddCollaboratorToOrg(coreUserDetail: CoreUserDetail, organisation: Organisation): Future[Either[ManageCollaboratorResult, Organisation]] = {
    Future.successful(Right(organisation.copy(collaborators = organisation.collaborators :+ Collaborator(coreUserDetail.userId, coreUserDetail.email))))
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

  private def handleDeleteUser(gatekeeperUserId: Option[String], email: String)(implicit hc: HeaderCarrier): Future[Either[ManageCollaboratorResult, Boolean]] = {
    thirdPartyDeveloperConnector.deleteUser(DeleteUserRequest(gatekeeperUserId, email)).map {
      case DeleteUserSuccessResult => Right(true)
      case DeleteUserFailureResult => Left(DeleteCollaboratorFailureResult("Failed to delete user"))
    }
  }

  def getOrCreateUserId(getOrCreateUserIdRequest: GetOrCreateUserIdRequest)(implicit hc: HeaderCarrier) = {
    thirdPartyDeveloperConnector.getOrCreateUserId(getOrCreateUserIdRequest)
  }

  def deleteUser(deleteUserRequest: DeleteUserRequest)(implicit hc: HeaderCarrier): Future[DeleteUserResult] = {
    thirdPartyDeveloperConnector.deleteUser(deleteUserRequest)
  }

  def update(organisation: Organisation): Future[Either[Exception, Organisation]] =
    organisationRepository.update(organisation)

  def deleteByOrgId(organisationId: OrganisationId): Future[Boolean] =
    organisationRepository.deleteByOrgId(organisationId)

  def findByOrgId(organisationId: OrganisationId): Future[Option[Organisation]] =
    organisationRepository.findByOrgId(organisationId)

  def findByVendorId(vendorId: VendorId): Future[Option[Organisation]] =
    organisationRepository.findByVendorId(vendorId)

  def findByOrganisationName(organisationName: OrganisationName): Future[List[Organisation]] =
    organisationRepository.findByOrganisationName(organisationName)

  def findAll(): Future[List[Organisation]] =
    organisationRepository.findAll

  private def getOrganisationId(): OrganisationId = OrganisationId(uuidService.newUuid())
}
