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

class OrganisationRepositoryISpec extends AnyWordSpec
  with MongoApp[Organisation]
  with GuiceOneAppPerSuite
  with ScalaFutures
  with BeforeAndAfterEach
  with AwaitTestSupport {

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

}
