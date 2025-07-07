# CaféTone - Professional Android Audio DSP

A professional-grade Android audio processing application that replicates Sony's premium headphone "Listening Mode" feature. CaféTone psychoacoustically transforms any audio playing on an Android device to sound as if it's coming from speakers in a distant café environment.

## 🎵 Features

### Core Audio Processing
- **System-wide audio processing** using Android AudioEffect framework
- **Real-time DSP** implemented in native C++ for optimal performance
- **Psychoacoustic simulation** of distant speaker perception
- **Spatial audio widening** using Haas effect and binaural processing
- **Environmental modeling** with EQ, reverb, and air absorption

### Advanced DSP Algorithms
- **Binaural Processing**: HRTF simulation for distance perception
- **Early Reflections**: Multi-tap delay for room acoustics
- **Air Absorption**: Frequency-dependent attenuation over distance
- **Haas Effect**: Stereo widening with micro-delays (0-15ms)
- **Dynamic EQ**: Variable high-pass (50-200Hz) and low-pass (4-12kHz)
- **Subtle Ambience**: Very low-level ambient noise injection

### User Interface
- **Minimal, elegant design** with Material Design 3
- **Real-time parameter control** without audio glitches
- **Master toggle** for café mode on/off
- **Intensity slider** (dry/wet mix control)
- **Spatial width slider** (Haas delay amount)
- **Distance slider** (perceived distance simulation)

## 🏗️ Architecture

### Native C++ DSP Library
```
app/src/main/cpp/
├── cafetone_dsp.cpp          # Main DSP interface
├── audio_processor.h/cpp     # Base processor class
├── haas_processor.h/cpp      # Haas effect implementation
├── eq_processor.h/cpp        # EQ and filtering
├── binaural_processor.h/cpp  # HRTF and spatial processing
├── reverb_processor.h/cpp    # Early reflections and reverb
└── CMakeLists.txt           # Build configuration
```

### Android Application Layer
```
app/src/main/java/com/cafetone/audio/
├── dsp/
│   └── CafeModeDSP.kt       # Native library wrapper
├── service/
│   └── CafeModeService.kt   # Foreground service
├── receiver/
│   └── BootReceiver.kt      # Boot persistence
└── MainActivity.kt          # Main UI
```

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Latest stable version
- **NDK**: r25 or later for C++ compilation
- **Target SDK**: API 33+ for modern Android features
- **Minimum SDK**: API 24 for AudioEffect support

### Building the Project

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/cafetone.git
   cd cafetone
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cafetone directory and select it

3. **Build and Run**
   - Connect an Android device (audio effects don't work in emulator)
   - Click "Run" or press Shift+F10
   - Grant necessary permissions when prompted

### Required Permissions
- `RECORD_AUDIO`: For audio processing
- `MODIFY_AUDIO_SETTINGS`: For system-wide audio effects
- `FOREGROUND_SERVICE`: For persistent audio processing
- `POST_NOTIFICATIONS`: For service notifications
- `RECEIVE_BOOT_COMPLETED`: For persistence across reboots

## 🎛️ Usage

### Basic Operation
1. **Launch the app** and grant permissions
2. **Toggle Café Mode** using the main switch
3. **Adjust parameters** using the sliders:
   - **Intensity**: Controls the strength of the effect (dry/wet mix)
   - **Spatial Width**: Adjusts stereo widening (Haas delay amount)
   - **Distance**: Simulates perceived distance from speakers

### Advanced Features
- **System Integration**: Works with any audio source (Spotify, YouTube, etc.)
- **Persistent Service**: Continues running in background
- **Boot Persistence**: Automatically starts on device boot
- **Real-time Control**: Parameter changes apply instantly

## 🔧 Technical Details

### DSP Processing Chain
```
Input Audio → EQ Filter → Haas Effect → Binaural Processing → Reverb → Output
```

### Performance Targets
- **CPU Usage**: <5% on mid-range devices
- **Memory Footprint**: <10MB total app size
- **Audio Latency**: <20ms processing delay
- **Battery Impact**: Minimal - optimized for continuous use

### Audio Quality Benchmarks
Compare against Sony's implementation:
- **Spatial Perception**: Convincing distance simulation
- **Frequency Response**: Natural café-like EQ curve
- **Stereo Imaging**: Wide, ambient soundstage
- **Transparency**: Seamless integration with any audio source

## 🛠️ Development

### Project Structure
```
cafetone/
├── app/
│   ├── build.gradle                 # App-level build config
│   ├── proguard-rules.pro          # ProGuard rules
│   └── src/main/
│       ├── cpp/                    # Native C++ DSP code
│       ├── java/                   # Kotlin/Java application code
│       ├── res/                    # Resources (layouts, drawables, etc.)
│       └── AndroidManifest.xml     # App manifest
├── build.gradle                    # Project-level build config
├── settings.gradle                 # Project settings
└── README.md                       # This file
```

### Key Components

#### Native DSP Engine
- **Optimized C++ implementation** for real-time audio processing
- **Modular design** with separate processors for different effects
- **Memory-safe** with no malloc/free in real-time audio path
- **Thread-safe** with proper synchronization

#### Android Service Architecture
- **Foreground service** for persistent audio processing
- **AudioEffect integration** using reflection for private APIs
- **Session management** for multiple audio sources
- **Proper lifecycle management** and cleanup

#### User Interface
- **Material Design 3** with modern styling
- **View Binding** for type-safe view access
- **Real-time parameter updates** without audio glitches
- **Responsive design** for different screen sizes

### Building for Production

1. **Release Build**
   ```bash
   ./gradlew assembleRelease
   ```

2. **Optimization**
   - ProGuard rules are configured for code optimization
   - Native code is compiled with -O3 optimization
   - NEON instructions enabled for ARM devices

3. **Testing**
   - Test on real devices (not emulator)
   - Test with different audio sources
   - Test with various headphone types
   - Verify system integration

## 🐛 Troubleshooting

### Common Issues

#### Audio Effects Not Working
- **Ensure you're testing on a real device** (not emulator)
- **Check permissions** are granted
- **Verify audio is playing** from another app
- **Check logcat** for error messages

#### High CPU Usage
- **Reduce intensity** setting
- **Lower spatial width** parameter
- **Check for other audio apps** running simultaneously

#### Audio Glitches
- **Increase buffer size** in native code
- **Reduce processing complexity** for older devices
- **Check device performance** and thermal throttling

### Debug Information
Enable debug logging by setting the log level in the native code:
```cpp
#define LOG_LEVEL ANDROID_LOG_DEBUG
```

## 📱 Compatibility

### Supported Devices
- **Android 7.0+** (API 24+)
- **ARM64-v8a** (primary target)
- **ARMv7** (secondary support)
- **x86/x86_64** (development only)

### Audio Sources
- **Music apps**: Spotify, Apple Music, YouTube Music
- **Video apps**: YouTube, Netflix, VLC
- **Games**: Any app with audio output
- **System sounds**: Notifications, ringtones

### Headphone Types
- **Wired headphones**: 3.5mm, USB-C, Lightning
- **Bluetooth headphones**: All codecs supported
- **Wireless earbuds**: AirPods, Galaxy Buds, etc.

## 🤝 Contributing

### Development Guidelines
1. **Follow the existing code style**
2. **Add comprehensive comments** for DSP algorithms
3. **Test on real devices** before submitting
4. **Update documentation** for new features
5. **Ensure backward compatibility**

### Code Quality Standards
- **Memory Safe**: No memory leaks in audio path
- **Thread Safe**: Proper synchronization
- **Error Handling**: Graceful degradation
- **Documentation**: Clear comments for all algorithms

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Sony Corporation** for inspiration from their Listening Mode feature
- **Android AudioEffect Framework** for system integration
- **Material Design** for the beautiful UI components
- **OpenSL ES** for native audio processing capabilities

## 📞 Support

For questions, issues, or contributions:
- **GitHub Issues**: Report bugs and feature requests
- **Discussions**: General questions and community support
- **Email**: [evinjohnignatious11@gmail.com]

---

**CaféTone** - Transform your audio experience with professional-grade DSP processing. # cafetone
