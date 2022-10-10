import 'dart:developer';

import 'package:flutter/services.dart';
import 'package:telpo_flutter_sdk/telpo_flutter_sdk.dart';

class TelpoFlutterChannel {
  late MethodChannel _platform;

  final String _channelName = 'me.aljan.telpo_flutter_sdk/telpo';

  TelpoFlutterChannel() {
    _platform = MethodChannel(_channelName);
  }

  /// Returns an [Enum] of type [TelpoStatus] indicating current status of
  /// underlying Telpo Device.
  Future<TelpoStatus> checkStatus() async {
    try {
      final status = await _platform.invokeMethod('checkStatus');

      switch (status) {
        case 'STATUS_OK':
          return TelpoStatus.ok;
        case 'STATUS_NO_PAPER':
          return TelpoStatus.noPaper;
        case 'STATUS_OVER_FLOW':
          return TelpoStatus.cacheIsFull;

        case 'STATUS_OVER_UNKNOWN':
        default:
          return TelpoStatus.unknown;
      }
    } catch (_) {
      return TelpoStatus.unknown;
    }
  }

  /// Connect with underlying Telpo device if any.
  ///
  /// Returns a nullable [bool] whether connected successfully or not.
  Future<bool?> connect() {
    return _platform.invokeMethod('connect');
  }

  /// Disconnect from Telpo device.
  ///
  /// Returns a nullable [bool] whether disconnected successfully or not.
  Future<bool?> disconnect() {
    return _platform.invokeMethod('disconnect');
  }

  /// Returns a nullable [bool] whether or not connected with Telpo device.
  Future<bool?> isConnected() {
    return _platform.invokeMethod('isConnected');
  }

  /// Takes [List<PrintData>] to be printed and returns [PrintResult] enum as
  /// an indicator for result of the process
  ///
  /// If [PrintResult.success] the data printed successfully, if else process
  /// blocked by some exception. See the result enum for more info.
  Future<PrintResult> print(List<PrintData> data) async {
    try {
      await _platform.invokeMethod(
        'print',
        {
          "data": data.toJson(),
        },
      );

      return PrintResult.success;
    } on PlatformException catch (e) {
      switch (e.code) {
        case '3':
          return PrintResult.noPaper;
        case '4':
          return PrintResult.lowBattery;
        case '12':
          return PrintResult.overHeat;
        case '13':
          return PrintResult.dataCanNotBeTransmitted;
        default:
          log('TELPO EXCEPTION: $e, code: ${e.code}');

          return PrintResult.other;
      }
    }
  }
}
