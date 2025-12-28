# Fossify Messages with OTP and transaction detection
<img alt="Logo" src="graphics/icon.webp" width="120" />

<a href='https://play.google.com/store/apps/details?id=org.fossify.messages'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height=80/></a> <a href="https://f-droid.org/packages/org.fossify.messages/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" alt="Get it on F-Droid" height=80/></a> <a href="https://apt.izzysoft.de/fdroid/index/apk/org.fossify.messages"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height=80/></a>

Fossify Messages is your trusted messaging companion, designed to enhance your messaging experience in various ways.
The above is the official fossify message links however in my fork the following changes
1. Build & Compatibility Fixes
•
Resolved Build Error 25: Added --enable-native-access, --add-opens, and -XX:+UseParallelGC to gradle.properties to ensure compatibility with Gradle 8.13 and JDK 21.
•
Robust APK Signing: Updated app/build.gradle.kts to automatically detect signing credentials from your global ~/.gradle/gradle.properties using SIGNING_ snake_case naming conventions.
•
Warning Cleanup: Fixed various deprecation warnings in Context.kt (Glide into calls) and resolved unresolved references during the build process.
2. New Features: OTP & Transaction Automation
•
OTP Detection: Created OTPDetector.kt to identify verification codes. The app now automatically copies OTPs to the clipboard and plays a dedicated otp.mp3 sound.
•
Smart Transaction Announcements:
◦
Added TransactionDetector.kt to parse debit/credit alerts from major banks (Axis, HDFC, IDFC, Kotak, Federal, etc.).
◦
Implemented a Singleton TTSHelper to announce transactions out loud (e.g., "Paid 100 rupees to Amazon from HDFC bank").
◦
Conflict Prevention: Ensured that messages containing both OTPs and amounts are prioritized as OTPs to avoid redundant announcements.
•
Dedicated Notification Channels: Created separate Android notification channels for OTP and Transactions to allow for independent customization and audio routing.
3. App Functionality Enhancements
•
Mark All as Read: Fully implemented this missing feature by adding the necessary Room DAO queries, a Context extension, and a new menu option in the MainActivity toolbar.
•
Notification Actions: Fixed the "Mark as read" button in the notification bar to correctly dismiss unique OTP and Transaction notifications by passing their specific hash IDs to the MarkAsReadReceiver.
4. Settings & UI Cleanup
•
Unlocked Premium Features: Removed the purchase requirement and "locked" labels for Blocked Numbers and Blocked Keywords settings.
•
Removed Monetization Prompts: Completely hid the "Purchase Fossify Thank You" item from the General Settings to provide a cleaner, fully open-source experience.


<div align="center">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="30%">
</div>

