#!/usr/bin/env bash
sbt clean scalafmtAll scalafixAll  coverage test it:test coverageReport
