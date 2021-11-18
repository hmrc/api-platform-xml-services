/*
 * Copyright 2021 HM Revenue & Customs
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

import com.mongodb.client.model.Projections.{excludeId, fields, include}
import com.mongodb.client.model.ReturnDocument
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes._
import org.mongodb.scala.model.Updates.{set, setOnInsert}
import org.mongodb.scala.model._
import uk.gov.hmrc.apiplatformxmlservices.models.{Organisation, OrganisationId, VendorId}
import uk.gov.hmrc.apiplatformxmlservices.repository.MongoFormatters._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrganisationRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Organisation](
    collectionName = "organisations",
    mongoComponent = mongo,
    domainFormat = organisationFormats,
    indexes = Seq(
      IndexModel(ascending("organisationId"), IndexOptions().name("organisationId_index").background(true).unique(true)),
      IndexModel(ascending("vendorId"), IndexOptions().name("vendorId_index").background(true).unique(true))
    ),
    replaceIndexes = false
  ) {

  def findOrgWithMaxVendorId(): Future[Option[Organisation]] = {
    collection
      .find()
      .sort(
        descending("vendorId")
      )
      .limit(1)
      .toFuture().map(_.headOption)
  }

  def findByOrgId(organisationId: OrganisationId): Future[Option[Organisation]] = {
    collection.find(equal("organisationId", Codecs.toBson(organisationId))).toFuture().map(_.headOption)

  }

  def findByVendorId(vendorId: VendorId): Future[Option[Organisation]] = {
    collection.find(equal("vendorId", Codecs.toBson(vendorId))).toFuture().map(_.headOption)

  }

  def createOrUpdate(organisation: Organisation): Future[Either[Exception, Organisation]] = {
    val query = equal("organisationId", Codecs.toBson(organisation.organisationId))

    val setOnInsertOperations = List(
      setOnInsert("organisationId", Codecs.toBson(organisation.organisationId)),
      setOnInsert("vendorId", Codecs.toBson(organisation.vendorId))
    )

    val setOnUpdate = List(set("name", organisation.name))

    val allOps = setOnInsertOperations ++ setOnUpdate

    collection.findOneAndUpdate(
      filter = query,
      update = Updates.combine(allOps: _*),
      options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).toFuture
      .map(x => Right(x))
      .recover {
        case e: Exception => Left(new Exception(s"Failed to create Organisation with name ${organisation.name} - ${e.getMessage}"))
      }
  }

  def update(organisation: Organisation): Future[Boolean] = {
    val filter = equal("organisationId", Codecs.toBson(organisation.organisationId))

    collection.findOneAndReplace(filter, organisation, FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER)).toFutureOption()
      .map {
        case Some(_) => true
        case None => false
      }
  }

  def deleteByOrgId(organisationId: OrganisationId): Future[Boolean] = {
    collection.deleteOne(equal("organisationId", Codecs.toBson(organisationId))).toFuture()
      .map(x => x.getDeletedCount == 1)
  }

  def create(organisation: Organisation): Future[Either[Exception, Boolean]] = {
    collection.insertOne(organisation).toFuture
      .map(x => Right(x.wasAcknowledged()))
      .recover {
        case e: Exception => Left(new Exception(s"Failed to create Organisation with name ${organisation.name} - ${e.getMessage}"))
      }
  }

}
