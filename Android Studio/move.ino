//daiji
//sumaho~M5stickcplus

#include <M5StickCPlus.h>
#include <BluetoothSerial.h>
#include <ESP32Servo.h>
#include <ArduinoJson.h>

BluetoothSerial SerialBT;
Servo servo1;
Servo servo2;

String btBuffer = "";
bool isHugPosition = false;
unsigned long hugStartTime = 0; // 抱きつき開始時間

const unsigned long HUG_DURATION = 2000; // 抱きつき持続時間（ミリ秒）

void setup() {
  // サーボ初期化
  servo1.setPeriodHertz(50);
  servo2.setPeriodHertz(50);
  servo1.attach(25);
  servo2.attach(26);

  M5.begin();
  Serial.begin(115200);
  delay(1000); 

  // 画面表示
  M5.Lcd.fillScreen(BLACK);
  M5.Lcd.setTextSize(2);
  M5.Lcd.setCursor(10, 30);
  M5.Lcd.setTextColor(WHITE);
  M5.Lcd.println("waiting...");

  Serial.println("M5StickC initialized");
  SerialBT.begin("M5StickC-servo");
  Serial.println("Bluetooth device is ready to pair");

  // 初期位置は必ず離す（120°）
  servo1.write(120);
  servo2.write(120);
  isHugPosition = false;
  Serial.println("Initial position: 120° (release)");
}

void loop() {
  // Bluetooth接続状態の確認
  static bool clientConnected = false;
  if (SerialBT.hasClient()) {
    if (!clientConnected) {
      Serial.println("Bluetooth client connected");
      clientConnected = true;
    }
  } else {
    if (clientConnected) {
      Serial.println("Bluetooth client disconnected");
      clientConnected = false;
    }
  }

  // データ受信処理
  while (SerialBT.available()) {
    char c = SerialBT.read();
    Serial.print(c);
    btBuffer += c;

    // JSONの終わりを検出
    if (c == '}') {
      Serial.println("\n--- Received JSON ---");
      Serial.println(btBuffer);

      StaticJsonDocument<128> doc;
      DeserializationError error = deserializeJson(doc, btBuffer);

      if (!error) {
        const char* command = doc["key"];
        Serial.print("Command: ");
        Serial.println(command);

        if (strcmp(command, "hug") == 0) {
          // 抱きつき → 0°
          servo1.write(0);
          servo2.write(0);
          isHugPosition = true;
          hugStartTime = millis(); // 抱きつき開始時間記録
          Serial.println("→ Servo moved to 0° (hug)");
        }
        else {
          Serial.println("Unknown command received");
        }
      } 
      else {
        Serial.print("JSON parse error: ");
        Serial.println(error.c_str());
      }

      // バッファをクリア
      btBuffer = "";
    }
  }

  // 自動で離す処理（2秒後）
  if (isHugPosition && millis() - hugStartTime >= HUG_DURATION) {
    servo1.write(120);
    servo2.write(120);
    isHugPosition = false;
    Serial.println("→ Auto release after 2 seconds");
  }
}
