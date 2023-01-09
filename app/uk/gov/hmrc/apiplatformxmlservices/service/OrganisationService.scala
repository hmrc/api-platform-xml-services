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

import com.mongodb.MongoCommandException
import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.models.collaborators.GetOrCreateUserFailedResult
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.UserResponse
import uk.gov.hmrc.apiplatformxmlservices.repository.OrganisationRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class OrganisationService @Inject() (
    organisationRepository: OrganisationRepository,
    uuidService: UuidService,
    vendorIdService: VendorIdService,
    override val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    override val xmlApiService: XmlApiService
  )(implicit val ec: ExecutionContext) extends UserFunctions {

  def findAndCreateOrUpdate(organisationName: OrganisationName, vendorId: VendorId): Future[Either[Exception, Organisation]] = {
    organisationRepository.findByVendorId(vendorId) flatMap {
      case Some(organisation) => organisationRepository.createOrUpdate(organisation.copy(name = organisationName))
      case None               => createOrganisation(organisationName, vendorId)
    }
  }

  def create(request: CreateOrganisationRequest)(implicit hc: HeaderCarrier): Future[CreateOrganisationResult] = {
    vendorIdService.getNextVendorId().flatMap {
      case Right(vendorId: VendorId) => handleGetOrCreateUser(request.email, request.firstName, request.lastName).flatMap {
          case Right(user: UserResponse)            =>
            handleCreateOrganisation(request.organisationName, vendorId, List(Collaborator(user.userId, request.email)))
          case Left(e: GetOrCreateUserFailedResult) => successful(CreateOrganisationFailedResult(e.message))
        }
      case Left(e: Throwable)        => successful(CreateOrganisationFailedResult(e.getMessage))
     
    }.recover {
      case NonFatal(e: Throwable) => CreateOrganisationFailedResult(e.getMessage)
    }
  }

  def handleCreateOrganisation(organisationName: OrganisationName,
                               vendorId: VendorId,
                               collaborators: List[Collaborator] = List.empty): Future[CreateOrganisationResult] = {

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

  def findByUserId(userId: UserId): Future[List[Organisation]] =
    organisationRepository.findByUserId(userId)

  def findByOrganisationName(organisationName: OrganisationName): Future[List[Organisation]] =
    organisationRepository.findByOrganisationName(organisationName)

  def findAll(sortBy: Option[OrganisationSortBy] = None): Future[List[Organisation]] = organisationRepository.findAll(sortBy)


  private def createOrganisation(organisationName: OrganisationName, vendorId: VendorId): Future[Either[Exception, Organisation]] = {
    organisationRepository.createOrUpdate(
      Organisation(
        organisationId = generateOrganisationId(),
        name = organisationName,
        vendorId = vendorId
      )
    )
  }


  private def generateOrganisationId(): OrganisationId = OrganisationId(uuidService.newUuid())
}
