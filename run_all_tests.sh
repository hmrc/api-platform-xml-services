#!/usr/bin/env bash
sbt clean scalafmtAll scalafix test:scalafmtAll  test:scalafix it:test::scalafmtAll it:test::scalafix coverage test it:test coverageReport
