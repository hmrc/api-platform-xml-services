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

  def addCollaborator(organisationId: OrganisationId, getOrCreateUserIdRequest: GetOrCreateUserIdRequest)(implicit hc: HeaderCarrier) {
    thirdPartyDeveloperConnector.getOrCreateUserId(getOrCreateUserIdRequest) match {
      case Left(e) => Left(e)
      case Right(userDetails: CoreUserDetail) =>
    }

    for {
      organisation <- organisationRepository.findByOrgId(organisationId)
      modifiedOrganisation = addCollaboratorToOrg(eitherThrowableOrUserDetails, organisation)
      updatedOrganisation <- organisationRepository.update(modifiedOrganisation)

    } yield updatedOrganisation

    // def addCollaboratorToOrg(eitherThrowableOrUserDetails: Either[Throwable, CoreUserDetail], organisation: Option[Organisation]): Organisation = {

    //   eitherThrowableOrUserDetails match {
    //     case Left
    //   }

    // // organisation.map(org => org.copy(collaborators = org.collaborators ++ Collaborator(userDetails.userId)))
    // }
  }


private def handleFindByOrgId(organisationId: OrganisationId): Future[Either[AddCollaboratorResult, CoreUserDetail]] = {

}

  private def getOrCreateUserId(getOrCreateUserIdRequest: GetOrCreateUserIdRequest)(implicit hc: HeaderCarrier): Future[Either[AddCollaboratorResult, CoreUserDetail]] = {
    thirdPartyDeveloperConnector.getOrCreateUserId(getOrCreateUserIdRequest) match {
      case Right(x: CoreUserDetail) => Right(x)
      case Left(e: Throwable) => Left(GetOrCreateUserIdFailedResult(e.message))   
        }
  }

  def update(organisation: Organisation): Future[Either[Exception, Boolean]] =
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


  sealed trait AddCollaboratorResult

  case class GetOrCreateUserIdFailedResult(message: String) extends AddCollaboratorResult

}
