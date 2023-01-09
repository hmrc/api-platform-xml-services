#!/usr/bin/env bash
sbt clean scalafmtAll test:scalafmtAll  it:test::scalafmtAll coverage test it:test coverageReport
