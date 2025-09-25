package com.example.myapp.manager

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.*

class BluetoothPolarManager {

    private var bluetoothGatt: BluetoothGatt? = null
    private var heartRateCallback: ((Int) -> Unit)? = null

    companion object {
        private val HEART_RATE_SERVICE_UUID: UUID =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT_UUID: UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val BATTERY_SERVICE_UUID: UUID =
            UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_UUID: UUID =
            UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    }

    /**
     * Polarデバイスに接続して心拍データを受信
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(context: Context, device: BluetoothDevice, onHeartRateReceived: (Int) -> Unit) {
        heartRateCallback = onHeartRateReceived
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    /**
     * バッテリー残量（%）を取得
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun requestBatteryLevel(context: Context, device: BluetoothDevice, callback: (Int) -> Unit) {
        var batteryGatt: BluetoothGatt? = null

        batteryGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("PolarBattery", "バッテリー取得のために接続成功")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("PolarBattery", "バッテリー用接続切断")
                    batteryGatt?.close()
                    batteryGatt = null
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                val batteryLevelChar = batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)

                if (batteryLevelChar != null) {
                    val success = gatt.readCharacteristic(batteryLevelChar)
                    if (!success) {
                        Log.e("PolarBattery", "バッテリー読み取り失敗")
                        callback(-1)
                        gatt.disconnect()
                    }
                } else {
                    Log.e("PolarBattery", "バッテリーサービスが見つかりません")
                    callback(-1)
                    gatt.disconnect()
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (characteristic.uuid == BATTERY_LEVEL_UUID) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                        Log.d("PolarBattery", "バッテリー残量: $batteryLevel%")
                        callback(batteryLevel)
                    } else {
                        Log.e("PolarBattery", "読み取り失敗 status=$status")
                        callback(-1)
                    }
                    gatt.disconnect()
                }
            }
        })
    }

    /**
     * GATT接続の終了（心拍用）
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stop() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    /**
     * 心拍取得用の GATT コールバック
     */
    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("Polar", "心拍取得接続成功、サービス探索中")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("Polar", "心拍取得切断")
                stop()
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(HEART_RATE_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)

            if (characteristic != null) {
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                Log.d("Polar", "心拍通知を有効にしました")
            } else {
                Log.e("Polar", "心拍サービスが見つかりません")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                val flag = characteristic.properties
                val format = if (flag and 0x01 != 0)
                    BluetoothGattCharacteristic.FORMAT_UINT16
                else
                    BluetoothGattCharacteristic.FORMAT_UINT8

                val heartRate = characteristic.getIntValue(format, 1) ?: -1
                Log.d("Polar", "心拍数: $heartRate bpm")
                heartRateCallback?.invoke(heartRate)
            }
        }
    }
}
