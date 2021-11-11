package uk.gov.hmrc.apiplatformxmlservices.support

import org.scalatest.{BeforeAndAfterEach, Suite, TestSuite}
import uk.gov.hmrc.apiplatformxmlservices.models.Organisation
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

trait MongoApp[A] extends DefaultPlayMongoRepositorySupport[A]  with BeforeAndAfterEach  {
  me: Suite with TestSuite =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
  }

  def dropMongoDb(): Unit =
    mongoDatabase.drop()
}


