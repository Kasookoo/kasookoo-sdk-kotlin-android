# Kasookoo SDK for Android

The Kasookoo SDK allows you to integrate high-quality voice calling (WebRTC & SIP) and real-time messaging into your own Android application.

## 1. Installation

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

## 2. Manifest Requirements

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

## 3. Initialization

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
        // Create the notification channel required for Android 8.0+
        val channel = NotificationChannel("kasookoo_calls", "Calls", NotificationManager.IMPORTANCE_HIGH)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
```

---

## 4. Authentication Flow

### KasookooAuthManager
Use this class to handle the login process. It handles user identification and FCM device registration in one flow.

```kotlin
val authManager = KasookooAuthManager(context, "YOUR_PUBLISHABLE_KEY")

// Step 1: Request permissions (Microphone, Notifications, etc.)
// This shows the system dialog to the user.
authManager.requestPermissions(activity)

// Step 2: Login the user by email
authManager.loginAndInitialize("user@email.com", object : KasookooAuthManager.AuthCallback {
    override fun onProgress(show: Boolean) {
        // Use this to show or hide a loading spinner on your UI
    }

    override fun onSuccess(user: UserModelData) {
        // User is now logged in and device is registered for push notifications.
        // You can now navigate to your Main/Home screen.
    }

    override fun onError(message: String) {
        // Handle login failure (e.g., show a Toast with the error message)
    }
})
```

---

## 5. Voice Calling (WebRTC & SIP)

### KasookooChatManager
Use this class to manage all calling and messaging operations.

#### Initiate a Peer-to-Peer Call
Starts a call between two app users.
```kotlin
val chatManager = KasookooChatManager(context)

chatManager.initiateCall(targetUser, object : KasookooChatManager.CallCallback {
    override fun onConnecting() {
        // Show "Connecting..." UI
    }

    override fun onSuccess(response: Any, roomName: String) {
        // 1. Convert response to token model
        val res = response as CallerTokenResponse
        // 2. Connect to the LiveKit room to start the audio session
        chatManager.connectToRoom(res.caller_token, res.ws_url, roomName)
    }

    override fun onError(message: String) {
        // Show error message
    }
})
```

#### Make a Support (SIP) Call
Calls a real phone number via SIP.
```kotlin
chatManager.makeSupportCall("+123456789", object : KasookooChatManager.CallCallback {
    override fun onSuccess(response: Any, roomName: String) {
        // For SIP calls, the response contains the 'access_token' and 'wsUrl' directly
        val res = response as SupportCallMakeResponse
        chatManager.connectToRoom(res.data.access_token, res.wsUrl, roomName)
    }
})
```

#### During a Call (Controls)
```kotlin
// Accept an incoming call (changes state to IN_CALL)
chatManager.acceptCall()

// Toggle microphone (returns the new state: true for muted, false for unmuted)
val isMutedNow = chatManager.toggleMute(currentMuteState)

// Toggle speakerphone (switches between earpiece and speaker)
val isSpeakerOnNow = chatManager.toggleSpeaker()

// End the call and disconnect from the server
chatManager.endCall(isIncoming, roomName, participantId, callerId, calledId) {
    // Callback: Close your Call Activity here
    finish()
}
```

---

## 6. Messaging & Chat

#### Fetching Conversations
Get a list of all active chats for the current user.
```kotlin
chatManager.getConversations(object : KasookooChatManager.ListCallback<List<Conversation>> {
    override fun onSuccess(data: List<Conversation>) {
        // 'data' contains the list of active chats to show in your RecyclerView
    }
})
```

#### Messaging Actions
```kotlin
// Fetch message history for a specific chat ID
chatManager.getChatMessages(conversationId, userId, callback)

// Mark all messages in a chat as 'read'
chatManager.markMessagesAsRead(conversationId, userId)

// Send a plain text message to a LiveKit room
chatManager.publishChatMessage(room, "Hello world!")

// Send your current GPS coordinates to the other participant
chatManager.publishLocation(room, latitude, longitude)

// Save the message to the backend database so it appears in history
chatManager.saveMessage("Hello world!", roomName, receiverId, userId) { success ->
    // Message saved successfully
}
```

#### Real-time Chat Events
Listen for incoming messages while you are on the chat screen.
```kotlin
chatManager.observeChatEvents(room, object : KasookooChatManager.ChatEventCallback {
    override fun onMessageReceived(message: MessageModel) {
        // A new text message arrived. Add it to your chat list and scroll to bottom.
    }

    override fun onLocationReceived(lat: Double, lng: Double) {
        // A location update arrived. Update your map or show a location bubble.
    }
})
```

---

## 7. Utilities & Session

#### Updating UI Lists from Broadcasts
If your app is open and an FCM message or local broadcast arrives, use these to update your local `conversationList` without refreshing the whole API.

```kotlin
// Handle updates from Push Notifications
conversationList = chatManager.handleFCMUpdate(intent, conversationList)

// Handle updates from local broadcasts
conversationList = chatManager.handleBroadCastUpdate(intent, conversationList)
```

#### Logout
Revokes the session token and clears all local user data.
```kotlin
chatManager.logout { success ->
    if (success) {
        // Navigate the user back to the Login screen
    }
}
```
