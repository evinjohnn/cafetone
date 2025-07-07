# 🚀 CaféTone - Android Audio DSP App

## ✅ **Updated to Latest Versions (2025)**

This project has been updated to use the latest stable versions of all tools and dependencies:

- **Gradle**: 8.4 (Latest Stable)
- **Android Gradle Plugin**: 8.7.2 (Latest)
- **Kotlin**: 2.0.21 (Latest)
- **Target SDK**: Android 15 (API 35)
- **Compile SDK**: Android 15 (API 35)
- **NDK**: Latest compatible with AGP 8.7.2
- **All Dependencies**: Latest stable versions

## 🔧 **Setup Instructions**

### **1. Prerequisites**
- **Android Studio**: Latest stable version (2024.1.1+)
- **JDK**: 17 or higher (included with Android Studio)
- **Android SDK**: API 35 installed
- **Android NDK**: Latest version
- **Physical Android Device**: API 24+ (audio effects don't work in emulator)

### **2. Project Setup**

1. **Download and Open Project**
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

3. **Install Required SDKs**
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

## 🔍 **Troubleshooting**

### **Common Issues & Solutions**

#### **1. Gradle Sync Failed**
```bash
# Clean and rebuild
./gradlew clean
./gradlew build
```

#### **2. NDK Not Found**
- Install NDK via SDK Manager
- Update `ndk.dir` in `local.properties`

#### **3. CMake Errors**
- Install CMake via SDK Manager
- Ensure CMake version 3.22.1+ is installed

#### **4. Permission Errors (Windows)**
- Run Android Studio as Administrator
- Check antivirus isn't blocking Gradle

#### **5. Out of Memory**
```bash
# Increase heap size in gradle.properties (already configured)
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

## 📱 **Testing**

### **Required Device Setup**
1. **Enable Developer Options**
2. **Enable USB Debugging**
3. **Disable USB Audio Routing** (if present)
4. **Connect headphones/earbuds**

### **Testing Steps**
1. **Install APK** on device
2. **Grant all permissions** when prompted
3. **Open music app** (Spotify, YouTube Music, etc.)
4. **Play music** and return to CaféTone
5. **Toggle Café Mode** and adjust sliders
6. **Verify audio effect** is applied

## 🎯 **Performance Optimization**

The project includes several optimizations:
- **Gradle Configuration Cache**: Faster builds
- **Parallel Execution**: Reduced build times
- **R8 Optimization**: Smaller APK size
- **Native Code Optimization**: -O3, LTO, NEON instructions
- **Latest Kotlin Compiler**: Improved performance

## 🔧 **Build Variants**

### **Debug Build**
- Includes debugging symbols
- Faster build times
- Larger APK size

### **Release Build**
- Optimized and obfuscated
- Smaller APK size
- Requires signing for distribution

## 📋 **Project Structure**

```
CaféTone/
├── app/
│   ├── src/main/
│   │   ├── cpp/              # Native C++ DSP code
│   │   ├── java/             # Kotlin application code
│   │   ├── res/              # Resources and UI
│   │   └── AndroidManifest.xml
│   └── build.gradle          # App build configuration
├── gradle/
│   └── wrapper/              # Gradle wrapper
├── build.gradle              # Project build configuration
├── settings.gradle           # Project settings
├── gradle.properties         # Gradle configuration
└── local.properties          # Local SDK paths (create from template)
```

## 🚀 **Ready to Build!**

The project is now fully updated and compatible with the latest Android development tools. Follow the setup instructions above and you should have no compatibility issues.

**Need help?** Check the troubleshooting section or create an issue in the repository.