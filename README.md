# Kasookoo SDK for Android

The Kasookoo SDK allows you to integrate high-quality voice calling (WebRTC & SIP) and real-time messaging into your own Android application.

---

## 📖 Documentation
- **[Quick Reference Handbook](SDK_HANDBOOK.txt)**: A comprehensive guide on SDK functionality, benefits, and usage scenarios.
- **[UI Implementation Guide](README_VIEWS.md)**: Full source code for pre-built screens.

---

## 🚀 Quick Start Guide

To get up and running with the Kasookoo SDK, follow these 4 steps:

1.  **Installation**: Place the `.aar` and add dependencies.
2.  **Setup**: Configure `AndroidManifest.xml` and initialize `KasookooConfig`.
3.  **Authentication**: Login the user via `KasookooAuthManager`.
4.  **Action**: Start a call or chat using `KasookooChatManager`.

---

## 📦 Core Components

| Component | Description |
| :--- | :--- |
| **`KasookooAuthManager`** | Handles user login, permissions, and FCM registration. |
| **`KasookooChatManager`** | Manages calls (P2P/SIP), messaging, and conversation history. |
| **`LiveKitManager`** | The underlying engine for WebRTC audio/video sessions. |
| **`KasookooConfig`** | Global configuration for activity routing and UI state. |

---

## 🛠 1. Installation

### Add SDK File
1. Download the `sdk-release.aar` file.
2. Place the `.aar` file in your app's `libs/` directory (create it if it doesn't exist: `app/libs/`).

### Add Dependency
In your app-level `build.gradle.kts`, add the Kasookoo SDK dependency:

```kotlin
dependencies {
    implementation(files("libs/sdk-release.aar"))
}
```

### Required Dependencies
The SDK requires the following libraries. Please add them to your `build.gradle.kts`:

```kotlin
dependencies {
    // LiveKit SDK (Handles WebRTC audio/video)
    implementation("io.livekit:livekit-android:2.20.2")

    // Firebase Messaging (Handles push notifications)
    implementation(platform("com.google.firebase:firebase-bom:32.2.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Networking (Retrofit handles API calls)
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Utilities
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.github.bumptech.glide:glide:5.0.5")
}
```

### Firebase Setup
A `google-services.json` file from your Firebase console is **required** to be placed in your `app/` directory for push notifications and messaging to work.

---

## 📜 2. Manifest Requirements

### Application Class
You must declare your `Application` class in the `AndroidManifest.xml` so the system knows to use your custom initialization.

```xml
<application
    android:name=".CallApplication"
    ...>
</application>
```

### Permissions
Add the following permissions to your `AndroidManifest.xml`. These are necessary for recording audio and running background services.

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

### SDK Components
Register the SDK's internal services in your `AndroidManifest.xml`. These handle push messages and background call audio.

```xml
<application>
    <!-- Handles incoming Firebase push messages -->
    <service android:name="com.yuave.kasookoo.service.FirebaseMessagingService" android:exported="false">
        <intent-filter>
            <action android:name="com.google.firebase.MESSAGING_EVENT" />
        </intent-filter>
    </service>

    <!-- Manages the full-screen notification for incoming calls -->
    <service android:name="com.yuave.kasookoo.service.CallNotificationService" 
             android:foregroundServiceType="phoneCall" 
             android:permission="android.permission.FOREGROUND_SERVICE" 
             android:exported="false" />

    <!-- Maintains microphone connection when the app is in background -->
    <service android:name="com.yuave.kasookoo.service.CallForegroundService" 
             android:exported="false" 
             android:foregroundServiceType="microphone" />

    <!-- Handles clicks from the notification buttons (Accept/Decline) -->
    <receiver android:name="com.yuave.kasookoo.service.CallActionReceiver" android:exported="false" />
</application>
```

---

## ⚙️ 3. Initialization

### CallApplication.kt
Create an `Application` class and initialize the SDK. You must tell the SDK which of your Activity classes should be opened when a call or chat starts.

```kotlin
class CallApplication : Application() {
    
    lateinit var liveKitManager: LiveKitManager

    override fun onCreate() {
        super.onCreate()
        
        // 1. Initialize the LiveKit singleton
        liveKitManager = LiveKitManager.getInstance(this)
        
        // 2. Register your Activity classes so the SDK knows where to navigate
        KasookooConfig.initialize(
            mainActivity = MainActivity::class.java,
            chatActivity = ChatActivity::class.java,
            callActivity = CallActivity::class.java
        )
        
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("kasookoo_calls", "Calls", NotificationManager.IMPORTANCE_HIGH)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
```

---

## 🔑 4. Authentication Flow

### KasookooAuthManager
Use this class to handle the login process. It handles user identification and FCM device registration in one flow.

```kotlin
val authManager = KasookooAuthManager(context, "YOUR_PUBLISHABLE_KEY")

// Step 1: Request permissions (Microphone, Notifications, etc.)
authManager.requestPermissions(activity)

// Step 2: Login the user by email
authManager.loginAndInitialize("user@email.com", object : KasookooAuthManager.AuthCallback {
    override fun onProgress(show: Boolean) { /* Show/hide spinner */ }

    override fun onSuccess(user: UserModelData) {
        // User is now logged in and device is registered for push notifications.
    }

    override fun onError(message: String) { /* Handle error */ }
})
```

---

## 📞 5. Voice Calling (WebRTC & SIP)

### KasookooChatManager
Use this class to manage all calling operations.

#### Initiate a Peer-to-Peer Call
```kotlin
chatManager.initiateCall(targetUser, object : KasookooChatManager.CallCallback {
    override fun onSuccess(response: Any, roomName: String) {
        val res = response as CallerTokenResponse
        chatManager.connectToRoom(res.caller_token, res.ws_url, roomName)
    }
})
```

#### Make a Support (SIP) Call
```kotlin
chatManager.makeSupportCall("+123456789", object : KasookooChatManager.CallCallback {
    override fun onSuccess(response: Any, roomName: String) {
        val res = response as SupportCallMakeResponse
        chatManager.connectToRoom(res.data.access_token, res.wsUrl, roomName)
    }
})
```

#### Call Controls
```kotlin
chatManager.acceptCall() // Accept incoming
chatManager.toggleMute(isMuted) // Toggle mic
chatManager.toggleSpeaker() // Toggle speakerphone
chatManager.endCall(isIncoming, room, participant, caller, called) { finish() } // Hang up
```

---

## 💬 6. Messaging & Chat

#### Messaging Actions
```kotlin
chatManager.getConversations(callback) // Fetch chat list
chatManager.getChatMessages(id, userId, callback) // Fetch history
chatManager.publishChatMessage(room, "Hello") // Send real-time message
chatManager.saveMessage(text, room, receiver, sender) // Save to DB
```

#### Real-time Events
```kotlin
chatManager.observeChatEvents(room, object : KasookooChatManager.ChatEventCallback {
    override fun onMessageReceived(message: MessageModel) { /* Update UI */ }
})
```

---

## 🎨 7. Pre-built UI Components
For a complete reference on how to implement the **Calling Screen**, **Chat Screen**, and **User Dashboard**, please refer to our **[UI Implementation Guide (README_VIEWS.md)](README_VIEWS.md)**.

---

## 🚪 8. Logout
```kotlin
chatManager.logout { success ->
    if (success) { /* Navigate to Login */ }
}
```
