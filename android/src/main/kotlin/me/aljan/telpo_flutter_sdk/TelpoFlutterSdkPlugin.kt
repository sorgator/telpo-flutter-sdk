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
import com.telpo.tps550.api.decode.Decode
import com.telpo.tps550.api.decode.DecodeReaderActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class TelpoFlutterSdkPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private val TAG = "TelpoFlutterSdkPlugin"
    private lateinit var channel: MethodChannel
    private val channelId = "me.aljan.telpo_flutter_sdk/telpo"
    private var lowBattery = false
    private var _isConnected = false
    private lateinit var context: Context
    private lateinit var activity: Activity
    private lateinit var binding: FlutterPlugin.FlutterPluginBinding
    private lateinit var telpoThermalPrinter: TelpoThermalPrinter

    private var activityPluginBinding: ActivityPluginBinding? = null
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
            val plugin = TelpoFlutterSdkPlugin()
            channel.setMethodCallHandler(plugin)
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
            "openSoftScanner" -> {
                // Soft decoding (using Telpo API Capture activity)
                openSoftScanner()
            }
            "openHardScanner" -> {
                // Hard decoding (custom activity for hard decoding)
                openHardScanner()
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
            else -> {
                resultWrapper.notImplemented()
            }
        }
    }

    private fun openSoftScanner() {
        try {
            val intent = Intent()
            intent.setClassName("com.telpo.tps550.api", "com.telpo.tps550.api.barcode.Capture")
            activityPluginBinding?.activity?.startActivityForResult(intent, REQUEST_CODE_QR_SCAN)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Soft decoding app not found", Toast.LENGTH_LONG).show()
        }
    }

    private fun openHardScanner() {
        // Launch the custom activity for hard decoding
        val intent = Intent(activity, DecodeReaderActivity::class.java)
        activityPluginBinding?.activity?.startActivity(intent)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityPluginBinding = binding
        activity = binding.activity
        activityPluginBinding?.addActivityResultListener { requestCode, resultCode, data ->
            if (requestCode == REQUEST_CODE_QR_SCAN) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val qrCode = data.getStringExtra("qrCode")
                    result.success(qrCode)
                } else {
                    result.error("ERROR", "QR scan failed", null)
                }
                true
            } else {
                false
            }
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityPluginBinding = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        activityPluginBinding = null
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        if (_isConnected) {
            context.unregisterReceiver(printReceive)
        }
        channel.setMethodCallHandler(null)
    }

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
