# Firebase Setup Instructions for CaféTone

## 📋 Prerequisites
- Google account
- Android Studio with the project opened

## 🚀 Step-by-Step Setup

### 1. Create Firebase Project
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Create a project" or "Add project"
3. Enter project name: `cafetone-audio` (or your preference)
4. Enable Google Analytics for this project (recommended)
5. Select or create Analytics account
6. Click "Create project"

### 2. Add Android App to Firebase
1. In the Firebase console, click "Add app" and select Android
2. Enter the package name: `com.cafetone.audio`
3. Enter app nickname: `CaféTone`
4. Enter SHA-1 key (optional for development):
   ```bash
   # Get debug SHA-1
   ./gradlew signingReport
   ```
5. Click "Register app"

### 3. Download Configuration File
1. Download the `google-services.json` file
2. **IMPORTANT**: Move this file to `app/` directory (same level as `app/build.gradle`)
   ```
   /app/
   ├── app/
   │   ├── google-services.json  ← Place file here
   │   ├── build.gradle
   │   └── src/
   ```

### 4. Enable Services
In the Firebase console:
1. **Analytics**: Already enabled if you chose it during setup
2. **Crashlytics**: 
   - Go to "Crashlytics" in left menu
   - Click "Get started" 
   - Follow the setup steps (already done in our build.gradle)

### 5. Verify Setup
1. Build and run the app
2. Perform some actions (toggle café mode, adjust sliders)
3. Check Firebase console > Analytics > Events (may take 24 hours to appear)

## ⚠️ Important Notes
- **Never commit `google-services.json` to public repositories**
- Add to `.gitignore`:
  ```
  # Firebase
  google-services.json
  ```
- For release builds, you'll need the release SHA-1 key

## 🧪 Testing Firebase Integration
The app logs these events:
- `app_launch` - When app starts
- `first_launch` - First time app is opened
- `cafe_mode_toggled` - When café mode is turned on/off
- `slider_adjusted` - When any slider is moved
- `github_star_clicked` - When GitHub star button is pressed

Check Firebase Console > Analytics > DebugView for real-time events during development.