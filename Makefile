#
# Copyright 2025 Adobe. All rights reserved.
# This file is licensed to you under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License. You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under
# the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
# OF ANY KIND, either express or implied. See the License for the specific language
# governing permissions and limitations under the License.
#

EXTENSION-LIBRARY-FOLDER-NAME = contentanalytics
TEST-APP-FOLDER-NAME = sample-app
CURRENT_DIRECTORY := ${CURDIR}

init:
	git config core.hooksPath .githooks

clean:
	(./gradlew clean)

format:
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):spotlessApply)
		
format-license:
	(./gradlew licenseFormat)

# Used by build and test CI workflow
lint:
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):lint)

unit-test:
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):testPhoneDebugUnitTest)

unit-test-coverage:
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):createPhoneDebugUnitTestCoverageReport)

functional-test:
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):uninstallPhoneDebugAndroidTest)
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):connectedPhoneDebugAndroidTest)

functional-test-coverage:
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):uninstallPhoneDebugAndroidTest)
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):createPhoneDebugAndroidTestCoverageReport)

javadoc:
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):dokkaJavadoc)

assemble-phone:
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):assemblePhone)

assemble-phone-debug:
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):assemblePhoneDebug)
		
assemble-phone-release:
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):assemblePhoneRelease)

assemble-app:
	(./gradlew $(TEST-APP-FOLDER-NAME):assemble)

ci-publish-maven-local-jitpack: assemble-phone-release
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):publishReleasePublicationToMavenLocal -Pjitpack)

ci-publish-staging: clean
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):publish)

ci-publish: assemble-phone-release
	(./gradlew $(EXTENSION-LIBRARY-FOLDER-NAME):publish -Prelease)

