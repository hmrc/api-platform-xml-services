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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.apiplatformxmlservices.models.{Organisation, OrganisationId, VendorId}
import uk.gov.hmrc.apiplatformxmlservices.support.{AwaitTestSupport, MongoApp}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.UUID

class OrganisationRepositoryISpec
    extends AnyWordSpec
    with MongoApp[Organisation]
    with GuiceOneAppPerSuite
    with ScalaFutures
    with BeforeAndAfterEach
    with AwaitTestSupport
    with Matchers {

  override protected def repository: PlayMongoRepository[Organisation] = app.injector.instanceOf[OrganisationRepository]

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure("mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}")

  override implicit lazy val app: Application = appBuilder.build()

  def repo: OrganisationRepository = app.injector.instanceOf[OrganisationRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  trait Setup {
    def getUuid() = UUID.randomUUID()
    val organisationToPersist = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(20001), name = "Organisation Name")
  }

  "findByOrgId" should {
    "return an Organisation when organisationId exists" in new Setup {
      await(repo.create(organisationToPersist))

      val result = await(repo.findByOrgId(organisationToPersist.organisationId))
      result shouldBe Some(organisationToPersist)

    }

    "return None when organisationId does not exist" in new Setup {
      val result = await(repo.findByOrgId(OrganisationId(getUuid)))
      result shouldBe None

    }
  }

  "findByVendorId" should {
    "return an Organisation when vendorId exists" in new Setup {
      await(repo.create(organisationToPersist))

      val result = await(repo.findByVendorId(organisationToPersist.vendorId))
      result shouldBe Some(organisationToPersist)

    }

    "return None when vendorId does not exist" in new Setup {
      val result = await(repo.findByVendorId(VendorId(1234)))
      result shouldBe None

    }
  }

  "deleteByOrgId" should {
    "return true when organisation to be deleted exists" in new Setup {
        
      await(repo.create(organisationToPersist))

      val result = await(repo.deleteByOrgId(organisationToPersist.organisationId))
      result shouldBe true
    }

    "return false when organisation to be deleted doesn't exist" in new Setup {
        
      val result = await(repo.deleteByOrgId(organisationToPersist.organisationId))
      result shouldBe false
    }
  }

  "update" should {
    "return an Organisation when update successful" in new Setup {
      await(repo.create(organisationToPersist))
      val updatedOrganisation = organisationToPersist.copy(name = "New organisation name")

      val result = await(repo.update(updatedOrganisation))
      result shouldBe true

    }

    "return false when organisation does not exist" in new Setup {
      val result = await(repo.update(organisationToPersist))
      result shouldBe false

    }
  }

  "create Organisation" should {
    "return a Right if organisation doesn't already exist" in new Setup {
      val result = await(repo.create(organisationToPersist))

      result match {
        case Left(e: Exception) => fail
        case Right(x: Boolean)  => x shouldBe true
      }
    }

    "return a Left when try to create and organisation with the same id" in new Setup {
      await(repo.create(organisationToPersist))
      val result = await(repo.create(organisationToPersist))

      result match {
        case Left(e: Exception) => succeed
        case Right(_)           => fail
      }
    }
  }
}
