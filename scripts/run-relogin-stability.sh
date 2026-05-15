#!/usr/bin/env bash

set -euo pipefail

./gradlew :app:testDebugUnitTest \
  --tests "com.devpulse.app.ui.auth.AuthErrorMessageMapperTest" \
  --tests "com.devpulse.app.ui.main.MainLogoutLifecycleIntegrationTest"

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.devpulse.app.ui.DevPulseAppTest#reloginAfterLogout_keepsSubscriptionsUpdatesAndSettingsStable,com.devpulse.app.ui.DevPulseAppTest#reloginAfterLogout_withRestartBetweenAuthSteps_staysStable

./gradlew build
