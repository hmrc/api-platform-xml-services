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

import uk.gov.hmrc.mongo.MongoComponent
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apiplatformxmlservices.models.{OrganisationId, Organisation}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.apiplatformxmlservices.repository.MongoFormatters._
import org.mongodb.scala.model.Indexes._
import org.mongodb.scala.model._

@Singleton
class OrganisationRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
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

//  def findById(organisationId: OrganisationId): Future[Option[Organisation]] = {
//    collection.find(equal("organisationId", Codecs.toBson(organisationId))).toFuture().map(_.headOption)
//      .recover {
//        case e: Exception =>
//          None
//      }
//  }
  
  def create(organisation: Organisation) = {
      collection.insertOne(organisation)
        .map(x => Right(x))
        .recover{
          case e: Exception => Left(new Exception(s"Failed to create Organisation with name ${organisation.name} - ${e.getMessage}"))
        }
  }
}
