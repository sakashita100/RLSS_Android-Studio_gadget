package com.example.myapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bluetoothmqtt.bluetooth.BluetoothManager
import com.example.myapp.manager.BluetoothPolarManager
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch





class MainActivity : ComponentActivity() {
    private val bluetoothManagerEnv = BluetoothManager()
    private val bluetoothManagerServo = BluetoothManager()
    private lateinit var mqttClient: MqttClient

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val brokerUrl = "tcp://broker.emqx.io:1883"
        val clientId = "android-client-${System.currentTimeMillis()}"
        mqttClient = MqttClient(brokerUrl, clientId, null)

        val mqttOptions = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
        }


        try {
            mqttClient.connect(mqttOptions)
            Log.d("MQTT", "Connected to broker")
        } catch (e: Exception) {
            Log.e("MQTT", "MQTT connect failed: ${e.message}")
        }



        setContent {
            val context = LocalContext.current
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

            var selectedTabIndex by remember { mutableStateOf(0) }
            val tabTitles = listOf("🎈ハグ送信", "📡ハグ受信","💕心拍発信","💓心拍受信")

            var receivedMessage by remember { mutableStateOf("メッセージ待機中...") }
            var sensorData by remember { mutableStateOf("データ受信待ち...") }
            var heartRate by remember { mutableStateOf("未取得") }

            var pairedDevices by remember { mutableStateOf(setOf<BluetoothDevice>()) }
            var selectedEnv by remember { mutableStateOf<BluetoothDevice?>(null) }
            val coroutineScope = rememberCoroutineScope()

            var selectedPolar by remember { mutableStateOf<BluetoothDevice?>(null) }
            val polarManager = remember { BluetoothPolarManager() }
            var heartBpm by remember { mutableStateOf(0) }
            var playingJob by remember { mutableStateOf<Job?>(null) }
            val mediaPlayer = remember {
                MediaPlayer.create(context, R.raw.dokudoku)
            }

            //var currentBpm by remember { mutableStateOf(0) }

            // 1分間の心拍数を記録するためのリスト
            val bpmList = remember { mutableStateListOf<Int>() }
            // 心拍記録中かどうかを管理する状態
            var isRecording by remember { mutableStateOf(false) }
            // 1分ごとにMQTT送信を行うコルーチンのジョブ
            var recordingJob by remember { mutableStateOf<Job?>(null) }

            var polarBattery by remember { mutableStateOf("不明") }

            var lastBeatTime = System.currentTimeMillis()
            var currentBpm by mutableStateOf(70)
            var publishJob by remember { mutableStateOf<Job?>(null) }





            DisposableEffect(Unit) {
                onDispose {
                    bluetoothManagerEnv.disconnect()
                    bluetoothManagerServo.disconnect()
                    if (mqttClient.isConnected) mqttClient.disconnect()
                    mediaPlayer.release() // MediaPlayerを正しく解放
                    recordingJob?.cancel() // アプリ終了時に記録ジョブをキャンセル
                    polarManager.stop()    // Polar接続も停止
                }
            }

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFFFC1CC),
                    onPrimary = Color.White,
                    secondary = Color(0xFFB2EBF2),
                    onSecondary = Color.Black
                )
            ) {
                Scaffold(
                    topBar = {
                        Column {
                            TopAppBar(
                                title = { Text("❤ 遠距離恋愛支援システム ❤") },
                                colors = TopAppBarDefaults.smallTopAppBarColors(
                                    containerColor = Color(0xFFFFE4E1)
                                )
                            )
                            TabRow(selectedTabIndex = selectedTabIndex, containerColor = Color(0xFFFFF0F5)) {
                                tabTitles.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTabIndex == index,
                                        onClick = { selectedTabIndex = index },
                                        text = { Text(title) }
                                    )
                                }
                            }
                        }
                    },
                    content = { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (selectedTabIndex) {
                                0 -> {
                                    // (このタブのロジックは変更ありません)
                                    Button(
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(
                                                    context,
                                                    android.Manifest.permission.BLUETOOTH_CONNECT
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
                                            } else {
                                                ActivityCompat.requestPermissions(
                                                    this@MainActivity,
                                                    arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                                                    1
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC1CC))
                                    ) {
                                        Text("ペアリング済みデバイスを表示")
                                    }

                                    if (pairedDevices.isNotEmpty()) {
                                        //Text("📱 M5StickC-ENV を選択：")
                                        Text("📱 接続したいBluetoothを選択：")
                                        pairedDevices.forEach { device ->
                                            if (device.name?.contains("M5StickC-ENV") == true) {
                                                Text(
                                                    text = "• ${device.name}",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { selectedEnv = device }
                                                        .padding(4.dp)
                                                )
                                            }
                                        }

                                        selectedEnv?.let { device ->
                                            Button(
                                                onClick = {
                                                    Toast.makeText(context, "${device.name} に接続中...", Toast.LENGTH_SHORT).show()
                                                    try {
                                                        bluetoothManagerEnv.connectToDevice(device)
                                                        bluetoothManagerEnv.startListening { data ->
                                                            sensorData = data
                                                            Log.d("BluetoothData", "受信: $data")
                                                            try {
                                                                val json = JSONObject(data)
                                                                val value = json.getString("key")
                                                                if (value == "hug") {
                                                                    coroutineScope.launch {
                                                                        mqttClient.safePublish("m5stack/sensor", data)
                                                                        withContext(Dispatchers.Main) {
                                                                            Toast.makeText(context, "MQTT送信: $data", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    }
                                                                }
                                                            } catch (e: Exception) {
                                                                Log.e("Bluetooth", "JSON解析失敗: ${e.message}")
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "接続失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB2EBF2))
                                            ) {
                                                Text("🔌 このデバイスに接続")
                                            }
                                        }
                                    }
                                    Divider()
                                    Text("📊 受信データ: $sensorData")
                                }

                                1 -> {
                                    Button(
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(
                                                    context,
                                                    android.Manifest.permission.BLUETOOTH_CONNECT
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
                                                val servoDevice = pairedDevices.find { it.name?.contains("M5StickC-servo") == true }

                                                servoDevice?.let {
                                                    try {
                                                        bluetoothManagerServo.connectToDevice(it)
                                                        Toast.makeText(context, "${it.name} に接続", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "接続失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                } ?: run {
                                                    Toast.makeText(context, "M5StickC-servo が見つかりません", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                ActivityCompat.requestPermissions(
                                                    this@MainActivity,
                                                    arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                                                    1
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA9A825))
                                    ) {
                                        Text("🔧 M5StickC-servo に接続")
                                    }

                                    Button(
                                        onClick = {
                                            try {
                                                if (!mqttClient.isConnected) mqttClient.reconnect()
                                                mqttClient.subscribe("m5stack/sensor", 2)

                                                mqttClient.setCallback(object : MqttCallback {
                                                    override fun connectionLost(cause: Throwable?) {
                                                        Log.w("MQTT", "Connection lost: ${cause?.message}")
                                                    }

                                                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                                                        val msg = message?.toString() ?: ""
                                                        receivedMessage = "受信トピック: $topic\n内容: $msg"
                                                        Log.d("MQTT", receivedMessage)

                                                        try {
                                                            val json = JSONObject(msg)

                                                            // 条件: {"key":"hug"}
                                                            if (json.has("key") && json.getString("key") == "hug") {
                                                                if (bluetoothManagerServo.isConnected()) {
                                                                    bluetoothManagerServo.sendMessage(json.toString())
                                                                    Log.d("Bluetooth", "送信成功: ${json.toString()}")
                                                                } else {
                                                                    Log.d("Bluetooth", "未接続のため送信されませんでした")
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("Bluetooth", "JSON解析エラー: ${e.message}")
                                                        }
                                                    }

                                                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                                                })

                                                Toast.makeText(context, "MQTTサブスクライブ成功", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Log.e("MQTT", "Subscribe failed", e)
                                                Toast.makeText(context, "MQTTサブスクライブ失敗", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC1CC))
                                    ) {
                                        Text("💬 MQTTでサブスクライブ開始")
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("🔌 Bluetooth 接続状態: ${if (bluetoothManagerServo.isConnected()) "接続済み" else "未接続"}")
                                    Divider()
                                    Text("📨 MQTT受信メッセージ:")
                                    Text(receivedMessage)
                                }



                                2 -> { //「💕心拍発信」タブ
                                    Button(onClick = {
                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.BLUETOOTH_CONNECT
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
                                        } else {
                                            ActivityCompat.requestPermissions(
                                                this@MainActivity,
                                                arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                                                1
                                            )
                                        }
                                    }) {
                                        Text("ペアリング済みデバイスを表示")
                                    }

                                    if (pairedDevices.isNotEmpty()) {
                                        //Text("💓 Polar H10/Verity を選択：")
                                        Text("💓 接続したいBluetoothを選択：")
                                        pairedDevices.forEach { device ->
                                            if (device.name?.contains("Polar") == true) {
                                                Text(
                                                    text = "• ${device.name}",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { selectedPolar = device }
                                                        .padding(4.dp)
                                                )
                                            }
                                        }



                                        selectedPolar?.let { device ->
                                            Row {
                                                Button(
                                                    onClick = {
                                                        if (!isRecording) {
                                                            isRecording = true
                                                            Toast.makeText(context, "心拍取得と1分ごとの送信を開始します", Toast.LENGTH_SHORT).show()

                                                            // Polarデバイスに接続して心拍データを受信開始
                                                            try {
                                                                polarManager.connect(context, device) { bpm ->
                                                                    // UIにはリアルタイムで心拍数を表示
                                                                    heartRate = "$bpm bpm"
                                                                    // リストに心拍数を追加
                                                                    if (isRecording) {
                                                                        bpmList.add(bpm)
                                                                    }
                                                                    // すぐにMQTT送信（最初の心拍）
                                                                    val json = JSONObject().apply {
                                                                        put("type", "heartRate")
                                                                        put("bpm", bpm)
                                                                    }.toString()
                                                                    mqttClient.safePublish("polar/heart", json)

                                                                }
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "接続失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                isRecording = false // 失敗した場合はフラグを戻す
                                                            }

                                                            // 1分ごとにデータを送信するジョブを開始
                                                            recordingJob?.cancel() // 念のため既存のジョブはキャンセル
                                                            recordingJob = coroutineScope.launch {
                                                                while (isActive) {
                                                                    //delay(60_000L) // 1分(60000ミリ秒)待機
                                                                    delay(5_000L)
                                                                    // リストにデータがあれば処理
                                                                    if (bpmList.isNotEmpty()) {
                                                                        val averageBpm = bpmList.average().toInt()
                                                                        val json = JSONObject().apply {
                                                                            put("type", "heartRate")
                                                                            put("bpm", averageBpm)
                                                                        }.toString()

                                                                        // MQTTで平均心拍数を送信
                                                                        mqttClient.safePublish("polar/heart", json)

                                                                        withContext(Dispatchers.Main) {
                                                                            Toast.makeText(context, "平均心拍送信: $averageBpm bpm", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                        bpmList.clear() // 送信後リストをクリア
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                    enabled = !isRecording // 記録中でない場合のみ有効
                                                ) {
                                                    Text("心拍送信 開始")
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Button(
                                                    onClick = {
                                                        if (isRecording) {
                                                            isRecording = false
                                                            recordingJob?.cancel() // 送信ジョブを停止
                                                            polarManager.stop()    // Polarセンサーとの接続を停止
                                                            bpmList.clear()        // 記録リストをクリア
                                                            heartRate = "未取得"
                                                            Toast.makeText(context, "心拍取得と送信を停止しました", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    enabled = isRecording // 記録中の場合のみ有効
                                                ) {
                                                    Text("心拍送信 停止")
                                                }
                                            }
                                        }
                                    }
                                    Divider()
                                    Text("現在のあなたの心拍: $heartRate")

                                    if (pairedDevices.isNotEmpty()) {
                                        val polarDevice =
                                            pairedDevices.find { it.name?.contains("Polar") == true }
                                        polarDevice?.let { device ->
                                            Button(onClick = {
                                                selectedPolar?.let { device ->
                                                    polarManager.requestBatteryLevel(
                                                        context,
                                                        device
                                                    ) { level ->
                                                        polarBattery = "$level%"
                                                    }
                                                }
                                            }) {
                                                Text("🔋 Polarのバッテリーを取得")
                                            }
                                            Text("🔋 Polarバッテリー残量: $polarBattery")

                                        }
                                    }
                                }
                                3 -> {
                                    val bpmBuffer = remember { mutableStateListOf<Int>() }
                                    var currentBpm by remember { mutableStateOf(70) }
                                    var lastBpm by remember { mutableStateOf(70) }
                                    val initialBpm = 70
                                    val initialPlayCountLimit = 10
                                    var playCount by remember { mutableStateOf(0) }
                                    var isInitialPhase by remember { mutableStateOf(true) }
                                    var isStoppedManually by remember { mutableStateOf(false) }
                                    var playingJob by remember { mutableStateOf<Job?>(null) }
                                    val coroutineScope = rememberCoroutineScope()
                                    val context = LocalContext.current

                                    // 心拍アニメ用変数
                                    var targetScale by remember { mutableStateOf(1f) }
                                    var targetAlpha by remember { mutableStateOf(1f) }

                                    // アニメーション状態
                                    val animatedScale by animateFloatAsState(
                                        targetValue = targetScale,
                                        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                                        label = "HeartScale"
                                    )
                                    val animatedAlpha by animateFloatAsState(
                                        targetValue = targetAlpha,
                                        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                                        label = "HeartAlpha"
                                    )

                                    // 心拍再生ループ
                                    fun startHeartPlaybackLoop(coroutineScope: CoroutineScope, mediaPlayer: MediaPlayer) {
                                        playingJob?.cancel()
                                        playingJob = coroutineScope.launch {
                                            while (isActive) {
                                                val bpmToPlay = if (isInitialPhase) {
                                                    initialBpm
                                                } else if (bpmBuffer.isNotEmpty()) {
                                                    val bpm = bpmBuffer.removeAt(0)
                                                    lastBpm = bpm
                                                    bpm
                                                } else {
                                                    lastBpm
                                                }

                                                val interval = (60000.0 / bpmToPlay).toLong()

                                                try {
                                                    mediaPlayer.seekTo(0)
                                                    mediaPlayer.start()

                                                    // 音と同時にハート鼓動
                                                    targetScale = 1.2f
                                                    targetAlpha = 1.3f
                                                    launch {
                                                        delay(150)
                                                        targetScale = 1f
                                                        targetAlpha = 1f
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("MediaPlayer", "再生失敗: ${e.message}")
                                                }

                                                delay(interval)

                                                if (isInitialPhase) {
                                                    playCount++
                                                    if (playCount >= initialPlayCountLimit) {
                                                        isInitialPhase = false
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // MQTTで心拍受信
                                    Button(onClick = {
                                        try {
                                            if (!mqttClient.isConnected) mqttClient.reconnect()
                                            mqttClient.subscribe("polar/heart", 2)
                                            mqttClient.setCallback(object : MqttCallback {
                                                override fun connectionLost(cause: Throwable?) {
                                                    Log.w("MQTT", "Connection lost: ${cause?.message}")
                                                }

                                                override fun messageArrived(topic: String?, message: MqttMessage?) {
                                                    if (isStoppedManually) return

                                                    val msg = message?.payload?.toString(Charsets.UTF_8)
                                                    Log.d("MQTT", "Received: $msg on topic $topic")

                                                    try {
                                                        val json = JSONObject(msg ?: "")
                                                        if (json.getString("type") == "heartRate") {
                                                            val bpm = json.getInt("bpm")
                                                            currentBpm = bpm
                                                            receivedMessage = "💓 相手の心拍: $bpm bpm"

                                                            bpmBuffer.add(bpm)
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("MQTT", "解析失敗: ${e.message}")
                                                    }
                                                }

                                                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                                            })

                                            isStoppedManually = false
                                            startHeartPlaybackLoop(coroutineScope, mediaPlayer)
                                            Toast.makeText(context, "相手の心拍の受信と再生を開始しました", Toast.LENGTH_SHORT).show()

                                        } catch (e: Exception) {
                                            Log.e("MQTT", "Subscribe failed", e)
                                            Toast.makeText(context, "サブスクライブ失敗", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Text("MQTTで相手の心拍を受信")
                                    }

                                    // 停止ボタン
                                    Button(onClick = {
                                        isStoppedManually = true
                                        playingJob?.cancel()
                                        if (mediaPlayer.isPlaying) {
                                            mediaPlayer.pause()
                                            mediaPlayer.seekTo(0)
                                        }
                                        Toast.makeText(context, "心拍音を停止しました", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("心拍音を停止")
                                    }

                                    // 再開ボタン
                                    Button(onClick = {
                                        if (isStoppedManually) {
                                            isStoppedManually = false
                                            startHeartPlaybackLoop(coroutineScope, mediaPlayer)
                                            Toast.makeText(context, "心拍音を再開しました", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Text("心拍音を再開")
                                    }

                                    // 表示部分
                                    Text("相手の心拍数:")
                                    Text(receivedMessage)

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // ハート表示（色＋拡大縮小）
                                    Image(
                                        painter = painterResource(id = R.drawable.heart),
                                        contentDescription = "Heart",
                                        modifier = Modifier
                                            .size(100.dp)
                                            .scale(animatedScale)
                                            .graphicsLayer {
                                                alpha = animatedAlpha.coerceAtMost(1f) // 明るさの代わりにアルファを使う
                                            }
                                    )
                                }




                            }

                            }




                    }
                )
            }
        }
    }

    private fun BluetoothManager.disconnect() {}

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            val message = if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                "Bluetooth権限が許可されました"
            } else {
                "Bluetooth権限が拒否されました"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun BluetoothManager.sendMessage(string: String) {}

fun MqttClient.safePublish(topic: String, payload: String) {
    try {
        if (!this.isConnected) this.connect()
        val message = MqttMessage(payload.toByteArray()).apply { qos = 2 }
        this.publish(topic, message)
        Log.d("MQTT", "Published to $topic: $payload")
    } catch (e: Exception) {
        Log.e("MQTT", "Publish failed: ${e.message}")
    }
}
