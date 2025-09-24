//m5stickcplus-bluetooth-hug

#include <M5Unified.h>
#include "M5UnitENV.h"
#include "BluetoothSerial.h"  // Bluetooth Serial ライブラリ

SHT3X sht3x;
QMP6988 qmp;
BluetoothSerial SerialBT; // Bluetooth シリアルオブジェクト

// 以前の測定値を保持
float prevTempSHT = -100, prevHumSHT = -100;
float prevTempQMP = -100, prevPresQMP = -100, prevAltQMP = -100;

void setup() {
    M5.begin();
    SerialBT.begin("M5StickC-ENV");  // Bluetooth 名を設定

    M5.Lcd.setRotation(3);
    M5.Lcd.fillScreen(BLACK);
    M5.Lcd.setTextColor(WHITE, BLACK);
    M5.Lcd.setTextSize(2);

    if (!sht3x.begin(&Wire, SHT3X_I2C_ADDR, 32, 33, 400000U)) {
        Serial.println("Couldn't find SHT3X");
        M5.Lcd.setCursor(10, 10);
        M5.Lcd.println("SHT3X Not Found");
        while (1) delay(1);
    }

    if (!qmp.begin(&Wire, QMP6988_SLAVE_ADDRESS_L, 32, 33, 400000U)) {
        Serial.println("Couldn't find QMP6988");
        M5.Lcd.setCursor(10, 30);
        M5.Lcd.println("QMP6988 Not Found");
        while (1) delay(1);
    }
}

void loop() {
    M5.update();

    float tempSHT = sht3x.update() ? sht3x.cTemp : prevTempSHT;
    float humSHT  = sht3x.update() ? sht3x.humidity : prevHumSHT;

    float tempQMP = qmp.update() ? qmp.cTemp : prevTempQMP;
    float presQMP = qmp.update() ? qmp.pressure / 100 : prevPresQMP; // hPa
    float altQMP  = qmp.update() ? qmp.altitude : prevAltQMP;

    // 画面表示の更新
    M5.Lcd.fillRect(10, 30, 200, 100, BLACK);
    M5.Lcd.setCursor(10, 10);
    M5.Lcd.println("SHT3X Sensor");
    M5.Lcd.setCursor(10, 40);
    M5.Lcd.printf("Temp: %.2f C", tempSHT);
    M5.Lcd.setCursor(10, 60);
    M5.Lcd.printf("Humidity: %.2f%% rH", humSHT);

    M5.Lcd.setCursor(10, 90);
    M5.Lcd.printf("Pressure: %.1f hPa", presQMP);
    M5.Lcd.setCursor(10, 120);
    M5.Lcd.printf("Altitude: %.2f m", altQMP);

    // JSON データの作成
    String jsonData = "{";
    if (presQMP > 1015.0) {
        jsonData += "\"key\":\"hug\"";
    } else {
        jsonData += "\"key\":\"nohug\"";
    }
    jsonData += "}\n";

    // Bluetooth とシリアルモニターへ送信
    SerialBT.print(jsonData);
    Serial.println(jsonData);

    // 以前の値を更新
    prevTempSHT = tempSHT;
    prevHumSHT  = humSHT;
    prevTempQMP = tempQMP;
    prevPresQMP = presQMP;
    prevAltQMP  = altQMP;

    delay(1000);
}
