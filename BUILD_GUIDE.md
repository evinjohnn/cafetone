# 🚀 CaféTone - Android Audio DSP App

## ✅ **Updated to Latest Versions (2025)**

This project has been updated with:
- **Modern UI Redesign**: Material Design 3 with spacious, premium layout
- **GitHub Star Dialog**: One-time encouragement for users to star the project
- **Firebase Analytics**: Comprehensive event tracking and crash reporting
- **Global Audio Processing**: Real-time system-wide audio enhancement

## 🔧 **Setup Instructions**

### **1. Prerequisites**
- **Android Studio**: Latest stable version (2024.1.1+)
- **JDK**: 17 or higher (included with Android Studio)
- **Android SDK**: API 35 installed
- **Android NDK**: Latest version
- **Physical Android Device**: API 24+ (audio effects don't work in emulator)
- **Google Account**: For Firebase setup

### **2. Project Setup**

1. **Clone and Open Project**
   ```bash
   # Open Android Studio
   # File → Open → Select the /app folder
   ```

2. **Configure SDK Paths**
   - Copy `local.properties.template` to `local.properties`
   - Update paths in `local.properties`:
   ```properties
   sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
   ndk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk\\ndk\\25.2.9519653
   ```

3. **Firebase Setup (REQUIRED)**
   - Follow instructions in [FIREBASE_SETUP.md](FIREBASE_SETUP.md)
   - Download `google-services.json` to `app/` directory
   - **The app will not compile without this file**

4. **Install Required SDKs**
   - Go to **Tools → SDK Manager**
   - Install **Android 15 (API 35)**
   - Install **Latest NDK** (25.x.x)
   - Install **CMake** (latest version)

### **3. Build Process**

1. **Sync Project**
   ```bash
   # In Android Studio: File → Sync Project with Gradle Files
   # Or click "Sync Now" when prompted
   ```

2. **Build APK**
   ```bash
   # Method 1: Android Studio
   Build → Build Bundle(s)/APK(s) → Build APK(s)
   
   # Method 2: Command Line
   ./gradlew assembleDebug
   ```

3. **Install on Device**
   ```bash
   # Connect Android device via USB
   # Enable Developer Options and USB Debugging
   # Click "Run" in Android Studio or use:
   ./gradlew installDebug
   ```

## 🆕 **New Features (2025 Update)**

### **Modern UI Redesign**
- Clean header with app icon and title
- Prominent Master Toggle Card with integrated status
- Organized Controls Card with proper spacing and dividers
- Enhanced Action Buttons with better visual hierarchy
- Material Design 3 throughout

### **GitHub Star Dialog**
- One-time dialog on first app launch
- Encourages users to star the project on GitHub
- Never shows again after first display
- Additional star option in About dialog

### **Firebase Analytics Integration**
- Comprehensive event tracking:
  - `cafe_mode_toggled` - When users enable/disable café mode
  - `slider_adjusted` - When users adjust intensity/spatial/distance
  - `github_star_clicked` - When users click GitHub star button
  - `app_launch`, `first_launch`, permissions, and more
- Crash reporting with Crashlytics
- Privacy-focused (no personal data collected)

## 🔍 **Testing**

### **Required Device Setup**
1. **Enable Developer Options**
2. **Enable USB Debugging**
3. **Disable USB Audio Routing** (if present)
4. **Connect headphones/earbuds**

### **Testing Steps**
1. **Install APK** on device
2. **Grant all permissions** when prompted
3. **Complete Shizuku setup** if needed
4. **Open music app** (Spotify, YouTube Music, etc.)
5. **Play music** and return to CaféTone
6. **Toggle Café Mode** and adjust sliders
7. **Verify audio effect** is applied globally

### **Testing New Features**
1. **UI Redesign**: Verify clean, modern interface
2. **GitHub Dialog**: Uninstall/reinstall to see first-launch dialog
3. **Firebase Analytics**: Check Firebase Console for events

## 📊 **Analytics Events Tracked**

The app tracks these events for improvement (all anonymous):
- App launches and usage patterns
- Feature usage (café mode toggles, slider adjustments)
- User engagement (GitHub star clicks, dialog interactions)
- Performance metrics and crash reports

## 🔧 **Build Variants**

### **Debug Build**
- Includes debugging symbols
- Firebase DebugView enabled
- Faster build times
- Larger APK size

### **Release Build**
- Optimized and obfuscated
- Production Firebase events
- Smaller APK size
- Requires signing for distribution

## 📋 **Project Structure**

```
CaféTone/
├── app/
│   ├── google-services.json     # Firebase config (add manually)
│   ├── src/main/
│   │   ├── cpp/                 # Native C++ DSP code
│   │   ├── java/                # Kotlin application code
│   │   ├── res/                 # Resources and UI
│   │   └── AndroidManifest.xml
│   └── build.gradle             # App build configuration
├── gradle/
│   └── wrapper/                 # Gradle wrapper
├── build.gradle                 # Project build configuration
├── settings.gradle              # Project settings
├── FIREBASE_SETUP.md           # Firebase setup instructions
├── PRIVACY_POLICY.md           # Privacy policy for analytics
└── README.md                   # This file
```

## 🚀 **Ready to Build!**

The project now includes:
- ✅ Modern Material Design 3 UI
- ✅ GitHub star encouragement system
- ✅ Firebase Analytics integration
- ✅ Global audio processing enhancement
- ✅ Privacy-focused analytics tracking

**Next Steps:**
1. Set up Firebase project and download `google-services.json`
2. Build and install on Android device
3. Test global audio processing with music apps
4. Monitor Firebase Analytics for user engagement

**Need help?** Check the troubleshooting section or create an issue in the repository.