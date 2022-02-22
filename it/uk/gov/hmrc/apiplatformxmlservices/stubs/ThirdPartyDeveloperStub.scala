/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformxmlservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.JsonFormatters._
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.GetOrCreateUserIdRequest
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.UserResponse
import uk.gov.hmrc.apiplatformxmlservices.models.UserId
import uk.gov.hmrc.apiplatformxmlservices.models.thirdpartydeveloper.ImportUserRequest
import uk.gov.hmrc.apiplatformxmlservices.models.ApiCategory
import uk.gov.hmrc.apiplatformxmlservices.models.ServiceName

trait ThirdPartyDeveloperStub {
  val createOrGetUserIdUrl = "/developers/user-id"

def stubCreateOrGetUserIdReturnsResponse(email: String, responseBodyAsString: String) ={
    val requestAsString = Json.toJson(GetOrCreateUserIdRequest(email)).toString
  
    stubPostWithRequestBody("/developers/user-id", OK, requestAsString, responseBodyAsString)
}


def stubCreateOrGetUserIdReturnsNoResponse(email: String, status: Int) ={
    val requestAsString = Json.toJson(GetOrCreateUserIdRequest(email)).toString
  
    stubPostWithRequestBodyNoResponse("/developers/user-id", status, requestAsString)
}

def stubGetByEmailsReturnsResponse(emails: List[String], responseAsString: String) ={
    val requestAsString = Json.toJson(emails).toString()

     stubPostWithRequestBody("/developers/get-by-emails", OK, requestAsString, responseAsString)
}

def stubGetByEmailsReturnsNoResponse(emails: List[String], status: Int) ={
    val requestAsString = Json.toJson(emails).toString()

     stubPostWithRequestBodyNoResponse("/developers/get-by-emails", status, requestAsString)
}

def stubCreateVerifiedUserSuccess(email: String, firstName: String, lastName: String, userId: UserId, emailPreferences: Map[ApiCategory, List[ServiceName]], status: Int) ={
    val createXmlUserRequestObj = ImportUserRequest(email, firstName, lastName, emailPreferences)
    val requestAsString = Json.toJson(createXmlUserRequestObj).toString

    val userResponse = UserResponse(
      email = email,
      firstName = firstName,
      lastName = lastName,
      verified = true,
      userId = userId
    )

    stubPostWithRequestBody("/import-user", status, requestAsString, Json.toJson(userResponse).toString())

}

def stubCreateVerifiedUserEmptyResponse(email: String, firstName: String, lastName: String, emailPreferences: Map[ApiCategory, List[ServiceName]], status: Int) ={
    val importUserRequestObj = ImportUserRequest(email, firstName, lastName, emailPreferences)
    val requestAsString = Json.toJson(importUserRequestObj).toString


    stubPostWithRequestBodyNoResponse("/import-user", status, requestAsString)

}

 private def stubPostWithRequestBody(url: String, status: Int, expectedRequestBody: String, responseBodyAsString: String) = {
      stubFor(
        post(urlEqualTo(url))
          .withRequestBody(equalTo(expectedRequestBody))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(responseBodyAsString)
              .withHeader("Content-Type", "application/json")
          )
      )
    }


     private def stubPostWithRequestBodyNoResponse(url: String, status: Int, expectedRequestBody: String) = {
      stubFor(
        post(urlEqualTo(url))
          .withRequestBody(equalTo(expectedRequestBody))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withHeader("Content-Type", "application/json")
          )
      )
    }



}
