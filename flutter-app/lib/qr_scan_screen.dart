import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:permission_handler/permission_handler.dart';

/// 扫描游戏内地图上的二维码（内容形如 ADCOIN:ABC12345），返回原始文本。
/// 关闭/扫到时通过 Navigator.pop(value) 返回。
class QrScanScreen extends StatefulWidget {
  const QrScanScreen({super.key});

  @override
  State<QrScanScreen> createState() => _QrScanScreenState();
}

class _QrScanScreenState extends State<QrScanScreen> {
  bool _handled = false;
  bool _permissionGranted = false;
  bool _checking = true;

  @override
  void initState() {
    super.initState();
    _requestPermission();
  }

  Future<void> _requestPermission() async {
    final status = await Permission.camera.request();
    if (mounted) {
      setState(() {
        _permissionGranted = status.isGranted;
        _checking = false;
      });
    }
  }

  void _onDetect(BarcodeCapture capture) {
    if (_handled || capture.barcodes.isEmpty) return;
    final value = capture.barcodes.first.rawValue;
    if (value == null || value.isEmpty) return;
    _handled = true;
    if (mounted) Navigator.of(context).pop(value);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('扫描绑定二维码')),
      body: _checking
          ? const Center(child: CircularProgressIndicator())
          : !_permissionGranted
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.camera_alt_outlined, size: 56),
                        const SizedBox(height: 12),
                        const Text('需要相机权限才能扫码\n（也可以在上一页手动输入字符绑定码）',
                            textAlign: TextAlign.center),
                        const SizedBox(height: 16),
                        FilledButton(
                          onPressed: _requestPermission,
                          child: const Text('去授权'),
                        ),
                        TextButton(
                          onPressed: () => Navigator.of(context).pop(),
                          child: const Text('返回手动输入'),
                        ),
                      ],
                    ),
                  ),
                )
              : MobileScanner(
                  onDetect: _onDetect,
                  errorBuilder: (context, error) => Center(
                    child: Text('相机错误：$error\n可返回手动输入绑定码',
                        textAlign: TextAlign.center),
                  ),
                ),
    );
  }
}