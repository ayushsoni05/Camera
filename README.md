# PrismaticAura - Snapchat Camera Clone

**PrismaticAura** is an outstanding, feature-packed Android camera application designed to clone the visual aesthetics and core features of Snapchat's camera system (without the chat/social networking elements). Built on Android's high-performance **CameraX** framework and integrated with **Google ML Kit Face Detection**, the app delivers real-time AR lenses, gesture-driven capture mechanics, post-capture custom editing, and an in-app memories gallery drawer.

---

## 🌟 Premium Features

### 1. Snapchat Gestures & Shutter Interactions
- **Hold-to-Record Video**: Tap the shutter button once to capture a high-resolution photo, or press-and-hold it to record video snap (up to 15 seconds).
- **Circular Progress Ring**: A smooth progress ring wraps around the shutter button, filling up dynamically as you record to show the time limit.
- **Drag-to-Zoom Gesture**: Slide your thumb vertically up from the shutter button while holding it down to zoom in, and down to zoom out (simulating Snapchat's intuitive zoom controls).
- **Double Tap to Flip**: Double-tap anywhere on the viewfinder screen to swap between front and back cameras.
- **Swipe Up Memories**: Swipe up from the bottom of the screen to pull open your saved snaps gallery.

### 2. Live AR Lenses & Real-Time Tracking
- **AR Lenses Carousel**: A horizontal row of circular icons representing different face masks.
- **Real-Time Landmark Tracking**: Powered by Google ML Kit Face Detection, the app tracks eyes, nose, and mouth positions in real-time at 30 FPS.
- **Interactive AR Masks**: Align and scale decorative stickers (e.g. Dog Ears & Tongue, Cool Sunglasses, Neon Royal Crown, Mustache) onto the user's face dynamically.
- **Auto Smile Shutter**: Takes a photo automatically when a smiling face (probability > 85%) is detected in photo mode.
- **Live Color Tints**: Carousel of real-time color filters (Noir, Cyberpunk, Forest, Sunset, Retro) using hardware-accelerated canvas paint matrices.

### 3. Snapchat Post-Capture Editing Suite
- **Horizontal Swipe Filters**: Swipe left/right on captured snaps to cycle through gorgeous filters (Noir, Warm, Cool, Vintage) in real-time.
- **Interactive Doodle/Draw Tool**: Draw freehand lines on captured snaps using a colorful brush.
- **Stylized Text Tool**: Tap to add a text overlay, choose custom text colors, and drag the text label anywhere on the snap to compose the shot.
- **Bounce/Loop Video**: Automatically loops recorded video snaps continuously in the preview screen.
- **Save to Memories**: Download your final snap (incorporating filters, drawings, and text) to the internal memories directory.
- **Quick Share Sheet**: Share your snap instantly to other apps using Android's native share utility.

### 4. Professional Camera Helpers
- **Selfie Soft Flash**: Briefly illuminates the screen with a warm amber/yellow fill light before front camera captures in dark environments.
- **Digital Level Horizon**: Center level overlay line tilts based on rotation sensors, turning neon green with a haptic pulse when perfectly balanced.
- **Manual Exposure & Zoom**: Adjust exposure compensation (EV index) and linear zoom indexes using slider components.
- **Composition Grid**: Toggle a standard grid overlay for rule-of-thirds alignment.

---

## 🛠 Tech Stack & Dependencies

- **Platform**: Android SDK (minSdk 24, targetSdk 35)
- **Programming Language**: Java 8 / Kotlin (build configuration)
- **Camera Core**: `androidx.camera:camera-core` (CameraX version `1.3.0-rc01`)
- **Real-time Face ML**: `com.google.mlkit:face-detection:16.1.5`
- **UI & View Components**: `com.google.android.material:material:1.9.0`, `androidx.viewpager2:viewpager2:1.1.0`

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
- Android Studio Ladybug or newer.
- Android SDK 35 configured.
- A physical Android device with camera capability (for testing rotation sensors and ML face detection).

### Build Locally
To compile the application and generate a debug APK:
1. Clone or navigate to the repository directory.
2. Run the Gradle build wrapper task:
   ```bash
   ./gradlew assembleDebug
   ```
3. Locate the generated APK at:
   `app/build/outputs/apk/debug/app-debug.apk`
