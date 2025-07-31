package me.aljan.telpo_flutter_sdk

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import com.common.apiutil.decode.DecodeReader
import com.common.callback.IDecodeReaderListener
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.PluginRegistry

/** TelpoFlutterSdkPlugin */
class TelpoFlutterSdkPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private val TAG = "TelpoFlutterSdkPlugin"
    private lateinit var channel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private val channelId = "me.aljan.telpo_flutter_sdk/telpo"
    private val eventChannelId = "me.aljan.telpo_flutter_sdk/scanStream"
    private var lowBattery = false
    private var _isConnected = false
    lateinit var context: Context
    private lateinit var activity: Activity
    private lateinit var binding: FlutterPlugin.FlutterPluginBinding
    private lateinit var telpoThermalPrinter: TelpoThermalPrinter
    private lateinit var decodeReader: DecodeReader

    private var eventSink: EventChannel.EventSink? = null
    private var activityPluginBinding: ActivityPluginBinding? = null
    private val REQUEST_CODE_QR_SCAN = 0x124

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, channelId)
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, eventChannelId)
        binding = flutterPluginBinding
        context = flutterPluginBinding.applicationContext
        telpoThermalPrinter = TelpoThermalPrinter(this@TelpoFlutterSdkPlugin)
        decodeReader = DecodeReader(context)

        channel.setMethodCallHandler(this)

        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                eventSink = events
            }

            override fun onCancel(arguments: Any?) {
                eventSink = null
            }
        })
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull flutterResult: MethodChannel.Result) {
        val resultWrapper = MethodChannelResultWrapper(flutterResult)

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
            "openSoftScanner" -> {
                openSoftScanner(flutterResult)
            }
            "openHardScanner" -> {
                openHardScanner(flutterResult)
            }
            "closeScanner" -> {
                closeScanner(flutterResult)
            }
            else -> {
                resultWrapper.notImplemented()
            }
        }
    }

    private fun openSoftScanner(result: MethodChannel.Result) {
        try {
            val intent = Intent()
            intent.setClassName("com.telpo.tps550.api", "com.telpo.tps550.api.barcode.Capture")
            activity.startActivityForResult(intent, REQUEST_CODE_QR_SCAN)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Soft decoding app not found", Toast.LENGTH_LONG).show()
            result.error("ERROR", "Soft decoding app not found", e.message)
        }
    }

    private fun openHardScanner(result: MethodChannel.Result) {
        try {
            val openResult = decodeReader.open(115200) // Open with baud rate 115200
            if (openResult == 0) {
                decodeReader.setDecodeReaderListener(object : IDecodeReaderListener {
                    override fun onRecvData(data: ByteArray) {
                        // Handle received data
                        val scannedData = String(data)
                        Log.d(TAG, "Scanned Data new: $scannedData")

                        // Ensure the eventSink is valid before sending data
                        activity.runOnUiThread {
                            if (eventSink != null) {
                                eventSink?.success(scannedData)
                            } else {
                                Log.e(TAG, "EventSink is null, no active listener in Flutter")
                            }
                        }
                    }
                })
                Log.d(TAG, "Hard scanner opened successfully.")
                result.success(null)
            } else {
                Log.e(TAG, "Failed to open hard scanner, result code: $openResult")
                result.error("ERROR", "Failed to open hard scanner", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening hard scanner", e)
            result.error("ERROR", "Error opening hard scanner: ${e.message}", null)
        }
    }


    private fun closeScanner(result: MethodChannel.Result) {
        try {
            val closeResult = decodeReader.close()
            if (closeResult == 0) {
                Log.d(TAG, "Scanner closed successfully.")
                result.success(null)
            } else {
                Log.e(TAG, "Failed to close scanner, result code: $closeResult")
                result.error("ERROR", "Failed to close scanner", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing the scanner", e)
            result.error("ERROR", "Error closing scanner: ${e.message}", null)
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
