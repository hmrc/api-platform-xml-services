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

import java.util.UUID

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import uk.gov.hmrc.apiplatformxmlservices.common.data.CommonTestData
import uk.gov.hmrc.apiplatformxmlservices.models._
import uk.gov.hmrc.apiplatformxmlservices.support.MongoApp

class OrganisationRepositoryISpec
    extends AnyWordSpec
    with MongoApp[Organisation]
    with GuiceOneAppPerSuite
    with ScalaFutures
    with BeforeAndAfterEach
    with DefaultAwaitTimeout
    with FutureAwaits
    with Matchers {

  override protected def repository: PlayMongoRepository[Organisation] = app.injector.instanceOf[OrganisationRepository]

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure("mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}")

  override implicit lazy val app: Application         = appBuilder.build()
  val caseInsensitiveOrdering: Ordering[Organisation] = (x: Organisation, y: Organisation) => x.name.value.compareToIgnoreCase(y.name.value)
  val vendorIdOrdering: Ordering[Organisation]        = (x: Organisation, y: Organisation) => x.vendorId.value.compareTo(y.vendorId.value)

  def repo: OrganisationRepository = app.injector.instanceOf[OrganisationRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  trait Setup extends CommonTestData {

    val collaboratorTwo   = aCollaborator.copy(UserId.random)
    val collaboratorThree = aCollaborator.copy(UserId.random)

    val organisationWithTrimmedName = anOrganisation.copy(name = OrganisationName(anOrganisation.name.value.trim))
    val anOrganisation2             = Organisation(OrganisationId.random, vendorId = VendorId(9001), name = OrganisationName("Organisation Name 2"))

    val org3: Organisation = Organisation(
      OrganisationId.random,
      vendorId = VendorId(9003),
      name = OrganisationName("ABC DEF GHI"),
      collaborators = List(aCollaborator, collaboratorTwo)
    )

    val org4 = Organisation(organisationId = OrganisationId.random, vendorId = VendorId(9004), name = OrganisationName("DEF GHI"), collaborators = List(collaboratorThree))

    val org5 =
      Organisation(organisationId = OrganisationId.random, vendorId = VendorId(9005), name = OrganisationName("GHUIUIU"), collaborators = List(aCollaborator, collaboratorTwo))
    val org6 = Organisation(organisationId = OrganisationId.random, vendorId = VendorId(9006), name = OrganisationName("GPYGFRTDE"))

    def createOrganisations() = {
      await(repo.createOrUpdate(anOrganisation))
      await(repo.createOrUpdate(anOrganisation2))
      await(repo.createOrUpdate(org3))
      await(repo.createOrUpdate(org4))
      await(repo.createOrUpdate(org5))
      await(repo.createOrUpdate(org6))
    }

    def createUnsortedListOfOrganisations(): List[Organisation] = {
      val orgOne   = Organisation(OrganisationId.random, VendorId(1111L), OrganisationName("1 Trading"))
      val orgTwo   = Organisation(OrganisationId.random, VendorId(1112L), OrganisationName("Global Trans Inc"))
      val orgThree = Organisation(OrganisationId.random, VendorId(1113L), OrganisationName("23rd Street Inc"))
      val orgFour  = Organisation(OrganisationId.random, VendorId(1114L), OrganisationName("! Exclamation Trading"))
      val orgFive  = Organisation(OrganisationId.random, VendorId(1115L), OrganisationName("$ Dollar and Co"))
      val orgSix   = Organisation(OrganisationId.random, VendorId(1116L), OrganisationName("Zone 5 Corp"))
      val orgSeven = Organisation(OrganisationId.random, VendorId(1117L), OrganisationName("Criterion Games"))
      val orgEight = Organisation(OrganisationId.random, VendorId(1118L), OrganisationName("abcl corp"))
      val orgNine  = Organisation(OrganisationId.random, VendorId(1119L), OrganisationName("exile dog inc"))
      val orgTen   = Organisation(OrganisationId.random, VendorId(1120L), OrganisationName("yo yachets"))

      await(repo.createOrUpdate(orgOne))
      await(repo.createOrUpdate(orgTwo))
      await(repo.createOrUpdate(orgThree))
      await(repo.createOrUpdate(orgFour))
      await(repo.createOrUpdate(orgFive))
      await(repo.createOrUpdate(orgSix))
      await(repo.createOrUpdate(orgSeven))
      await(repo.createOrUpdate(orgEight))
      await(repo.createOrUpdate(orgNine))
      await(repo.createOrUpdate(orgTen))

      List(orgOne, orgTwo, orgThree, orgFour, orgFive, orgSix, orgSeven, orgEight, orgNine, orgTen)
    }
  }

  "findAll" should {
    "return a List of all Organisations sorted in order Special Chars -> Numerics -> Strings. Sorted by vendorId by default" in new Setup {
      val expectedResult = createUnsortedListOfOrganisations().sorted(vendorIdOrdering)
      val actualResult   = await(repo.findAll(None))

      actualResult shouldBe expectedResult
    }

    "return a List of all Organisations sorted in order Special Chars -> Numerics -> Strings. Sorted by orgname by when requested" in new Setup {
      val expectedResult = createUnsortedListOfOrganisations().sorted(caseInsensitiveOrdering)
      val actualResult   = await(repo.findAll(Some(OrganisationSortBy.ORGANISATION_NAME)))

      actualResult shouldBe expectedResult
    }

    "return a List of all Organisations sorted in order Special Chars -> Numerics -> Strings. Sorted by vendorId when requested" in new Setup {
      val expectedResult = createUnsortedListOfOrganisations().sorted(vendorIdOrdering)
      val actualResult   = await(repo.findAll(Some(OrganisationSortBy.VENDOR_ID)))

      actualResult shouldBe expectedResult
    }

    "return an empty List when no organisations exist" in new Setup {
      val result = await(repo.findAll(Some(OrganisationSortBy.ORGANISATION_NAME)))

      result shouldBe List.empty
    }
  }

  "findOrgWithMaxVendorId" should {
    "return Organisation with max vendorId when there are 2 organisations" in new Setup {
      await(repo.createOrUpdate(anOrganisation))
      await(repo.createOrUpdate(anOrganisation2))

      val result = await(repo.findOrgWithMaxVendorId())

      result shouldBe Some(anOrganisation2)
    }

    "return None no Organisations exist" in new Setup {
      val result = await(repo.findOrgWithMaxVendorId())

      result shouldBe None
    }
  }

  "findByOrgId" should {
    "return an Organisation when organisationId exists" in new Setup {
      await(repo.createOrUpdate(anOrganisation))

      val result = await(repo.findByOrgId(anOrganisation.organisationId))

      result shouldBe Some(organisationWithTrimmedName)
    }

    "return None when organisationId does not exist" in new Setup {
      val result = await(repo.findByOrgId(OrganisationId.random))

      result shouldBe None
    }
  }

  "findByVendorId" should {
    "return an Organisation when vendorId exists" in new Setup {
      await(repo.createOrUpdate(anOrganisation))

      val result = await(repo.findByVendorId(anOrganisation.vendorId))

      result shouldBe Some(organisationWithTrimmedName)
    }

    "return None when vendorId does not exist" in new Setup {
      val result = await(repo.findByVendorId(VendorId(1234)))

      result shouldBe None
    }
  }

  "findByUserId" should {

    "return all matching organisations" in new Setup {
      createOrganisations()

      val results: List[Organisation] = await(repo.findByUserId(aCollaborator.userId))

      results should contain only (List(org3, org5): _*)
    }

    "return no organisations for non-existent userId" in new Setup {
      createOrganisations()

      val results: List[Organisation] = await(repo.findByUserId(UserId.random))

      results shouldBe List.empty
    }
  }

  "findByOrganisationName" should {

    "return all matching organisation" in new Setup {
      createOrganisations()

      val results = await(repo.findByOrganisationName(OrganisationName("DEF")))

      results should contain allOf (org3, org4)
    }

    "return no organisations when there are no matches" in new Setup {
      createOrganisations()

      val results = await(repo.findByOrganisationName(OrganisationName("UNKNOWN")))

      results.isEmpty shouldBe true
    }

    "return organisations sorted in order Special Chars -> Numerics -> Strings" in new Setup {
      val expectedResult = createUnsortedListOfOrganisations().sorted(caseInsensitiveOrdering)
      val actualResult   = await(repo.findByOrganisationName(OrganisationName("")))

      actualResult shouldBe expectedResult
    }
  }

  "deleteByOrgId" should {
    "return true when organisation to be deleted exists" in new Setup {
      await(repo.createOrUpdate(anOrganisation))

      val result = await(repo.deleteByOrgId(anOrganisation.organisationId))

      result shouldBe true
    }

    "return false when organisation to be deleted doesn't exist" in new Setup {
      val result = await(repo.deleteByOrgId(anOrganisation.organisationId))

      result shouldBe false
    }
  }

  "addCollaboratorByVendorId" should {
    "return Right(true) when collaborator successfully added to Organisation with no existing Collaborators" in new Setup {
      await(repo.createOrUpdate(anOrganisation))
      await(repo.addCollaboratorByVendorId(anOrganisation.vendorId, aCollaborator)) match {
        case Right(organisation: Organisation) => organisation.organisationId shouldBe anOrganisation.organisationId
        case _                                 => fail()
      }
    }

    "return Right(false) when try to add collaborator to an Organisation on which it already exists" in new Setup {
      await(repo.createOrUpdate(anOrganisation.copy(collaborators = List(aCollaborator))))

      await(repo.addCollaboratorByVendorId(anOrganisation.vendorId, aCollaborator)) match {
        case Right(organisation: Organisation) => organisation.organisationId shouldBe anOrganisation.organisationId
        case _                                 => fail()
      }
    }

    "return Right(false) when same collaborator added to two different Organisations" in new Setup {
      await(repo.createOrUpdate(anOrganisation.copy(collaborators = List(aCollaborator))))
      await(repo.createOrUpdate(anOrganisation2.copy(collaborators = List(aCollaborator))))

      await(repo.addCollaboratorByVendorId(anOrganisation.vendorId, aCollaborator)) match {
        case Right(organisation: Organisation) => organisation.organisationId shouldBe anOrganisation.organisationId
        case _                                 => fail()
      }
    }

  }

  "addCollaboratorToOrganisation" should {
    "return Right(true) when collaborator successfully added to Organisation with no existing Collaborators" in new Setup {
      await(repo.createOrUpdate(anOrganisation))
      await(repo.addCollaboratorToOrganisation(anOrganisation.organisationId, aCollaborator)) match {
        case Right(organisation: Organisation) => organisation.organisationId shouldBe anOrganisation.organisationId
        case _                                 => fail()
      }
    }

    "return Right(false) when try to add collaborator to an Organisation on which it already exists" in new Setup {
      await(repo.createOrUpdate(anOrganisation.copy(collaborators = List(aCollaborator))))

      await(repo.addCollaboratorToOrganisation(anOrganisation.organisationId, aCollaborator)) match {
        case Right(organisation: Organisation) => organisation.organisationId shouldBe anOrganisation.organisationId
        case _                                 => fail()
      }
    }

    "return Right(false) when same collaborator added to two different Organisations" in new Setup {
      await(repo.createOrUpdate(anOrganisation.copy(collaborators = List(aCollaborator))))
      await(repo.createOrUpdate(anOrganisation2.copy(collaborators = List(aCollaborator))))

      await(repo.addCollaboratorToOrganisation(anOrganisation.organisationId, aCollaborator)) match {
        case Right(organisation: Organisation) => organisation.organisationId shouldBe anOrganisation.organisationId
        case _                                 => fail()
      }
    }

  }

  "createOrUpdate" should {
    "return Organisation when create successful" in new Setup {
      await(repo.createOrUpdate(anOrganisation)) match {
        case Right(organisation: Organisation) => organisation.name.value shouldBe anOrganisation.name.value.trim
        case _                                 => fail()
      }
    }

    "return Organisation when upsert successful" in new Setup {
      await(repo.createOrUpdate(anOrganisation))
      val updatedOrganisation = anOrganisation.copy(name = OrganisationName("New organisation name"))
      await(repo.createOrUpdate(updatedOrganisation)) match {
        case Right(organisation: Organisation) => organisation shouldBe updatedOrganisation
        case _                                 => fail()
      }
    }

    "return Left when vendor Id already exists" in new Setup {
      await(repo.createOrUpdate(anOrganisation))

      val updatedOrganisation = anOrganisation.copy(organisationId = OrganisationId.random, name = OrganisationName("New organisation name"), vendorId = aVendorId)
      println(anOrganisation)
      println(updatedOrganisation)
      await(repo.createOrUpdate(updatedOrganisation)) match {
        case Right(a)           =>
          println(a)
          fail()
        case Left(e: Exception) => e.getMessage contains "E11000 duplicate key error"
      }
    }

    "return Left and if try to update name on an Organisation when OrganisationId and VendorId don't match an existing Organisation" in new Setup {
      await(repo.createOrUpdate(anOrganisation))

      val updatedOrganisation = anOrganisation.copy(name = OrganisationName("New organisation name"), vendorId = VendorId(9001))

      await(repo.createOrUpdate(updatedOrganisation)) match {
        case Left(_)  => succeed
        case Right(_) => fail()
      }
    }
  }

  "UpdateOrganisationDetails" should {
    "return UpdateOrganisationSuccessResult with updated name when successful" in new Setup {
      await(repo.createOrUpdate(anOrganisation))
      val updatedName = OrganisationName("updatedName")

      val result = await(repo.updateOrganisationDetails(anOrganisation.organisationId, updatedName))

      result match {
        case UpdateOrganisationSuccessResult(organisation: Organisation) =>
          organisation.name shouldBe updatedName
        case _                                                           => fail()
      }
    }

    "return UpdateOrganisationFailedResult when failure" in new Setup {
      await(repo.createOrUpdate(anOrganisation))
      val updatedName = OrganisationName("updatedName")

      val result = await(repo.updateOrganisationDetails(OrganisationId(UUID.randomUUID()), updatedName))

      result match {
        case _: UpdateOrganisationFailedResult => succeed
        case _                                 => fail()
      }
    }

  }
}
