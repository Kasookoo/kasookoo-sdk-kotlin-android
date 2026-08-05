# Kasookoo Pre-built UI Guide

This guide provides the complete source code for the pre-built Activities and Adapters used in this project. You can copy these classes directly into your project after importing the Kasookoo SDK.

## 1. Activities

### StartActivity.kt
**Layout**: `activity_start.xml`
Handles login and initial permissions.

```kotlin
package com.yuave.kasookoo.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yuave.kasookoo.KasookooAuthManager
import com.yuave.kasookoo.data.PrefHelper
import com.yuave.kasookoo.data.UserModelData
import com.yuave.kasookoo.databinding.ActivityStartBinding
import com.yuave.kasookoo.databinding.ProgressDialogBinding

class StartActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStartBinding
    private lateinit var authManager: KasookooAuthManager
    private lateinit var progressDialog: AlertDialog
    private var publishableKey = "YOUR_PUBLISHABLE_KEY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (PrefHelper.getInstance(this).getToken().isNotEmpty()) {
            navigateToMain()
        }
        showProgressDialog()
        authManager = KasookooAuthManager(this, publishableKey)
        authManager.requestPermissions(this)
        binding.loginButton.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sdkSessionAuth(email)
        }
    }

    private fun sdkSessionAuth(email: String) {
        authManager.loginAndInitialize(email, object : KasookooAuthManager.AuthCallback {
            override fun onProgress(show: Boolean) {
                if (show) progressDialog.show() else progressDialog.dismiss()
            }
            override fun onSuccess(user: UserModelData) { navigateToMain() }
            override fun onError(message: String) {
                Toast.makeText(this@StartActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        authManager.handlePermissionResult(requestCode, grantResults)
    }

    private fun showProgressDialog() {
        val binding = ProgressDialogBinding.inflate(LayoutInflater.from(this))
        progressDialog = AlertDialog.Builder(this).setView(binding.root).create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        progressDialog.setCancelable(false)
    }
}
```

---

### MainActivity.kt
**Layout**: `activity_main.xml`
The dashboard for user lists and conversations.

```kotlin
package com.yuave.kasookoo.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yuave.kasookoo.KasookooChatManager
import com.yuave.kasookoo.KasookooConfig
import com.yuave.kasookoo.data.*
import com.yuave.kasookoo.databinding.ActivityMainBinding
import com.yuave.kasookoo.databinding.ProgressDialogBinding
import com.yuave.kasookoo.service.CallManager
import kotlinx.coroutines.launch

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var chatManager: KasookooChatManager
    private lateinit var prefHelper: PrefHelper
    private lateinit var progressDialog: AlertDialog
    private lateinit var userAdapter: UserListAdapter
    private lateinit var chatAdapter: ChatListAdapter
    private var conversationList = ArrayList<Conversation>()
    private val allUsers = ArrayList<UserModelData>()
    private val visibleUsers = ArrayList<UserModelData>()
    private var currentPage = 0
    private val pageSize = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        chatManager = KasookooChatManager(this)
        prefHelper = PrefHelper.getInstance(this)
        setupUI()
        setupProgressDialog()
        refreshUsers()
    }

    private fun setupUI() {
        binding.apply {
            tvUserName.text = "Welcome ${prefHelper.getUserFullName()}"
            tvUserEmail.text = prefHelper.getUser().email
            chatAdapter = ChatListAdapter(conversationList) { position ->
                val model = conversationList[position]
                model.unread_count = 0
                openChat(model.participant_user_id, model.participant_name, model.participant_email, model.conversation_id)
            }
            rvChat.adapter = chatAdapter
            llCall.setOnClickListener { switchTab(isChat = false) }
            llChat.setOnClickListener { switchTab(isChat = true) }
            ivLogout.setOnClickListener { showLogoutDialog() }
            ivKeypad.setOnClickListener { 
                startActivity(Intent(this@MainActivity, CallActivity::class.java).apply { putExtra("isKeypad", true) })
            }
            ivRefresh.setOnClickListener { if (rvUsers.visibility == View.VISIBLE) refreshUsers() else refreshConversations() }
        }
    }

    private fun switchTab(isChat: Boolean) {
        binding.apply {
            chatBar.visibility = if (isChat) View.VISIBLE else View.INVISIBLE
            callBar.visibility = if (isChat) View.INVISIBLE else View.VISIBLE
            rvUsers.visibility = if (isChat) View.GONE else View.VISIBLE
            rvChat.visibility = if (isChat) View.VISIBLE else View.GONE
        }
    }

    private fun refreshUsers() {
        chatManager.getUsers(object : KasookooChatManager.ListCallback<List<UserModelData>> {
            override fun onProgress(show: Boolean) { if (show) progressDialog.show() else progressDialog.dismiss() }
            override fun onSuccess(data: List<UserModelData>) {
                allUsers.clear()
                allUsers.addAll(data)
                setupUserAdapter()
                refreshConversations()
            }
            override fun onError(message: String) { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
            override fun onLogout() { logout() }
        })
    }

    private fun refreshConversations() {
        chatManager.getConversations(object : KasookooChatManager.ListCallback<List<Conversation>> {
            override fun onProgress(show: Boolean) { if (show) progressDialog.show() else progressDialog.dismiss() }
            override fun onSuccess(data: List<Conversation>) {
                conversationList.clear()
                conversationList.addAll(data)
                chatAdapter.updateList(conversationList)
            }
            override fun onError(message: String) { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
            override fun onLogout() { logout() }
        })
    }

    private fun initiateCall(user: UserModelData) {
        chatManager.initiateCall(user, object : KasookooChatManager.CallCallback {
            override fun onConnecting() { progressDialog.show() }
            override fun onSuccess(response: Any, roomName: String) {
                val res = response as CallerTokenResponse
                lifecycleScope.launch {
                    chatManager.connectToRoom(res.caller_token, res.ws_url, roomName)
                    progressDialog.dismiss()
                    startActivity(Intent(this@MainActivity, CallActivity::class.java).apply {
                        putExtra("contactName", "${user.first_name} ${user.last_name}")
                        putExtra("calledUserId", user.id)
                    })
                }
            }
            override fun onError(message: String) { progressDialog.dismiss() }
        })
    }

    private fun setupUserAdapter() {
        currentPage = 0
        visibleUsers.clear()
        userAdapter = UserListAdapter(visibleUsers, { initiateCall(it) }, { user ->
            val conv = conversationList.find { it.participant_user_id == user.id }
            openChat(user.id, "${user.first_name} ${user.last_name}", user.email, conv?.conversation_id ?: "")
        })
        binding.rvUsers.adapter = userAdapter
        loadNextPage()
    }

    private fun loadNextPage() {
        val start = currentPage * pageSize
        if (start >= allUsers.size) return
        val end = minOf(start + pageSize, allUsers.size)
        visibleUsers.addAll(allUsers.subList(start, end))
        userAdapter.notifyDataSetChanged()
        currentPage++
    }

    private fun openChat(userId: String, name: String, email: String, conversationId: String) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("calledUserId", userId)
            putExtra("userName", name)
            putExtra("email", email)
            putExtra("conversationId", conversationId)
        })
    }

    private fun logout() {
        prefHelper.clear()
        startActivity(Intent(this, StartActivity::class.java))
        finish()
    }

    private fun setupProgressDialog() {
        val dialogBinding = ProgressDialogBinding.inflate(LayoutInflater.from(this))
        progressDialog = AlertDialog.Builder(this).setView(dialogBinding.root).create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private val fcmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { conversationList = chatManager.handleFCMUpdate(it, conversationList) }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(fcmReceiver, IntentFilter("FCM_MESSAGE"), RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        KasookooConfig.isMainActivityVisible = true
    }

    override fun onPause() {
        super.onPause()
        KasookooConfig.isMainActivityVisible = false
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(fcmReceiver)
    }
}
```

---

### CallActivity.kt
**Layout**: `activity_call.xml`
Unified screen for dialing, ringing, and active calls.

```kotlin
package com.yuave.kasookoo.ui

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.yuave.kasookoo.CallApplication
import com.yuave.kasookoo.KasookooChatManager
import com.yuave.kasookoo.KasookooConfig
import com.yuave.kasookoo.R
import com.yuave.kasookoo.core.CallState
import com.yuave.kasookoo.core.KasookooRingtoneManager
import com.yuave.kasookoo.core.LiveKitManager
import com.yuave.kasookoo.data.*
import com.yuave.kasookoo.databinding.ActivityCallBinding
import com.yuave.kasookoo.service.CallManager
import com.yuave.kasookoo.service.FirebaseMessagingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallBinding
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var chatManager: KasookooChatManager
    private lateinit var signalListener: SignalStrengthListener
    private var ringbackPlayer: MediaPlayer? = null
    private var isIncomingCall = false
    private var isKeypad = false
    private var isMuted = false
    private var contactName = ""

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        liveKitManager = (application as CallApplication).liveKitManager
        chatManager = KasookooChatManager(this)
        handleIntentData()
        setupUI()
        setupClickListeners()
        setupKeypad()
        observeCallState()
        signalListener = SignalStrengthListener(this) { level -> updateSignalUI(level) }

        if (isIncomingCall) {
            KasookooRingtoneManager.play(this)
        } else if (!isKeypad) {
            startRingbackTone()
            liveKitManager.forceEnableAudio()
        }
    }

    private fun handleIntentData() {
        isIncomingCall = intent.getBooleanExtra("is_incoming_call", false)
        isKeypad = intent.getBooleanExtra("isKeypad", false)
        contactName = intent.getStringExtra("contactName") ?: intent.getStringExtra("caller_name") ?: "Unknown"
        if (intent.getBooleanExtra("auto_accept", false)) answerCall()
    }

    private fun setupUI() {
        binding.keypadSection.visibility = if (isKeypad) View.VISIBLE else View.GONE
        binding.callingSection.visibility = if (isKeypad) View.GONE else View.VISIBLE
        binding.tvContactName.text = "$contactName (Agent)"
        binding.tvCallStatus.text = if (isIncomingCall) "Incoming Call" else "Calling Agent"
        updateControlsVisibility(isInCall = false)
    }

    private fun setupKeypad() {
        val buttons = listOf(R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, 
                            R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnStar, R.id.btnHash)
        buttons.forEach { id ->
            findViewById<TextView>(id).setOnClickListener { btn -> binding.tvNumber.append((btn as TextView).text) }
        }
        binding.btn0.setOnClickListener { binding.tvNumber.append("0") }
        binding.btn0.setOnLongClickListener { binding.tvNumber.append("+"); true }
        binding.ivBackSpace.setOnClickListener {
            val text = binding.tvNumber.text.toString()
            if (text.isNotEmpty()) binding.tvNumber.text = text.dropLast(1)
        }
        binding.btnStartCall.setOnClickListener {
            val number = binding.tvNumber.text.toString()
            if (number.isNotEmpty() && (number.startsWith("00") || number.startsWith("+"))) {
                initiateSupportCall(number)
            }
        }
    }

    private fun initiateSupportCall(number: String) {
        chatManager.makeSupportCall(number, object : KasookooChatManager.CallCallback {
            override fun onConnecting() { 
                binding.keypadSection.visibility = View.GONE
                binding.callingSection.visibility = View.VISIBLE
                binding.tvContactName.text = "$number (Agent)"
                startRingbackTone()
            }
            override fun onSuccess(response: Any, roomName: String) {
                val data = (response as SupportCallMakeResponse).data ?: return
                lifecycleScope.launch { chatManager.connectToRoom(data.access_token, response.wsUrl ?: "", roomName) }
            }
            override fun onError(message: String) { finish() }
        })
    }

    private fun answerCall() {
        binding.btnAnswer.visibility = View.GONE
        KasookooRingtoneManager.stop()
        stopRingbackTone()
        val roomName = intent.getStringExtra("room_name") ?: ""
        if (isIncomingCall && roomName.isNotEmpty()) {
            lifecycleScope.launch { chatManager.connectToRoom(FirebaseMessagingService.caller_token, FirebaseMessagingService.ws_url, roomName) }
        } else { chatManager.acceptCall() }
    }

    private fun endCall() {
        chatManager.endCall(isIncomingCall, intent.getStringExtra("room_name") ?: "", 
            intent.getStringExtra("participant_identity") ?: "", 
            intent.getStringExtra("caller_user_id") ?: "", 
            intent.getStringExtra("calledUserId") ?: "") { finish() }
    }

    private fun observeCallState() {
        lifecycleScope.launch {
            liveKitManager.callState.collect { state ->
                when (state) {
                    CallState.IN_CALL -> {
                        updateControlsVisibility(isInCall = true)
                        binding.tvCallStatus.text = "Connected"
                        startCallTimer()
                        stopRingbackTone()
                    }
                    CallState.IDLE -> finish()
                    CallState.ERROR -> finish()
                    else -> {}
                }
            }
        }
    }

    private fun startCallTimer() {
        lifecycleScope.launch {
            while (CallManager.isCallActive) {
                val d = liveKitManager.getCurrentCallDuration()
                binding.tvCallDuration.text = String.format("%02d:%02d", d / 60, d % 60)
                delay(1000)
            }
        }
    }

    private fun toggleMute() {
        isMuted = chatManager.toggleMute(isMuted)
        binding.btnMute.setImageResource(if (isMuted) com.app.sdk.R.drawable.ic_mic_off else com.app.sdk.R.drawable.ic_microphone)
    }

    private fun toggleSpeaker() {
        val isOn = chatManager.toggleSpeaker()
        binding.btnSpeaker.setImageResource(if (isOn) com.app.sdk.R.drawable.ic_speaker_on else com.app.sdk.R.drawable.ic_speaker_off)
    }

    private fun updateControlsVisibility(isInCall: Boolean) {
        binding.btnAnswer.visibility = if (!isInCall && isIncomingCall) View.VISIBLE else View.GONE
        binding.btnMute.visibility = if (isInCall) View.VISIBLE else View.GONE
        binding.btnSpeaker.visibility = if (isInCall) View.VISIBLE else View.GONE
        binding.tvCallDuration.visibility = if (isInCall) View.VISIBLE else View.GONE
    }

    private fun startRingbackTone() {
        try {
            (getSystemService(AUDIO_SERVICE) as AudioManager).mode = AudioManager.MODE_IN_COMMUNICATION
            ringbackPlayer = MediaPlayer.create(this, R.raw.phone_ring).apply {
                isLooping = true
                start()
            }
        } catch (e: Exception) {}
    }

    private fun stopRingbackTone() {
        ringbackPlayer?.stop()
        ringbackPlayer?.release()
        ringbackPlayer = null
    }

    private fun updateSignalUI(level: Int) {
        val icon = when (level) {
            0, 1 -> R.drawable.ic_signal_1
            2 -> R.drawable.ic_signal_2
            3 -> R.drawable.ic_signal_3
            else -> R.drawable.ic_signal_4
        }
        binding.ivSignalBars.setImageResource(icon)
    }

    override fun onResume() { super.onResume(); signalListener.startListening() }
    override fun onPause() { super.onPause(); signalListener.stopListening() }
    override fun onDestroy() { KasookooRingtoneManager.stop(); stopRingbackTone(); super.onDestroy() }
}
```

---

### ChatActivity.kt
**Layout**: `activity_chat.xml`
1-on-1 messaging with real-time text and location sharing.

```kotlin
package com.yuave.kasookoo.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.yuave.kasookoo.KasookooChatManager
import com.yuave.kasookoo.KasookooConfig
import com.yuave.kasookoo.data.CallerTokenResponse
import com.yuave.kasookoo.data.MessageModel
import com.yuave.kasookoo.data.PrefHelper
import com.yuave.kasookoo.databinding.ActivityChatBinding
import com.yuave.kasookoo.databinding.ProgressDialogBinding
import io.livekit.android.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    companion object {
        var isOpen: Boolean = false
        var conversationId: String = ""
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatManager: KasookooChatManager
    private lateinit var prefHelper: PrefHelper
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var progressDialog: AlertDialog
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var room: Room? = null
    private var observeJob: Job? = null
    private var chatList = ArrayList<MessageModel>()
    private var receiverId = ""
    private var receiverName = ""
    private var email = ""
    private var roomName = ""
    private var userId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        chatManager = KasookooChatManager(this)
        prefHelper = PrefHelper.getInstance(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        handleIntentData()
        setupUI()
        setupProgressDialog()
        initializeChat()
    }

    private fun handleIntentData() {
        conversationId = intent.getStringExtra("conversationId") ?: ""
        email = intent.getStringExtra("email") ?: ""
        receiverName = intent.getStringExtra("userName") ?: ""
        receiverId = intent.getStringExtra("calledUserId") ?: ""
        userId = prefHelper.getUser().id
        val ids = listOf(userId, receiverId).sorted()
        roomName = "chat_${ids[0]}_${ids[1]}"
    }

    private fun setupUI() {
        binding.apply {
            tvName.text = receiverName
            tvInitials.text = receiverName.split(" ").filter { it.isNotEmpty() }.joinToString("") { it.first().uppercase() }
            tvEmail.text = email
            chatAdapter = ChatAdapter(userId, prefHelper.getUser().first_name, receiverName, this@ChatActivity, chatList)
            rvChat.adapter = chatAdapter
            ivBack.setOnClickListener { finish() }
            ivAttach.setOnClickListener { requestLocationPermission() }
            ivSend.setOnClickListener {
                val msg = etMessage.text.toString().trim()
                if (msg.isNotEmpty()) sendMessage(msg)
            }
        }
    }

    private fun initializeChat() {
        chatManager.getChatToken(roomName, userId, receiverId, receiverName, email, "", object : KasookooChatManager.CallCallback {
            override fun onConnecting() { progressDialog.show() }
            override fun onSuccess(response: Any, roomName: String) {
                val tokenResponse = response as CallerTokenResponse
                lifecycleScope.launch {
                    val chatRoom = chatManager.createChatRoom()
                    room = chatRoom
                    chatManager.connectToChatRoom(chatRoom, tokenResponse.ws_url, tokenResponse.access_token)
                    observeJob = launch { observeEvents(chatRoom) }
                    progressDialog.dismiss()
                    fetchMessages()
                    chatManager.markMessagesAsRead(conversationId, userId)
                }
            }
            override fun onError(message: String) { progressDialog.dismiss() }
        })
    }

    private fun fetchMessages() {
        chatManager.getChatMessages(conversationId, userId, object : KasookooChatManager.ListCallback<List<MessageModel>> {
            override fun onProgress(show: Boolean) { if (show) progressDialog.show() else progressDialog.dismiss() }
            override fun onSuccess(data: List<MessageModel>) {
                chatList.clear()
                chatList.addAll(data)
                updateChatUI()
            }
            override fun onError(message: String) { progressDialog.dismiss() }
            override fun onLogout() {}
        })
    }

    private fun sendMessage(text: String) {
        room?.let { chatRoom ->
            chatManager.publishChatMessage(chatRoom, text)
            chatManager.saveMessage(text, roomName, receiverId, userId) { success -> }
            chatList.add(MessageModel("", conversationId, roomName, text, userId, "text", System.currentTimeMillis().toString()))
            updateChatUI()
            binding.etMessage.setText("")
        }
    }

    private suspend fun observeEvents(chatRoom: Room) {
        chatManager.observeChatEvents(chatRoom, object : KasookooChatManager.ChatEventCallback {
            override fun onMessageReceived(message: MessageModel) {
                lifecycleScope.launch(Dispatchers.Main) {
                    chatList.add(message)
                    updateChatUI()
                    chatManager.markMessagesAsRead(conversationId, userId)
                }
            }
            override fun onLocationReceived(lat: Double, lng: Double) {}
        })
    }

    private fun updateChatUI() {
        chatAdapter.updateList(chatList)
        binding.rvChat.scrollToPosition(chatList.size - 1)
    }

    private fun setupProgressDialog() {
        val binding = ProgressDialogBinding.inflate(LayoutInflater.from(this))
        progressDialog = AlertDialog.Builder(this).setView(binding.root).create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            checkLocationEnabled()
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) checkLocationEnabled()
    }

    private fun checkLocationEnabled() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) getLocation() else turnOnLocation()
    }

    private fun turnOnLocation() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        val settingsRequest = LocationSettingsRequest.Builder().addLocationRequest(request).build()
        LocationServices.getSettingsClient(this).checkLocationSettings(settingsRequest)
            .addOnSuccessListener { getLocation() }
    }

    @SuppressLint("MissingPermission")
    private fun getLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                sendMessage("${it.latitude}, ${it.longitude}")
                room?.let { r -> chatManager.publishLocation(r, it.latitude, it.longitude) }
            }
        }
    }

    override fun onResume() { super.onResume(); isOpen = true; KasookooConfig.isChatOpen = true; KasookooConfig.currentConversationId = conversationId }
    override fun onPause() { super.onPause(); isOpen = false; KasookooConfig.isChatOpen = false }
    override fun onDestroy() { super.onDestroy(); observeJob?.cancel(); room?.disconnect() }
}
```

## 2. Adapters

### UserListAdapter.kt
```kotlin
package com.yuave.kasookoo.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yuave.kasookoo.data.UserModelData
import com.yuave.kasookoo.databinding.ItemListUserBinding

class UserListAdapter(
    private var items: ArrayList<UserModelData>,
    private val onItemSelected: (UserModelData) -> Unit,
    private val onItemSelectedChat: (UserModelData) -> Unit,
) : RecyclerView.Adapter<UserListAdapter.MyViewHolder>() {
    inner class MyViewHolder(private val binding: ItemListUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UserModelData) {
            binding.apply {
                tvName.text = "${item.first_name} ${item.last_name}"
                tvEmail.text = item.email
                ivCall.setOnClickListener { onItemSelected(item) }
                ivChat.setOnClickListener { onItemSelectedChat(item) }
            }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder = MyViewHolder(ItemListUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) = holder.bind(items[position])
}
```

---

### ChatListAdapter.kt
```kotlin
package com.yuave.kasookoo.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yuave.kasookoo.data.Conversation
import com.yuave.kasookoo.databinding.ItemListConversationBinding

class ChatListAdapter(
    private var items: ArrayList<Conversation>,
    private val onItemSelected: (Int) -> Unit,
) : RecyclerView.Adapter<ChatListAdapter.MyViewHolder>() {
    inner class MyViewHolder(private val binding: ItemListConversationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Conversation) {
            binding.apply {
                tvName.text = item.participant_name
                tvMsg.text = item.last_message
                tvCount.visibility = if (item.unread_count > 0) View.VISIBLE else View.INVISIBLE
                tvCount.text = item.unread_count.toString()
                itemView.setOnClickListener { onItemSelected(adapterPosition) }
            }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder = MyViewHolder(ItemListConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) = holder.bind(items[position])
    fun updateList(newItems: ArrayList<Conversation>) { items = newItems; notifyDataSetChanged() }
}
```

---

### ChatAdapter.kt
```kotlin
package com.yuave.kasookoo.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yuave.kasookoo.R
import com.yuave.kasookoo.data.MessageModel
import com.yuave.kasookoo.databinding.ItemListChatBinding

class ChatAdapter(
    var userId: String, var userName: String, var receiverName: String, var context: Context, private var items: List<MessageModel>
) : RecyclerView.Adapter<ChatAdapter.MyViewHolder>() {
    inner class MyViewHolder(private val binding: ItemListChatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MessageModel) {
            binding.apply {
                tvText.text = item.message
                val isMe = item.sender_user_id == userId
                llMain.gravity = if (isMe) Gravity.END else Gravity.START
                llText.backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, if (isMe) R.color.chat_right else R.color.chat_left))
            }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder = MyViewHolder(ItemListChatBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
    fun updateList(newItems: List<MessageModel>) { items = newItems; notifyDataSetChanged() }
}
```

## 3. Utilities

### SignalStrengthListener.kt
```kotlin
package com.yuave.kasookoo.ui
import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager

class SignalStrengthListener(private val context: Context, private val onSignalChanged: (Int) -> Unit) : PhoneStateListener() {
    private var telephonyManager: TelephonyManager? = null
    fun startListening() {
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager?.listen(this, LISTEN_SIGNAL_STRENGTHS)
    }
    fun stopListening() { telephonyManager?.listen(this, LISTEN_NONE) }
    override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
        signalStrength?.let { onSignalChanged(it.level) }
    }
}
```
