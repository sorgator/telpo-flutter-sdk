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
      final status = await _platform.invokeMethod<String>('checkStatus');

      switch (status) {
        case 'STATUS_OK':
          return TelpoStatus.ok;
        case 'STATUS_NO_PAPER':
          return TelpoStatus.noPaper;
        case 'STATUS_OVER_FLOW':
          return TelpoStatus.cacheIsFull;
        default:
          return TelpoStatus.unknown;
      }
    } catch (e) {
      log('Failed to check status: $e');
      return TelpoStatus.unknown;
    }
  }

  /// Connect with underlying Telpo device if any.
  /// Returns a [bool] indicating whether connected successfully or not.
  Future<bool> connect() async {
    try {
      final connected = await _platform.invokeMethod<bool>('connect');
      return connected ?? false;
    } catch (e) {
      log('TELPO EXCEPTION: $e');
      return false;
    }
  }

  /// Disconnect from Telpo device.
  /// Returns a [bool] indicating whether disconnected successfully or not.
  Future<bool> disconnect() async {
    try {
      final disconnected = await _platform.invokeMethod<bool>('disconnect');
      return disconnected ?? false;
    } catch (e) {
      log('TELPO EXCEPTION: $e');
      return false;
    }
  }

  /// Returns a nullable [bool] whether or not connected with Telpo device.
  Future<bool?> isConnected() async {
    try {
      final isConnected = await _platform.invokeMethod<bool>('isConnected');
      return isConnected;
    } catch (e) {
      log('TELPO EXCEPTION: $e');
      return false;
    }
  }

  /// Takes [List<PrintData>] to be printed and returns [PrintResult] enum as
  /// an indicator for result of the process.
  Future<PrintResult> print(TelpoPrintSheet data) async {
    try {
      await _platform.invokeMethod(
        'print',
        {
          "data": data.asJson,
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

  /// Read barcode with timeout (in milliseconds).
  /// Returns a map with the type, length, and data of the barcode.
  Future<Map<String, dynamic>> readBarcodeWithFormat(int timeout) async {
    try {
      final result = await _platform
          .invokeMethod<Map>('readWithFormat', {"timeout": timeout});
      return Map<String, dynamic>.from(result ?? {});
    } on PlatformException catch (e) {
      log('Telpo exception: $e');
      return {};
    }
  }

  /// Open the software 2D barcode scanner (Soft Decoding).
  Future<void> openSoftScanner() async {
    try {
      await _platform.invokeMethod('openSoftScanner');
      log('Soft scanner opened successfully');
    } on PlatformException catch (e) {
      log('Failed to open soft scanner: $e');
    }
  }

  /// Open the hard 2D barcode scanner (Hard Decoding).
  Future<void> openHardScanner() async {
    try {
      await _platform.invokeMethod('openHardScanner');
      log('Hard scanner opened successfully');
    } on PlatformException catch (e) {
      log('Failed to open hard scanner: $e');
    }
  }

  /// Close the 2D barcode scanner.
  Future<void> closeScanner() async {
    try {
      await _platform.invokeMethod('closeScanner');
      log('Scanner closed successfully');
    } on PlatformException catch (e) {
      log('Failed to close scanner: $e');
    }
  }
}
