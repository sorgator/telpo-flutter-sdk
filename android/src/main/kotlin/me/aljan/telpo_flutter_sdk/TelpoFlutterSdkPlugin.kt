package me.aljan.telpo_flutter_sdk

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.annotation.NonNull
import com.telpo.tps550.api.decode.Decode
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

/** TelpoFlutterSdkPlugin */
class TelpoFlutterSdkPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private val TAG = "TelpoFlutterSdkPlugin"
    private lateinit var channel: MethodChannel
    private val channelId = "me.aljan.telpo_flutter_sdk/telpo"
    private var lowBattery = false
    private var _isConnected = false
    lateinit var context: Context
    private lateinit var activity: Activity
    private lateinit var binding: FlutterPlugin.FlutterPluginBinding
    private lateinit var telpoThermalPrinter: TelpoThermalPrinter
    public lateinit var registrar: PluginRegistry.Registrar

    // Request code for QR Scanner via Capture activity
    private val REQUEST_CODE_QR_SCAN = 0x124
    private lateinit var result: Result

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, channelId)
        binding = flutterPluginBinding
        context = flutterPluginBinding.applicationContext
        telpoThermalPrinter = TelpoThermalPrinter(this@TelpoFlutterSdkPlugin)
        channel.setMethodCallHandler(this)
    }

    companion object {
        @JvmStatic
        fun registerWith(registrar: PluginRegistry.Registrar) {
            val channel = MethodChannel(registrar.messenger(), TelpoFlutterSdkPlugin().channelId)
            val plugin = TelpoFlutterSdkPlugin();
            channel.setMethodCallHandler(plugin);
            TelpoFlutterSdkPlugin().registrar = registrar;
        }
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        this.result = result
        val resultWrapper = MethodChannelResultWrapper(result)

        when (call.method) {
            "connect" -> {
                if (!_isConnected) {
                    val pIntentFilter = IntentFilter()
                    pIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
                    pIntentFilter.addAction("android.intent.action.BATTERY_CAPACITY_EVENT")

                    context.registerReceiver(printReceive, pIntentFilter)
                    val isConnected = telpoThermalPrinter.connect()
                    _isConnected = isConnected

                    Log.d(TAG, "connected")
                    resultWrapper.success(_isConnected)
                }
            }
            "checkStatus" -> {
                telpoThermalPrinter.checkStatus(resultWrapper, lowBattery)
            }
            "isConnected" -> {
                resultWrapper.success(_isConnected)
            }
            "disconnect" -> {
                if (_isConnected) {
                    Log.d(TAG, "disconnected")

                    context.unregisterReceiver(printReceive)
                    val disconnected = telpoThermalPrinter.disconnect()
                    _isConnected = false
                    resultWrapper.success(disconnected)
                }
            }
            "print" -> {
                val printDataList =
                    call.argument<ArrayList<Map<String, Any>>>("data") ?: ArrayList()

                telpoThermalPrinter.print(resultWrapper, printDataList, lowBattery)
            }
            "openScanner" -> {
                try {
                    Decode.open() // open the scanner
                    result.success(null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening the scanner", e)
                    result.error("ERROR:", "failed to open the scanner", e.message)
                }
            }
            "readWithFormat" -> {
                val timeout: Int = call.argument<Int>("timeout") ?: 5000 // Use a default value if null
                try {
                    val scanResult: ByteArray = Decode.readWithFormat(timeout)
                    val scanType = scanResult[0]
                    val length = scanResult[1]
                    if (scanResult.size >= 2 + length) {
                        val barCodeData = String(scanResult.sliceArray(2 until 2 + length))

                        val response = mapOf(
                            "type" to scanType,
                            "length" to length,
                            "data" to barCodeData
                        )
                        result.success(response)
                    } else {
                        Log.e(TAG, "Invalid scan result")
                        result.error("ERROR", "Invalid scan result", null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading barcode", e)
                    result.error("ERROR:", "failed to read barcode", e.message)
                }
            }
            "closeScanner" -> {
                try {
                    Decode.close()
                    result.success(null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing the scanner", e)
                    result.error("ERROR:", "failed to close the scanner", e.message)
                }
            }
            "startQrCodeScan" -> {
                openQrScanner()
            }
            else -> {
                resultWrapper.notImplemented()
            }
        }
    }

    // Method to open QR scanner using Telpo Capture activity
    private fun openQrScanner() {
        try {
            val intent = Intent()
            intent.setClassName("com.telpo.tps550.api", "com.telpo.tps550.api.barcode.Capture")
            activity.startActivityForResult(intent, REQUEST_CODE_QR_SCAN)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening QR scanner", e)
            result.error("ERROR:", "failed to open QR scanner", e.message)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_QR_SCAN) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // Success: Extract the QR code data
                val qrCode = data.getStringExtra("qrCode")
                result.success(qrCode)
            } else {
                // Failure: Return an error
                result.error("ERROR", "QR scan failed", null)
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        if (_isConnected) {
            context.unregisterReceiver(printReceive)
        }
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {}

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {}

    private val printReceive: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action == Intent.ACTION_BATTERY_CHANGED) {
                val status = intent.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING
                )
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)

                if (status != BatteryManager.BATTERY_STATUS_CHARGING) {
                    lowBattery = level * 5 <= scale
                }
            } else if (action == "android.intent.action.BATTERY_CAPACITY_EVENT") {
                val status: Int = intent.getIntExtra("action", 0)
                val level: Int = intent.getIntExtra("level", 0)

                lowBattery = if (status == 0) {
                    level < 1
                } else {
                    false
                }
            }
        }
    }
}
