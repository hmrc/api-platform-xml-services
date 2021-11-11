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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{Assertion, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.apiplatformxmlservices.models.Organisation
import uk.gov.hmrc.apiplatformxmlservices.support.{AwaitTestSupport, MongoApp}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.apiplatformxmlservices.models.VendorId
import uk.gov.hmrc.apiplatformxmlservices.models.OrganisationId
import java.util.UUID
import org.scalatest.matchers.should.Matchers

class OrganisationRepositoryISpec
    extends AnyWordSpec
    with MongoApp[Organisation]
    with GuiceOneAppPerSuite
    with ScalaFutures
    with BeforeAndAfterEach
    with AwaitTestSupport
    with Matchers {

  override protected def repository: PlayMongoRepository[Organisation] = app.injector.instanceOf[OrganisationRepository]
  val indexNameToDrop = "please_delete_me__let_me_go"

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "mongodb.oldIndexesToDrop" -> Seq(indexNameToDrop, "text_index_1_0")
      )

  override implicit lazy val app: Application = appBuilder.build()

  def repo: OrganisationRepository = app.injector.instanceOf[OrganisationRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  trait Setup {
    def getUuid() = UUID.randomUUID()
  }

  "create Organisation" should {
    "return a Right" in new Setup {
      val organisationToPersist = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(20001), name = "Organisation Name")
      val result = await(repo.create(organisationToPersist))

      result match {
        case Left(e: Exception) => fail
        case Right(x: Boolean)  => x shouldBe true
      }
    }

    "return a Left" in new Setup {
      val organisationToPersist = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(20001), name = "Organisation Name")
      await(repo.create(organisationToPersist))
      val result = await(repo.create(organisationToPersist))

      result match {
        case Left(e: Exception) => succeed
        case Right(_)           => fail
      }
    }
  }
}
