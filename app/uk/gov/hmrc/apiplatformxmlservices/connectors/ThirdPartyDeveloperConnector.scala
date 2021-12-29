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

package uk.gov.hmrc.apiplatformxmlservices.connectors

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

import uk.gov.hmrc.apiplatformxmlservices.connectors.ThirdPartyDeveloperConnector.Config
import uk.gov.hmrc.apiplatformxmlservices.models.{CoreUserDetails, FindUserIdRequest, FindUserIdResponse}
import uk.gov.hmrc.apiplatformxmlservices.models.JsonFormatters._
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ThirdPartyDeveloperConnector @Inject() (http: HttpClient, config: Config)(implicit val ec: ExecutionContext) {

  def findUserId(email: String)(implicit hc: HeaderCarrier): Future[Option[CoreUserDetails]] = {
    http.POST[FindUserIdRequest, Option[FindUserIdResponse]](s"${config.thirdPartyDeveloperUrl}/developers/find-user-id", FindUserIdRequest(email))
      .map {
        case Some(response) => Some(CoreUserDetails(email, response.userId))
        case None           => None
      }
  }
}

object ThirdPartyDeveloperConnector {
  case class Config(thirdPartyDeveloperUrl: String)
}
