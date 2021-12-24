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
import uk.gov.hmrc.apiplatformxmlservices.models.{Organisation, OrganisationId, OrganisationName, VendorId}
import uk.gov.hmrc.apiplatformxmlservices.support.{AwaitTestSupport, MongoApp}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.UUID
import java.util.UUID.randomUUID

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
  implicit val caseInsensitive: Ordering[Organisation] = (x: Organisation, y: Organisation) => x.name.value.compareToIgnoreCase(y.name.value)

  def repo: OrganisationRepository = app.injector.instanceOf[OrganisationRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  trait Setup {
    def getUuid() = UUID.randomUUID()
    val organisationToPersist = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(9000), name = OrganisationName("Organisation Name"))
    val organisationToPersist2 = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(9001), name = OrganisationName("Organisation Name 2"))
    val org3 = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(9003), name = OrganisationName("ABC DEF GHI"))
    val org4 = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(9004), name = OrganisationName("DEF GHI"))
    val org5 = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(9005), name = OrganisationName("GHUIUIU"))
    val org6 = Organisation(organisationId = OrganisationId(getUuid), vendorId = VendorId(9006), name = OrganisationName("GPYGFRTDE"))

    def createOrganisations() = {
      await(repo.create(organisationToPersist))
      await(repo.create(organisationToPersist2))
      await(repo.create(org3))
      await(repo.create(org4))
      await(repo.create(org5))
      await(repo.create(org6))
    }

    def createUnsortedListOfOrganisations(): List[Organisation] = {
      val orgOne = Organisation(OrganisationId(randomUUID()), VendorId(1111L), OrganisationName("1 Trading"))
      val orgTwo = Organisation(OrganisationId(randomUUID()), VendorId(1112L), OrganisationName("Global Trans Inc"))
      val orgThree = Organisation(OrganisationId(randomUUID()), VendorId(1113L), OrganisationName("23rd Street Inc"))
      val orgFour = Organisation(OrganisationId(randomUUID()), VendorId(1114L), OrganisationName("! Exclamation Trading"))
      val orgFive = Organisation(OrganisationId(randomUUID()), VendorId(1115L), OrganisationName("$ Dollar and Co"))
      val orgSix = Organisation(OrganisationId(randomUUID()), VendorId(1116L), OrganisationName("Zone 5 Corp"))
      val orgSeven = Organisation(OrganisationId(randomUUID()), VendorId(1117L), OrganisationName("Criterion Games"))
      val orgEight = Organisation(OrganisationId(randomUUID()), VendorId(1118L), OrganisationName("abcl corp"))
      val orgNine = Organisation(OrganisationId(randomUUID()), VendorId(1119L), OrganisationName("exile dog inc"))
      val orgTen = Organisation(OrganisationId(randomUUID()), VendorId(1120L), OrganisationName("yo yachets"))

      await(repo.create(orgOne))
      await(repo.create(orgTwo))
      await(repo.create(orgThree))
      await(repo.create(orgFour))
      await(repo.create(orgFive))
      await(repo.create(orgSix))
      await(repo.create(orgSeven))
      await(repo.create(orgEight))
      await(repo.create(orgNine))
      await(repo.create(orgTen))

      List(orgOne, orgTwo, orgThree, orgFour, orgFive, orgSix, orgSeven, orgEight, orgNine, orgTen)
    }
  }

  "findAll" should {
    "return a List of all Organisations sorted in order Special Chars -> Numerics -> Strings" in new Setup {
      val expectedResult: List[Organisation] = createUnsortedListOfOrganisations().sorted(caseInsensitive)
      val actualResult: List[Organisation] = await(repo.findAll)

      actualResult shouldBe expectedResult
    }

    "return an empty List when no organisations exist" in new Setup {
      val result = await(repo.findAll)

      result shouldBe List.empty
    }
  }

  "findOrgWithMaxVendorId" should {
    "return Organisation with max vendorId when there are 2 organisations" in new Setup {
      await(repo.create(organisationToPersist))
      await(repo.create(organisationToPersist2))

      val result = await(repo.findOrgWithMaxVendorId)

      result shouldBe Some(organisationToPersist2)
    }

    "return None no Organisations exist" in new Setup {
      val result = await(repo.findOrgWithMaxVendorId)

      result shouldBe None
    }
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

  "findByOrganisationName" should {

    "return all matching organisation" in new Setup {
      createOrganisations()

      val results: List[Organisation] = await(repo.findByOrganisationName(OrganisationName("DEF")))

      results should contain allOf (org3, org4)
    }

    "return no organisations when there are no matches" in new Setup {
      createOrganisations()

      val results: List[Organisation] = await(repo.findByOrganisationName(OrganisationName("UNKNOWN")))

      results.isEmpty shouldBe true
    }

    "return organisations sorted in order Special Chars -> Numerics -> Strings" in new Setup {
      val expectedResult: List[Organisation] = createUnsortedListOfOrganisations().sorted(caseInsensitive)
      val actualResult: List[Organisation] = await(repo.findByOrganisationName(OrganisationName("")))

      actualResult shouldBe expectedResult
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
    "return true when update successful" in new Setup {
      await(repo.create(organisationToPersist))
      val updatedOrganisation = organisationToPersist.copy(name = OrganisationName("New organisation name"))

      await(repo.update(updatedOrganisation)) match {
        case Right(x: Boolean) => x shouldBe true
        case _                 => fail
      }
    }

    "return false when organisation does not exist" in new Setup {
      await(repo.update(organisationToPersist)) match {
        case Right(x: Boolean) => x shouldBe false
        case _                 => fail
      }
    }

    "return Left when try to update with another organisation's VendorId" in new Setup {
      await(repo.create(organisationToPersist))
      await(repo.create(organisationToPersist2))
      val updatedOrganisation = organisationToPersist.copy(name = OrganisationName("New organisation name"), vendorId = organisationToPersist2.vendorId)

      await(repo.update(updatedOrganisation)) match {
        case Left(e: Exception) => e.getMessage contains "duplicate key"
        case _                  => fail
      }
    }

  }

  "createOrUpdate" should {
    "return Organisation when create successful" in new Setup {
      await(repo.createOrUpdate(organisationToPersist)) match {
        case Right(organisation: Organisation) => organisation shouldBe organisationToPersist
        case _                                 => fail
      }
    }

    "return Left when vendor Id already exists" in new Setup {
      await(repo.create(organisationToPersist))

      val updatedOrganisation = organisationToPersist.copy(name = OrganisationName("New organisation name"), organisationId = OrganisationId(getUuid()), vendorId = VendorId(9000))

      await(repo.createOrUpdate(updatedOrganisation)) match {
        case Right(_)           => fail
        case Left(e: Exception) => e.getMessage contains "E11000 duplicate key error"
      }
    }

    "return Right and update organisation name if Organisation Id already exists but vendor Id does not" in new Setup {
      await(repo.create(organisationToPersist))

      val updatedOrganisation = organisationToPersist.copy(name = OrganisationName("New organisation name"), vendorId = VendorId(9001))

      await(repo.createOrUpdate(updatedOrganisation)) match {
        case Right(persistedOrg: Organisation) => {
          persistedOrg.name shouldBe updatedOrganisation.name
          persistedOrg.organisationId shouldBe organisationToPersist.organisationId
          persistedOrg.vendorId shouldBe organisationToPersist.vendorId
        }
        case Left(_)                           => fail
      }
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

    "return a Left when try to create an organisation with an existing id" in new Setup {
      await(repo.create(organisationToPersist))
      val result = await(repo.create(organisationToPersist))

      result match {
        case Left(e: Exception) => succeed
        case Right(_)           => fail
      }
    }
  }
}
