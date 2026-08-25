# Us.

"Just you. Just me. Always connected."

A private digital connection app built for two partners to send handwritten/drawn messages. 

## Project Structure
This repository contains the full Android template for "Us." built with Jetpack Compose.
- `app/src/main/java/com/us/app/ui/` -> Compose UI (Canvas, Home, Theme)
- `app/src/main/java/com/us/app/data/` -> Models for Firestore synchronization
- `app/src/main/java/com/us/app/service/` -> Floating LoveBrush Overlay and Push Notifications

## Setup Instructions

1. **Open in Android Studio:** Open the `UsApp` folder in Android Studio.
2. **Firebase Setup:**
   - Go to the [Firebase Console](https://console.firebase.google.com/)
   - Create a new project.
   - Add an Android app with the package name `com.us.app`.
   - Download the `google-services.json` file and place it in the `app/` directory.
   - Enable **Firestore** in Test Mode.
   - Enable **Authentication** (Anonymous provider).
   - Enable **Cloud Messaging**.
3. **Build and Run:** Sync the project with Gradle files and click "Run".

## Current Status
- Project scaffolding completed.
- Android Permissions configured for Internet, Overlay, and Notifications.
- Core Data Models established.
- Jetpack Compose Theme added.

*Note: The actual real-time drawing logic requires Firebase integration to fully synchronize strokes across devices.*
