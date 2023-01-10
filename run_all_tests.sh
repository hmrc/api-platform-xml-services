#!/usr/bin/env bash
sbt clean scalafmtAll scalafixAll scalastyle coverage test it:test coverageReport
