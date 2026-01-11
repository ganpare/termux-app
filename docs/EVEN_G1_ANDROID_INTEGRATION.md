# EVEN G1 ARグラス Android ネイティブ統合ガイド

このドキュメントでは、AndroidネイティブアプリにEVEN G1 ARグラスとのBLE通信機能を組み込む方法を説明します。

---

## 📋 目次

1. [必要な準備](#必要な準備)
2. [パーミッション設定](#パーミッション設定)
3. [BLE通信の実装](#ble通信の実装)
4. [プロトコル実装](#プロトコル実装)
5. [使用例](#使用例)
6. [表示仕様](#表示仕様)
7. [トラブルシューティング](#トラブルシューティング)

---

## 必要な準備

### build.gradle (app)

```groovy
android {
    compileSdk 34
    
    defaultConfig {
        minSdk 24  // BLE機能にはAndroid 7.0以上推奨
        targetSdk 34
    }
}

dependencies {
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
}
```

---

## パーミッション設定

### AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    
    <!-- Bluetooth permissions for Android 12+ (API 31+) -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" 
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" 
        tools:targetApi="s" />
    
    <!-- Bluetooth permissions for Android 11 and below -->
    <uses-permission android:name="android.permission.BLUETOOTH"
        android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30" />
    
    <!-- Location permissions (required for BLE scan on older Android versions) -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
    
    <!-- BLEハードウェア要件 -->
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>
    
    <application ...>
        <!-- アプリの内容 -->
    </application>
</manifest>
```

### ランタイムパーミッション取得

```kotlin
class MainActivity : AppCompatActivity() {
    
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // パーミッション取得成功 - BLEスキャン開始可能
            EvenG1Manager.instance.startScan()
        }
    }
    
    fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }
}
```

---

## BLE通信の実装

### 1. 定数定義

```kotlin
object EvenG1Constants {
    // BLE Service/Characteristic UUIDs（Nordic UART Service）
    const val SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
    const val WRITE_CHARACTERISTIC_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  // TX
    const val READ_CHARACTERISTIC_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"   // RX
    
    // Descriptor UUID for notifications
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    
    // ハートビート間隔
    const val HEARTBEAT_INTERVAL_MS = 15000L  // 15秒
    
    // デバイス名パターン: G1_{チャンネル番号}_{L/R}_{ID}
    val DEVICE_NAME_PATTERN = "G\\d+".toRegex()
}
```

### 2. デバイスモデル

```kotlin
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build

data class EvenG1Device(
    val name: String,
    val address: String,
    val channelNumber: String,
    var gatt: BluetoothGatt? = null,
    var writeCharacteristic: BluetoothGattCharacteristic? = null,
    var isConnected: Boolean = false
) {
    fun isLeft(): Boolean = name.contains("_L_")
    fun isRight(): Boolean = name.contains("_R_")
    
    @SuppressLint("MissingPermission")
    fun sendData(data: ByteArray): Boolean {
        val g = gatt ?: return false
        val char = writeCharacteristic ?: return false
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = data
                @Suppress("DEPRECATION")
                g.writeCharacteristic(char)
            }
        } catch (e: Exception) {
            Log.e("EvenG1Device", "Send error: $e")
            false
        }
    }
    
    companion object {
        fun fromScanResult(name: String, address: String): EvenG1Device? {
            if (!name.contains(EvenG1Constants.DEVICE_NAME_PATTERN)) return null
            
            val parts = name.split("_")
            if (parts.size != 4) return null
            
            return EvenG1Device(
                name = name,
                address = address,
                channelNumber = parts[1]
            )
        }
    }
}

data class EvenG1DevicePair(
    var leftDevice: EvenG1Device? = null,
    var rightDevice: EvenG1Device? = null
) {
    fun isBothConnected(): Boolean = 
        leftDevice?.isConnected == true && rightDevice?.isConnected == true
    
    fun channelNumber(): String? = leftDevice?.channelNumber ?: rightDevice?.channelNumber
}
```

### 3. BLEマネージャー

```kotlin
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.util.UUID

@SuppressLint("MissingPermission")
class EvenG1Manager private constructor() {
    
    companion object {
        private const val TAG = "EvenG1Manager"
        val instance: EvenG1Manager by lazy { EvenG1Manager() }
    }
    
    // コールバック
    interface ConnectionCallback {
        fun onDeviceFound(channelNumber: String, leftName: String, rightName: String)
        fun onConnected(devicePair: EvenG1DevicePair)
        fun onDisconnected()
        fun onConnectionFailed(error: String)
        fun onDataReceived(isLeft: Boolean, data: ByteArray)
    }
    
    private var callback: ConnectionCallback? = null
    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter
        get() = bluetoothManager.adapter
    
    private val discoveredDevices = mutableListOf<EvenG1Device>()
    private var connectedPair: EvenG1DevicePair? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var heartbeatJob: Job? = null
    
    // スキャン設定
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val name = device.name ?: return
            
            val evenDevice = EvenG1Device.fromScanResult(name, device.address) ?: return
            
            // 既に発見済みかチェック
            if (discoveredDevices.any { it.address == device.address }) return
            
            discoveredDevices.add(evenDevice)
            
            // 同じチャンネルの左右デバイスを探す
            val channelNum = evenDevice.channelNumber
            val pairDevices = discoveredDevices.filter { it.channelNumber == channelNum }
            
            if (pairDevices.size >= 2) {
                val left = pairDevices.find { it.isLeft() }
                val right = pairDevices.find { it.isRight() }
                
                if (left != null && right != null) {
                    Handler(Looper.getMainLooper()).post {
                        callback?.onDeviceFound(channelNum, left.name, right.name)
                    }
                }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
        }
    }
    
    // 初期化
    fun initialize(context: Context, callback: ConnectionCallback) {
        this.context = context.applicationContext
        this.callback = callback
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    // スキャン開始
    fun startScan() {
        if (!bluetoothAdapter.isEnabled) {
            callback?.onConnectionFailed("Bluetooth is not enabled")
            return
        }
        discoveredDevices.clear()
        bluetoothAdapter.bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
    }
    
    // スキャン停止
    fun stopScan() {
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }
    
    // 接続
    fun connect(channelNumber: String) {
        val left = discoveredDevices.find { it.channelNumber == channelNumber && it.isLeft() }
        val right = discoveredDevices.find { it.channelNumber == channelNumber && it.isRight() }
        
        if (left == null || right == null) {
            callback?.onConnectionFailed("Device pair not found for channel $channelNumber")
            return
        }
        
        connectedPair = EvenG1DevicePair(left, right)
        
        // 両方に接続
        bluetoothAdapter.getRemoteDevice(left.address)
            .connectGatt(context, false, createGattCallback())
        bluetoothAdapter.getRemoteDevice(right.address)
            .connectGatt(context, false, createGattCallback())
    }
    
    // 切断
    fun disconnect() {
        heartbeatJob?.cancel()
        connectedPair?.leftDevice?.gatt?.disconnect()
        connectedPair?.rightDevice?.gatt?.disconnect()
        connectedPair?.leftDevice?.gatt?.close()
        connectedPair?.rightDevice?.gatt?.close()
        connectedPair = null
    }
    
    // 接続状態確認
    fun isConnected(): Boolean = connectedPair?.isBothConnected() == true
    
    // データ送信（左右両方）
    fun sendData(data: ByteArray): Boolean {
        val pair = connectedPair ?: return false
        val leftResult = pair.leftDevice?.sendData(data) ?: false
        val rightResult = pair.rightDevice?.sendData(data) ?: false
        return leftResult && rightResult
    }
    
    // 左のみに送信
    fun sendToLeft(data: ByteArray): Boolean = connectedPair?.leftDevice?.sendData(data) ?: false
    
    // 右のみに送信
    fun sendToRight(data: ByteArray): Boolean = connectedPair?.rightDevice?.sendData(data) ?: false
    
    // ハートビート開始
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = mainScope.launch {
            while (isActive) {
                delay(EvenG1Constants.HEARTBEAT_INTERVAL_MS)
                if (isConnected()) {
                    EvenG1Protocol.sendHeartbeat(this@EvenG1Manager)
                }
            }
        }
    }
    
    // GATT Callback
    private fun createGattCallback() = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        gatt?.discoverServices()
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            callback?.onConnectionFailed("Connection failed: $status")
                        }
                    }
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    updateDeviceState(gatt, false)
                    if (connectedPair?.isBothConnected() != true) {
                        Handler(Looper.getMainLooper()).post {
                            callback?.onDisconnected()
                        }
                    }
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            
            val service = gatt?.getService(UUID.fromString(EvenG1Constants.SERVICE_UUID)) ?: return
            val readChar = service.getCharacteristic(UUID.fromString(EvenG1Constants.READ_CHARACTERISTIC_UUID))
            val writeChar = service.getCharacteristic(UUID.fromString(EvenG1Constants.WRITE_CHARACTERISTIC_UUID))
            
            if (readChar == null || writeChar == null) return
            
            // 通知を有効化
            gatt.setCharacteristicNotification(readChar, true)
            val descriptor = readChar.getDescriptor(UUID.fromString(EvenG1Constants.CCCD_UUID))
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }
            
            // MTUを要求（大きいパケット用）
            gatt.requestMtu(251)
            
            // デバイス状態を更新
            updateDeviceState(gatt, true, writeChar)
            
            // 両方接続完了したら通知
            if (connectedPair?.isBothConnected() == true) {
                startHeartbeat()
                Handler(Looper.getMainLooper()).post {
                    callback?.onConnected(connectedPair!!)
                }
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val isLeft = gatt.device.address == connectedPair?.leftDevice?.address
            Handler(Looper.getMainLooper()).post {
                callback?.onDataReceived(isLeft, value)
            }
        }
    }
    
    private fun updateDeviceState(
        gatt: BluetoothGatt?,
        connected: Boolean,
        writeChar: BluetoothGattCharacteristic? = null
    ) {
        connectedPair?.let { pair ->
            when (gatt?.device?.address) {
                pair.leftDevice?.address -> {
                    pair.leftDevice?.gatt = if (connected) gatt else null
                    pair.leftDevice?.isConnected = connected
                    if (writeChar != null) pair.leftDevice?.writeCharacteristic = writeChar
                }
                pair.rightDevice?.address -> {
                    pair.rightDevice?.gatt = if (connected) gatt else null
                    pair.rightDevice?.isConnected = connected
                    if (writeChar != null) pair.rightDevice?.writeCharacteristic = writeChar
                }
            }
        }
    }
}
```

---

## プロトコル実装

### EvenG1Protocol.kt

```kotlin
import java.nio.ByteBuffer
import java.nio.ByteOrder

object EvenG1Protocol {
    
    private var heartbeatSeq = 0
    private var evenAISeq = 0
    
    // ========================
    // コマンド定義
    // ========================
    
    object Commands {
        const val HEARTBEAT = 0x25
        const val EXIT_TO_DASHBOARD = 0x18
        const val EVEN_AI_DATA = 0x4E
        const val NOTIFICATION = 0x4B
        const val WHITELIST = 0x04
        const val MIC_CONTROL = 0x0E
        const val BRIGHTNESS = 0x01
        const val HEAD_UP_ANGLE = 0x0B
        const val DISPLAY_SETTINGS = 0x26
        const val DASHBOARD_CONFIG = 0x06
        const val GET_DISPLAY_SETTINGS = 0x3B
        const val GET_HEAD_UP_ANGLE = 0x32
    }
    
    // ========================
    // ハートビート
    // ========================
    
    fun sendHeartbeat(manager: EvenG1Manager): Boolean {
        val length = 6
        val data = byteArrayOf(
            Commands.HEARTBEAT.toByte(),
            (length and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte(),
            (heartbeatSeq % 0xFF).toByte(),
            0x04,
            (heartbeatSeq % 0xFF).toByte()
        )
        heartbeatSeq++
        return manager.sendData(data)
    }
    
    // ========================
    // テキスト表示（AI/テレプロンプター用）
    // ========================
    
    /**
     * テキストをグラスに表示する
     * 
     * @param manager BLEマネージャー
     * @param text 表示するテキスト
     * @param newScreen 新しい画面かどうか (1=新規, 0=追加)
     * @param currentPage 現在のページ番号
     * @param maxPage 最大ページ番号
     */
    suspend fun sendText(
        manager: EvenG1Manager,
        text: String,
        newScreen: Int = 1,
        currentPage: Int = 1,
        maxPage: Int = 1
    ): Boolean {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val syncSeq = (evenAISeq++ and 0xFF)
        
        // パケットを分割して送信
        val packets = createTextPackets(
            command = Commands.EVEN_AI_DATA,
            data = textBytes,
            syncSeq = syncSeq,
            newScreen = newScreen,
            position = 0,
            currentPage = currentPage,
            maxPage = maxPage
        )
        
        // 左右両方に送信
        for (packet in packets) {
            if (!manager.sendToLeft(packet)) return false
            kotlinx.coroutines.delay(50)  // パケット間の遅延
        }
        
        for (packet in packets) {
            if (!manager.sendToRight(packet)) return false
            kotlinx.coroutines.delay(50)
        }
        
        return true
    }
    
    private fun createTextPackets(
        command: Int,
        data: ByteArray,
        syncSeq: Int,
        newScreen: Int,
        position: Int,
        currentPage: Int,
        maxPage: Int,
        maxLen: Int = 191
    ): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        var maxSeqNum = data.size / maxLen
        if (data.size % maxLen > 0) maxSeqNum++
        
        for (seq in 0 until maxSeqNum) {
            val start = seq * maxLen
            val end = minOf(start + maxLen, data.size)
            val chunk = data.copyOfRange(start, end)
            
            // ヘッダー: [cmd, syncSeq, maxSeq, seq, newScreen, pos(2bytes), currentPage, maxPage]
            val header = ByteBuffer.allocate(9).apply {
                order(ByteOrder.BIG_ENDIAN)
                put(command.toByte())
                put(syncSeq.toByte())
                put(maxSeqNum.toByte())
                put(seq.toByte())
                put(newScreen.toByte())
                putShort(position.toShort())
                put(currentPage.toByte())
                put(maxPage.toByte())
            }.array()
            
            val packet = header + chunk
            packets.add(packet)
        }
        
        return packets
    }
    
    // ========================
    // 通知送信
    // ========================
    
    /**
     * 通知をグラスに送信する
     * 
     * @param manager BLEマネージャー
     * @param appName アプリ名
     * @param title 通知タイトル
     * @param body 通知本文
     */
    suspend fun sendNotification(
        manager: EvenG1Manager,
        appName: String,
        title: String,
        body: String,
        notifyId: Int = 1
    ): Boolean {
        val jsonData = """
            {
                "ncs_notification": {
                    "msg_id": $notifyId,
                    "app_identifier": "$appName",
                    "title": "$title",
                    "subtitle": "",
                    "message": "$body",
                    "display_name": "$appName",
                    "positive_action_label": "",
                    "negative_action_label": ""
                }
            }
        """.trimIndent()
        
        val packets = createNotifyPackets(
            command = Commands.NOTIFICATION,
            msgId = notifyId,
            data = jsonData.toByteArray(Charsets.UTF_8)
        )
        
        for (packet in packets) {
            if (!manager.sendData(packet)) return false
            kotlinx.coroutines.delay(50)
        }
        
        return true
    }
    
    private fun createNotifyPackets(
        command: Int,
        msgId: Int,
        data: ByteArray,
        maxLen: Int = 176
    ): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        var maxSeqNum = data.size / maxLen
        if (data.size % maxLen > 0) maxSeqNum++
        
        for (seq in 0 until maxSeqNum) {
            val start = seq * maxLen
            val end = minOf(start + maxLen, data.size)
            val chunk = data.copyOfRange(start, end)
            
            // ヘッダー: [cmd, msgId, maxSeq, seq]
            val header = byteArrayOf(
                command.toByte(),
                msgId.toByte(),
                maxSeqNum.toByte(),
                seq.toByte()
            )
            
            val packet = header + chunk
            packets.add(packet)
        }
        
        return packets
    }
    
    // ========================
    // ダッシュボードに戻る
    // ========================
    
    fun exitToDashboard(manager: EvenG1Manager): Boolean {
        val data = byteArrayOf(Commands.EXIT_TO_DASHBOARD.toByte())
        return manager.sendData(data)
    }
    
    // ========================
    // 明るさ設定
    // ========================
    
    /**
     * 明るさを設定する（右デバイスのみ）
     * 
     * @param manager BLEマネージャー
     * @param brightness 明るさ (0-42)
     * @param autoBrightness 自動明るさ調整
     */
    fun setBrightness(
        manager: EvenG1Manager,
        brightness: Int,
        autoBrightness: Boolean
    ): Boolean {
        if (brightness < 0 || brightness > 0x2A) return false
        
        val data = byteArrayOf(
            Commands.BRIGHTNESS.toByte(),
            (brightness and 0xFF).toByte(),
            if (autoBrightness) 0x01 else 0x00
        )
        
        return manager.sendToRight(data)
    }
    
    // ========================
    // 時刻・天気設定
    // ========================
    
    private var timeWeatherSeq = 0
    
    /**
     * 時刻と天気を設定する
     * 
     * @param manager BLEマネージャー
     * @param weatherIconId 天気アイコンID (0x00-0x10)
     * @param temperature 温度 (-128 to 127)
     * @param useFahrenheit 華氏を使用するか
     * @param use12HourFormat 12時間表記を使用するか
     */
    fun setTimeAndWeather(
        manager: EvenG1Manager,
        weatherIconId: Int,
        temperature: Int,
        useFahrenheit: Boolean = false,
        use12HourFormat: Boolean = false
    ): Boolean {
        if (weatherIconId < 0 || weatherIconId > 0x10) return false
        if (temperature < -128 || temperature > 127) return false
        
        val now = java.time.LocalDateTime.now()
        val utcEquivalent = java.time.ZonedDateTime.of(
            now, java.time.ZoneOffset.UTC
        )
        val timeMs = utcEquivalent.toInstant().toEpochMilli()
        val timeSec = timeMs / 1000
        
        val seq = (timeWeatherSeq++ and 0xFF)
        
        val buffer = ByteBuffer.allocate(21).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(Commands.DASHBOARD_CONFIG.toByte())  // 0x06
            put(0x15)  // Length: 21 bytes
            put(0x00)  // Padding
            put(seq.toByte())  // Sequence
            put(0x01)  // Subcommand: SET_TIME_AND_WEATHER
            putInt(timeSec.toInt())  // Time 32-bit
            putLong(timeMs)  // Time 64-bit
            put(weatherIconId.toByte())  // Weather icon
            put(temperature.toByte())  // Temperature
            put(if (useFahrenheit) 0x01 else 0x00)  // C/F
            put(if (use12HourFormat) 0x01 else 0x00)  // 12h/24h
        }
        
        return manager.sendData(buffer.array())
    }
    
    // ========================
    // 天気アイコンID一覧
    // ========================
    
    object WeatherIcons {
        const val SUNNY = 0x00
        const val CLOUDY = 0x01
        const val PARTLY_CLOUDY = 0x02
        const val RAINY = 0x03
        const val HEAVY_RAIN = 0x04
        const val THUNDERSTORM = 0x05
        const val SNOWY = 0x06
        const val FOGGY = 0x07
        const val WINDY = 0x08
        const val HAZY = 0x09
        const val SLEET = 0x0A
        const val UNKNOWN = 0x10
    }
}
```

---

## 使用例

### Activity での使用

```kotlin
class MainActivity : AppCompatActivity(), EvenG1Manager.ConnectionCallback {
    
    private val manager = EvenG1Manager.instance
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初期化
        manager.initialize(this, this)
        
        // パーミッション取得後にスキャン開始
        requestPermissions()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        manager.disconnect()
    }
    
    // ===== ConnectionCallback 実装 =====
    
    override fun onDeviceFound(channelNumber: String, leftName: String, rightName: String) {
        Log.d("MainActivity", "Found G1 pair: $channelNumber")
        // UIに表示するなど
        runOnUiThread {
            // 発見したデバイスをリストに追加
        }
    }
    
    override fun onConnected(devicePair: EvenG1DevicePair) {
        Log.d("MainActivity", "Connected to G1!")
        runOnUiThread {
            Toast.makeText(this, "EVEN G1に接続しました", Toast.LENGTH_SHORT).show()
        }
        
        // 接続後にテキストを送信
        lifecycleScope.launch {
            EvenG1Protocol.sendText(
                manager = manager,
                text = "Hello from Android!",
                newScreen = 1,
                currentPage = 1,
                maxPage = 1
            )
        }
    }
    
    override fun onDisconnected() {
        Log.d("MainActivity", "Disconnected from G1")
        runOnUiThread {
            Toast.makeText(this, "切断されました", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onConnectionFailed(error: String) {
        Log.e("MainActivity", "Connection failed: $error")
    }
    
    override fun onDataReceived(isLeft: Boolean, data: ByteArray) {
        // グラスからのデータを受信
        val side = if (isLeft) "Left" else "Right"
        Log.d("MainActivity", "Received from $side: ${data.toHexString()}")
        
        // コマンド解析
        if (data.isNotEmpty()) {
            when (data[0].toInt() and 0xFF) {
                0xF5 -> handleGlassesEvent(data)
                0xF1 -> handleMicData(data)
            }
        }
    }
    
    private fun handleGlassesEvent(data: ByteArray) {
        if (data.size < 2) return
        when (data[1].toInt() and 0xFF) {
            0x00 -> Log.d("MainActivity", "Exit to dashboard")
            0x01 -> Log.d("MainActivity", "Touchpad tap")
            0x17 -> Log.d("MainActivity", "Even AI start requested")
            0x18 -> Log.d("MainActivity", "Even AI stop requested")
        }
    }
    
    private fun handleMicData(data: ByteArray) {
        // マイクデータの処理（LC3デコードが必要）
    }
    
    // ===== ボタンアクション例 =====
    
    fun onScanButtonClick(view: View) {
        manager.startScan()
    }
    
    fun onConnectButtonClick(channelNumber: String) {
        manager.stopScan()
        manager.connect(channelNumber)
    }
    
    fun onSendTextButtonClick(view: View) {
        lifecycleScope.launch {
            EvenG1Protocol.sendText(
                manager = manager,
                text = "これはテストメッセージです。\nEVEN G1に表示されます。",
                newScreen = 1
            )
        }
    }
    
    fun onSendNotificationButtonClick(view: View) {
        lifecycleScope.launch {
            EvenG1Protocol.sendNotification(
                manager = manager,
                appName = "MyApp",
                title = "新着通知",
                body = "これは通知のテストです"
            )
        }
    }
}

// ByteArray拡張
fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
```

---

## 表示仕様

### 画面制限

EVEN G1のディスプレイには以下の制限があります：

```
┌─────────────────────────────────────────────────┐
│ 画面幅: 488ピクセル相当                          │
│ フォントサイズ: 21（推奨）                        │
│                                                 │
│ 1行目のテキスト                                  │
│ 2行目のテキスト                                  │
│ 3行目のテキスト                                  │
│ 4行目のテキスト                                  │
│ 5行目のテキスト  ← 最大5行/画面                  │
└─────────────────────────────────────────────────┘
```

| 項目 | 値 |
|------|-----|
| 画面幅 | 488ピクセル相当 |
| 推奨フォントサイズ | 21 |
| 1画面の最大行数 | 5行 |
| 1行の文字数目安（日本語） | 約20-25文字 |
| 1行の文字数目安（英語） | 約40-50文字 |

### ダッシュボードモード（3種類）

グラスのダッシュボード表示モードを切り替えることができます：

| モード | ID | 説明 |
|--------|-----|------|
| **Full** | `0x00` | フル表示（デフォルト） |
| **Dual** | `0x01` | デュアルペイン（左右分離表示） |
| **Minimal** | `0x02` | ミニマル表示 |

```kotlin
object DashboardMode {
    const val FULL = 0x00
    const val DUAL = 0x01
    const val MINIMAL = 0x02
}

/**
 * ダッシュボードモードを設定する
 * 
 * @param manager BLEマネージャー
 * @param modeId モードID (0x00=Full, 0x01=Dual, 0x02=Minimal)
 * @param secondaryPaneId Dualモード時のサブペインID (0x00-0x05)
 */
fun setDashboardMode(
    manager: EvenG1Manager,
    modeId: Int,
    secondaryPaneId: Int = 0x00
): Boolean {
    if (modeId < 0 || modeId > 0x02) return false
    if (secondaryPaneId < 0 || secondaryPaneId > 0x05) return false
    
    val seq = (dashboardModeSeq++ and 0xFF)
    
    val data = byteArrayOf(
        0x06,                        // Command: DASHBOARD_CONFIG
        0x07,                        // Length: 7 bytes
        0x00,                        // Padding
        seq.toByte(),                // Sequence
        0x06,                        // Subcommand: SET_DASHBOARD_MODE
        modeId.toByte(),             // Mode ID
        secondaryPaneId.toByte()     // Secondary Pane ID
    )
    
    return manager.sendData(data)
}

private var dashboardModeSeq = 0
```

### newScreen フラグの詳細

テキスト送信時の `newScreen` パラメータは、表示方法を制御します：

```kotlin
object NewScreenFlags {
    // タイプ
    const val TYPE_NEW = 0x01      // 新規画面
    const val TYPE_APPEND = 0x00   // 既存画面に追加
    
    // ステータス
    const val STATUS_TEXT = 0x70   // テキストモード
    const val STATUS_AI = 0x31     // AIアシスタントステータス
    
    // 組み合わせ例
    const val NEW_TEXT_SCREEN = 0x71  // TYPE_NEW | STATUS_TEXT
    const val APPEND_TEXT = 0x70      // TYPE_APPEND | STATUS_TEXT
}

fun calculateNewScreen(type: Int, status: Int): Int {
    return status or type  // ビットOR演算
}
```

| newScreen値 | 意味 |
|-------------|------|
| `0x71` | 新規テキスト画面を表示 |
| `0x70` | 既存画面にテキストを追加 |
| `0x31` | AIステータス表示 |

### テキスト分割の実装

長いテキストを送信する際は、画面に収まるよう分割する必要があります：

```kotlin
/**
 * テキストを表示可能な行に分割する
 * 
 * @param text 元のテキスト
 * @param maxWidthPx 1行の最大幅（デフォルト: 488）
 * @param fontSize フォントサイズ（デフォルト: 21）
 * @return 行のリスト
 */
fun splitTextIntoLines(
    text: String,
    maxWidthPx: Float = 488f,
    fontSize: Float = 21f
): List<String> {
    val paint = android.graphics.Paint().apply {
        textSize = fontSize
    }
    
    val lines = mutableListOf<String>()
    
    // 段落ごとに処理
    text.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { paragraph ->
            var remaining = paragraph
            while (remaining.isNotEmpty()) {
                // 1行に収まる文字数を計算
                val count = paint.breakText(remaining, true, maxWidthPx, null)
                if (count <= 0) break
                
                lines.add(remaining.substring(0, count).trim())
                remaining = remaining.substring(count)
            }
        }
    
    return lines
}

/**
 * 行リストをページに分割する（5行/ページ）
 * 
 * @param lines 行のリスト
 * @return ページのリスト（各ページは最大5行）
 */
fun splitIntoPages(lines: List<String>): List<List<String>> {
    return lines.chunked(5)
}

/**
 * 総ページ数を計算
 */
fun calculateTotalPages(lines: List<String>): Int {
    return (lines.size + 4) / 5  // 切り上げ
}

/**
 * 現在のページ番号を計算（1始まり）
 */
fun calculateCurrentPage(currentLineIndex: Int): Int {
    return (currentLineIndex / 5) + 1
}
```

### ページ送信の実装例

```kotlin
class TextPaginator(private val manager: EvenG1Manager) {
    private var lines: List<String> = emptyList()
    private var currentLineIndex = 0
    
    fun loadText(text: String) {
        lines = splitTextIntoLines(text)
        currentLineIndex = 0
    }
    
    suspend fun sendCurrentPage(): Boolean {
        if (lines.isEmpty()) return false
        
        val pageLines = lines.drop(currentLineIndex).take(5)
        val pageText = pageLines.joinToString("\n") + "\n"
        
        return EvenG1Protocol.sendText(
            manager = manager,
            text = pageText,
            newScreen = NewScreenFlags.NEW_TEXT_SCREEN,
            currentPage = calculateCurrentPage(currentLineIndex),
            maxPage = calculateTotalPages(lines)
        )
    }
    
    suspend fun nextPage(): Boolean {
        if (currentLineIndex + 5 >= lines.size) return false
        currentLineIndex += 5
        return sendCurrentPage()
    }
    
    suspend fun previousPage(): Boolean {
        if (currentLineIndex < 5) return false
        currentLineIndex -= 5
        return sendCurrentPage()
    }
    
    val totalPages: Int get() = calculateTotalPages(lines)
    val currentPage: Int get() = calculateCurrentPage(currentLineIndex)
    val hasNextPage: Boolean get() = currentLineIndex + 5 < lines.size
    val hasPreviousPage: Boolean get() = currentLineIndex >= 5
}
```

### 実装時の注意点

| 注意点 | 説明 |
|--------|------|
| **日本語テキスト** | 1行約20-25文字が目安。全角文字は幅が広い |
| **英語テキスト** | 1行約40-50文字が目安 |
| **改行の扱い** | 各行の末尾に `\n` を付けて送信 |
| **ページ分割** | 5行を超えるテキストは必ずページ分割 |
| **送信間隔** | パケット間に50ms程度の遅延を入れる |
| **Dualモード** | 左右で別コンテンツを表示可能 |

---

## トラブルシューティング

### よくある問題

| 問題 | 原因 | 解決策 |
|------|------|--------|
| スキャンでデバイスが見つからない | パーミッション不足 | 位置情報パーミッションを確認 |
| 接続が切れる | ハートビートなし | 15秒間隔でハートビートを送信 |
| テキストが表示されない | パケット分割エラー | パケット間に50ms程度の遅延を入れる |
| 片方しか接続できない | 左右両方への接続が必要 | 必ず左右ペアで接続 |
| connectGatt後にコールバックが来ない | 他アプリが接続中 | 公式アプリを強制停止 |
| status=139でdisconnect | 左右同時接続の競合 | 左→右の順次接続に変更 |

### 🔴 重要: 順次接続パターン

**EVEN G1は左右同時接続に対応していません。** 必ず以下の順序で接続してください：

```
1. LEFT デバイスに connectGatt()
2. LEFT の onServicesDiscovered() を待つ
3. LEFT 接続完了後、500ms待機
4. RIGHT デバイスに connectGatt()
5. RIGHT の onServicesDiscovered() を待つ
6. 両方接続完了 → 成功コールバック
```

**悪い例（同時接続 - 失敗する）:**
```kotlin
// ❌ これは動作しません
leftDevice.connectGatt(...)
rightDevice.connectGatt(...)  // 左右同時に接続しようとすると失敗
```

**良い例（順次接続 - 成功する）:**
```kotlin
// ✅ 正しいパターン
leftDevice.connectGatt(context, false, object : BluetoothGattCallback() {
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        // LEFT接続完了後にRIGHTを接続
        handler.postDelayed({
            rightDevice.connectGatt(...)
        }, 500)
    }
})
```

### 公式アプリとの競合

EVEN G1の公式アプリがバックグラウンドで動作していると、BLE接続が占有されてしまいます。

**症状:**
- `connectGatt()` は成功するが `onConnectionStateChange()` が呼ばれない
- `status=139` (GATT_ERROR) でdisconnectされる

**解決策:**
1. **公式アプリを強制停止**: 設定 → アプリ → EVEN G1 → 強制停止
2. **Bluetoothを再起動**: Bluetooth OFF → ON
3. **EVEN G1を再起動**: ケースに入れて5秒待ち、取り出す
4. **スマホを再起動**: 最も確実な方法

### ペアリングの問題

初回接続時に「ペア設定しますか？」ダイアログが表示されます。

- **必ず「はい」を選択してください**
- 左右両方のデバイスに対してペアリングが必要です
- ペアリングが完了していない場合、接続がタイムアウトします

### デバイス名パターン

EVEN G1のデバイス名は以下の形式です：

```
Even G1_{チャンネル番号}_{L/R}_{デバイスID}

例:
- Even G1_19_L_6DDD88  (左側、チャンネル19)
- Even G1_19_R_80314A  (右側、チャンネル19)
```

正規表現パターン: `Even G1_\d+_[LR]_\w+`

### デバッグTIPS

```bash
# BLEログを詳細に出力
adb shell setprop log.tag.BluetoothGatt VERBOSE
adb shell setprop log.tag.BluetoothAdapter VERBOSE

# アプリのログを確認
adb logcat | grep -E "EvenG1|BluetoothGatt"
```

---

## 8. 応用: バックグラウンド制御とメディアボタン連携

ARグラスの操作は、スマートフォンをポケットに入れた状態（バックグラウンド）で行えると非常に便利です。
ここでは、Androidの**メディアセッション（MediaSession）**と**Bluetoothメディアリモコン**（またはイヤホンのボタン）を利用して、バックグラウンドからAR表示を制御する高度なテクニックを紹介します。

### 仕組みの概要

1.  **無音再生 (`SilentAudioPlayer`)**:
    *   Android OSは「現在音を出しているアプリ」を優先的にメディアボタンの送信先に選びます。
    *   ごく短い無音のオーディオトラックをループ再生し、`AudioFocus`を取得し続けることで、他の音楽アプリ（Spotify等）に割り込まれることなくボタンイベントを独占します。
2.  **メディアセッション (`MediaSessionCompat`)**:
    *   `KEYCODE_MEDIA_NEXT` (次へ) や `KEYCODE_MEDIA_PREVIOUS` (前へ) といったシステムイベントをフックします。
    *   これらのイベントをアプリケーション内のロジック（ARページ送りなど）に転送します。

### 実装例

#### 1. SilentAudioPlayer (無音再生)

```java
public class SilentAudioPlayer {
    private AudioTrack mAudioTrack;
    private volatile boolean mIsPlaying = false;

    public void play() {
        // 1. AudioFocusを要求（重要）
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        // 2. 無音データの生成と再生
        int bufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(..., AudioTrack.MODE_STREAM);
        mAudioTrack.play();
        
        // 3. 別スレッドで無音(0)を書き込み続ける
        new Thread(() -> {
            byte[] silence = new byte[bufferSize];
            while (mIsPlaying) {
                mAudioTrack.write(silence, 0, silence.length);
            }
        }).start();
    }
}
```

#### 2. MediaControlManager (イベント監視)

```java
public class MediaControlManager {
    private MediaSessionCompat mMediaSession;

    public void start(Callback callback) {
        // 1. セッション作成
        mMediaSession = new MediaSessionCompat(context, "AR_Control_Session");
        
        // 2. コールバックの設定
        mMediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onSkipToNext() {
                // "次へ"ボタン押下時の処理
                callback.onNextPage();
            }

            @Override
            public void onSkipToPrevious() {
                // "前へ"ボタン押下時の処理
                callback.onPrevPage();
            }
        });

        // 3. セッション有効化
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | 
                               MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setActive(true);
        
        // 4. 無音再生開始（OSに認識させるため）
        silentAudioPlayer.play();
    }
}
```

この構成により、ユーザーはスマホ画面を見ることなく、手元のBluetoothリモコンだけでARグラス上の情報を操作可能になります。
```

---

## ライセンス

このドキュメントのコード例は、EVEN G1との互換性を持つアプリケーション開発に自由に使用できます。

---

最終更新: 2026-01-08 (順次接続パターン追記)
