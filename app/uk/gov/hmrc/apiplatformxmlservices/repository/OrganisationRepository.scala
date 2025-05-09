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

package uk.gov.hmrc.apiplatformxmlservices.repository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import com.mongodb.client.model.ReturnDocument
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes._
import org.mongodb.scala.model.Updates.{addToSet, pull, set, setOnInsert}
import org.mongodb.scala.model.{FindOneAndUpdateOptions, _}

import play.api.libs.json.Json
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import uk.gov.hmrc.apiplatformxmlservices.models.OrganisationSortBy._
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.repository.MongoFormatters._
import uk.gov.hmrc.apiplatformxmlservices.util.ApplicationLogger

@Singleton
class OrganisationRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[Organisation](
      collectionName = "organisations",
      mongoComponent = mongo,
      domainFormat = organisationFormats,
      indexes = Seq(
        IndexModel(ascending("organisationId"), IndexOptions().name("organisationId_index").background(true).unique(true)),
        IndexModel(ascending("vendorId"), IndexOptions().name("vendorId_index").background(true).unique(true)),
        IndexModel(ascending("name"), IndexOptions().name("organisationName_index").background(true).unique(false)),
        IndexModel(ascending("collaborators.userId"), IndexOptions().name("collaborators_userId_index").background(true).unique(false))
      ),
      replaceIndexes = true,
      extraCodecs = Seq(
        Codecs.playFormatCodec(OrganisationId.format),
        Codecs.playFormatCodec(VendorId.format),
        Codecs.playFormatCodec(UserId.format)
      )
    )
    with ApplicationLogger {
  override lazy val requiresTtlIndex: Boolean = false

  def findOrgWithMaxVendorId(): Future[Option[Organisation]] = {
    collection
      .find()
      .sort(
        descending("vendorId")
      )
      .limit(1)
      .toFuture().map(_.headOption)
  }

  implicit val caseInsensitiveOrgNameOrdering: Ordering[Organisation] = (x: Organisation, y: Organisation) => x.name.value.compareToIgnoreCase(y.name.value)
  implicit val vendorIdOrdering: Ordering[Organisation]               = (x: Organisation, y: Organisation) => x.vendorId.value.compare(y.vendorId.value)

  def findAll(sortBy: Option[OrganisationSortBy] = None): Future[List[Organisation]] = {

    val sorting = sortBy match {
      case Some(ORGANISATION_NAME) => caseInsensitiveOrgNameOrdering
      case Some(VENDOR_ID)         => vendorIdOrdering
      case _                       => vendorIdOrdering
    }
    collection.find().toFuture()
      .map(_.toList.sorted(sorting))
  }

  def findByOrgId(organisationId: OrganisationId): Future[Option[Organisation]] = {
    collection.find(equal("organisationId", organisationId)).toFuture().map(_.headOption)
  }

  def findByUserId(userId: UserId): Future[List[Organisation]] = {
    collection.find(equal("collaborators.userId", userId))
      .toFuture()
      .map(_.toList.sorted(caseInsensitiveOrgNameOrdering))
  }

  def findByVendorId(vendorId: VendorId): Future[Option[Organisation]] = {
    collection.find(equal("vendorId", vendorId)).toFuture().map(_.headOption)
  }

  def findByOrganisationName(organisationName: OrganisationName): Future[List[Organisation]] = {
    collection.find(regex(fieldName = "name", pattern = organisationName.value, options = "ims"))
      .toFuture()
      .map(_.toList.sorted(caseInsensitiveOrgNameOrdering))
  }

  def addCollaboratorToOrganisation(organisationId: OrganisationId, collaborator: Collaborator): Future[Either[Exception, Organisation]] = {
    collection.findOneAndUpdate(
      equal("organisationId", organisationId),
      addToSet("collaborators", Codecs.toBson(collaborator)),
      FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
    )
      .toFuture()
      .map(x => Right(x))
      .recover {
        case e: Exception =>
          logger.info("addCollaboratorToOrganisation failed:", e)
          Left(new Exception(s"Failed add collaborator to Organisation with organisationId ${organisationId.value} - ${e.getMessage}"))
      }
  }

  def addCollaboratorByVendorId(vendorId: VendorId, collaborator: Collaborator): Future[Either[Exception, Organisation]] = {
    collection.findOneAndUpdate(
      equal("vendorId", Codecs.toBson(vendorId)),
      addToSet("collaborators", Codecs.toBson(collaborator)),
      FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
    )
      .toFuture()
      .map(x => Right(x))
      .recover {
        case e: Exception =>
          logger.info("addCollaboratorByVendorId failed:", e)
          Left(new Exception(s"Failed add collaborator to Organisation with vendorId ${vendorId} - ${e.getMessage}"))
      }
  }

  def removeCollaboratorFromOrganisation(organisationId: OrganisationId, userId: UserId): Future[UpdateOrganisationResult] = {
    collection.findOneAndUpdate(
      equal("organisationId", organisationId),
      pull("collaborators", Codecs.toBson(Json.obj("userId" -> userId))),
      FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    )
      .toFutureOption()
      .map {
        case Some(organisation: Organisation) => UpdateOrganisationSuccessResult(organisation)
        case _                                => UpdateOrganisationFailedResult()
      }
  }

  def createOrUpdate(organisation: Organisation): Future[Either[Exception, Organisation]] = {
    val query = and(equal("organisationId", Codecs.toBson(organisation.organisationId)), equal("vendorId", Codecs.toBson(organisation.vendorId)))

    val setOnInsertOperations = List(
      setOnInsert("organisationId", organisation.organisationId),
      setOnInsert("vendorId", organisation.vendorId)
    )

    val setOnUpdate = List(
      set("name", organisation.name.value.trim),
      set("collaborators", Codecs.toBson(organisation.collaborators)),
      set("services", Codecs.toBson(organisation.services))
    )

    val allOps = setOnInsertOperations ++ setOnUpdate

    collection.findOneAndUpdate(
      filter = query,
      update = Updates.combine(allOps: _*),
      options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).toFuture()
      .map(x => Right(x))
      .recover {
        case e: Exception =>
          logger.info("createOrUpdate failed:", e)
          Left(new Exception(s"Failed to create or update Organisation with name ${organisation.name.value} - ${e.getMessage}"))
      }
  }

  def updateOrganisationDetails(organisationId: OrganisationId, organisationName: OrganisationName): Future[UpdateOrganisationResult] = {
    val query = equal("organisationId", organisationId)
    collection.findOneAndUpdate(query, set("name", organisationName.value), options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)).toFutureOption()
      .map {
        case Some(organisation: Organisation) => UpdateOrganisationSuccessResult(organisation)
        case _                                => UpdateOrganisationFailedResult()
      }.recover {
        case e: Exception =>
          logger.info("UpdateOrganisationFailed:", e)
          UpdateOrganisationFailedResult()
      }

  }

  def deleteByOrgId(organisationId: OrganisationId): Future[Boolean] = {
    collection.deleteOne(equal("organisationId", organisationId)).toFuture()
      .map(x => x.getDeletedCount == 1)
  }
}
