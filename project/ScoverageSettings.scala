import scoverage.ScoverageKeys

object ScoverageSettings {
  def apply() =
    Seq(
      // Semicolon-separated list of regexs matching classes to exclude
      ScoverageKeys.coverageExcludedPackages := Seq(
        "<empty",
        """.*\.domain\.models\..*""",
        """uk.gov.hmrc.apiplatformxmlservices.controllers\.binders\..*""",
        """uk\.gov\.hmrc\.apiplatform.modules\.apis\..*""",
        """uk\.gov\.hmrc\.apiplatform.modules\.applications\..*""",
        """uk\.gov\.hmrc\.apiplatform.modules\.developers\..*""",
        """uk\.gov\.hmrc\.apiplatform.modules\.common\..*""",
        """uk\.gov\.hmrc\.BuildInfo""",
        """.*\.Routes""",
        """.*\.RoutesPrefix""",
        """.*Filters?""",
        """MicroserviceAuditConnector""",
        """Module""",
        """GraphiteStartUp""",
        """.*\.Reverse[^.]*""",
        """uk\.gov\.hmrc\.apiplatform\.modules\.test_only\..*""",
        """uk\.gov\.hmrc\.apiplatform\.modules\.scheduling\..*"""
      ).mkString(";"),
      ScoverageKeys.coverageMinimumStmtTotal := 95.80,
      ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageKeys.coverageHighlighting := true
    )

}
