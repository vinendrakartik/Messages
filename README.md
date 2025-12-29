# Fossify Messages with OTP and transaction detection
<img alt="Logo" src="graphics/icon.webp" width="120" />


Fossify Messages is your trusted messaging companion, designed to enhance your messaging experience in various ways.
**The above is the official fossify message links however in my fork the following changes**<br><br><br>
**1. Build & Compatibility Fixes**<br>
• **Resolved Build Error 25**: Added --enable-native-access, --add-opens, and -XX:+UseParallelGC to gradle.properties to ensure compatibility with Gradle 8.13 and JDK 21. This is personal preference<br>
• **Robust APK Signing**: Updated app/build.gradle.kts to automatically detect signing credentials from your global ~/.gradle/gradle.properties using SIGNING_ snake_case naming conventions.<br>
• **Warning Cleanup**: Fixed various deprecation warnings in Context.kt (Glide into calls) and resolved unresolved references during the build process.<br><br>
**2. New Feature**s: OTP & Transaction Automation<br>
• **OTP Detection**: Created OTPDetector.kt to identify verification codes. The app now automatically copies OTPs to the clipboard and plays a dedicated otp.mp3 sound.<br><br>
**Smart Transaction Announcements**:<br>
◦ Added TransactionDetector.kt to parse debit/credit alerts from major banks (Axis, HDFC, IDFC, Kotak, Federal, etc.).<br>
◦ Implemented a Singleton TTSHelper to announce transactions out loud (e.g., "Paid 100 rupees to Amazon from HDFC bank").<br>
◦ Conflict Prevention: Ensured that messages containing both OTPs and amounts are prioritized as OTPs to avoid redundant announcements.<br>
• Dedicated Notification Channels: Created separate Android notification channels for OTP and Transactions to allow for independent customization and audio routing.<br><br>
**3. App Functionality Enhancements**<br>
• **Mark All as Read: **Fully implemented this missing feature by adding the necessary Room DAO queries, a Context extension, and a new menu option in the MainActivity toolbar.<br>
• **Notification Actions:** Fixed the "Mark as read" button in the notification bar to correctly dismiss unique OTP and Transaction notifications by passing their specific hash IDs to the MarkAsReadReceiver. (Not in main source)<br><br>
**4. Settings & UI Cleanup**<br>
• **Unlocked Premium Features:** Removed the purchase requirement and "locked" labels for Blocked Numbers and Blocked Keywords settings.<br>
• **Removed Monetization Prompts:** Completely hid the "Purchase Fossify Thank You" item from the General Settings to provide a cleaner, fully open-source experience.<br>
