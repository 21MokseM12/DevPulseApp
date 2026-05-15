#!/usr/bin/env bash

set -euo pipefail

./gradlew :app:testDebugUnitTest \
  --tests "com.devpulse.app.ui.auth.*" \
  --tests "com.devpulse.app.data.repository.DefaultAuthRepositoryTest" \
  --tests "com.devpulse.app.data.remote.Auth*"

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.devpulse.app.ui.auth.AuthUiIntegrationTest
