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

package uk.gov.hmrc.apiplatformxmlservices.service

import java.util.UUID

import uk.gov.hmrc.apiplatformxmlservices.models._

trait CommonTestData {
  val uuidVal: UUID      = UUID.fromString("dcc80f1e-4798-11ec-81d3-0242ac130003")
  val vendorId: VendorId = VendorId(9000)

  val getUuid: UUID                  = UUID.randomUUID()
  val organisationId: OrganisationId = OrganisationId(uuidVal)

  val organisation: Organisation                           = Organisation(organisationId, vendorId = vendorId, name = OrganisationName("Organisation Name"))
  val createOrganisationRequest: CreateOrganisationRequest = CreateOrganisationRequest(organisation.name, "some@email.com", "firstName", "lastName")
  val updatedOrgName: OrganisationName                     = OrganisationName("Updated Organisation Name")

  val userId: UserId = UserId(UUID.randomUUID())
}
