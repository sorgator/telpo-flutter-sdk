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
  /// Returns a [bool] whether connected successfully or not.
  Future<bool> connect() async {
    try {
      final connected = await _platform.invokeMethod('connect');

      return connected ?? false;
    } catch (e) {
      log('TELPO EXCEPTION: $e');
      return false;
    }
  }

  /// Disconnect from Telpo device.
  ///
  /// Returns a [bool] whether disconnected successfully or not.
  Future<bool> disconnect() async {
    try {
      final disconnected = await _platform.invokeMethod('disconnect');
      return disconnected ?? false;
    } catch (e) {
      log('TELPO EXCEPTION: $e');
      return false;
    }
  }

  /// Returns a nullable [bool] whether or not connected with Telpo device.
  Future<bool?> isConnected() async {
    try {
      final isConnected = await _platform.invokeMethod('isConnected');
      return isConnected ?? false;
    } catch (e) {
      log('TELPO EXCEPTION: $e');
      return false;
    }
  }

  /// Takes [List<PrintData>] to be printed and returns [PrintResult] enum as
  /// an indicator for result of the process
  ///
  /// If [PrintResult.success] the data printed successfully, if else process
  /// blocked by some exception. See the result enum for more info.
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

  /// Opens the barcode scanner.
  Future<void> openScanner() async {
    try {
      await _platform.invokeMethod('openScanner');
    } on PlatformException catch (e) {
      log('Telpo exception: $e');
    }
  }

  /// Reads barcode with format and specified timeout.
  Future<Map<String, dynamic>> readBarcodeWithFormat(int timeout) async {
    try {
      final result =
          await _platform.invokeMethod('readWithFormat', {"timeout": timeout});
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      log('Telpo exception: $e');
      return {};
    }
  }

  /// Closes the barcode scanner.
  Future<void> closeScanner() async {
    try {
      await _platform.invokeMethod('closeScanner');
    } on PlatformException catch (e) {
      log('Telpo exception: $e');
    }
  }

  /// Opens the QR scanner using Capture activity.
  Future<void> openQrScanner() async {
    try {
      await _platform.invokeMethod('startQrCodeScan');
      log('QR scanner opened successfully');
    } on PlatformException catch (e) {
      log('Failed to open QR scanner: $e');
    }
  }

  /// Reads the result of QR code scanning and returns the QR data.
  Future<String> startQrCodeScan() async {
    try {
      final qrCode = await _platform.invokeMethod('startQrCodeScan');
      return qrCode;
    } on PlatformException catch (e) {
      log('Failed to read QR code: $e');
      return '';
    }
  }
}
