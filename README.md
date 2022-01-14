# TestFairy's - Testers App

Testers app lets testers file bug reports and download apps that are using the [TestFairy](https://www.testfairy.com) platform.

## Installation

To install, clone this project from github and create an .APK by running:

```shell
./gradlew assembleDebug
```

If you are on a private cloud, make sure to update `MainActivity::BASE_URL` with your cloud URL before building the app.

## Changelog

### Version 2.7.0

* Removed TestFairy Android SDK and gradle plugin from the project.

### Version 2.6.0

* [IMPROVEMENT] Compiled with the latest Android SDK.

### Version 2.5.1

* [NEW] Support Android TV

### Version 1.8

* No major changes, just compilation settings updated.

### Version 1.7 
* Added: Updated app icon.
* Added: Creating a TestFairy account on device, so when using an app powered by TestFairy's SDK, it will pick up on the account used to log in into the testers' app.

### Version 1.6
* Added: Support back for closing on screen dialogs.
* Added: Moved project to GitHub.

### Version 1.5
* Bugfix: Progress bar randomly crashed below API version 11.

## Support

For bug reports or questions, please file a [GitHub issue](https://github.com/testfairy/testers-app-android/issues) or email [support@testfairy.com](mailto:support@testfairy.com)
