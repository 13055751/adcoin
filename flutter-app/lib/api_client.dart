import 'dart:convert';

import 'package:http/http.dart' as http;

import 'models.dart';

/// 后端地址：构建时 -Padcoin 时代对应 --dart-define=API_BASE_URL=...
/// 默认模拟器访问宿主机。
const String kApiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8787',
);

/// 广告模式：mock | admob | pangle
const String kAdMode = String.fromEnvironment('AD_MODE', defaultValue: 'mock');

class ApiException implements Exception {
  final int status;
  final String error;
  ApiException(this.status, this.error);

  @override
  String toString() => 'ApiException($status, $error)';
}

/// 后端 API 客户端（http 包，无第三方封装依赖）。
class ApiClient {
  String? _token;

  void setToken(String? token) => _token = token;

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        if (_token != null) 'Authorization': 'Bearer $_token',
      };

  Future<Map<String, dynamic>> _req(
    String method,
    String path, {
    Map<String, dynamic>? body,
  }) async {
    final uri = Uri.parse('$kApiBaseUrl/$path');
    try {
      final resp = method == 'GET'
          ? await http.get(uri, headers: _headers)
          : await http.post(uri,
              headers: _headers, body: jsonEncode(body ?? const {}));
      final Map<String, dynamic> json =
          resp.body.isEmpty ? <String, dynamic>{} : jsonDecode(utf8.decode(resp.bodyBytes)) as Map<String, dynamic>;
      if (resp.statusCode >= 400 && json['ok'] != true) {
        throw ApiException(resp.statusCode, (json['error'] as String?) ?? 'http_${resp.statusCode}');
      }
      return json;
    } on ApiException {
      rethrow;
    } catch (e) {
      throw ApiException(0, 'network: $e');
    }
  }

  // ------------------------------------------------------------ 认证

  Future<AuthResult> register(String username, String password) async {
    final j = await _req('POST', 'api/auth/register',
        body: {'username': username, 'password': password});
    return AuthResult.fromJson(j);
  }

  Future<AuthResult> login(String username, String password) async {
    final j = await _req('POST', 'api/auth/login',
        body: {'username': username, 'password': password});
    return AuthResult.fromJson(j);
  }

  // ------------------------------------------------------------ 数据

  Future<MeInfo> me() async => MeInfo.fromJson(await _req('GET', 'api/me'));

  Future<ClaimResult> claim({
    required String platform,
    required String transactionId,
    String? adUnitId,
  }) async {
    final j = await _req('POST', 'api/ad/claim', body: {
      'platform': platform,
      'transactionId': transactionId,
      if (adUnitId != null) 'adUnitId': adUnitId,
    });
    return ClaimResult.fromJson(j);
  }

  Future<BindResult> bind(String code) async =>
      BindResult.fromJson(await _req('POST', 'api/link/bind', body: {'code': code}));

  Future<BindResult> bindLong() async =>
      BindResult.fromJson(await _req('POST', 'api/link/bind-long', body: {}));

  Future<GenericResult> unbind() async =>
      GenericResult.fromJson(await _req('POST', 'api/link/unbind'));

  Future<SearchResponse> search(String q) async {
    final j = await _req('GET', 'api/friend/search?q=${Uri.encodeQueryComponent(q)}');
    return SearchResponse.fromJson(j);
  }

  Future<FriendActionResult> friend(String action, [Map<String, dynamic>? body]) async {
    final j = await _req('POST', 'api/friend/$action', body: body ?? const {});
    return FriendActionResult.fromJson(j);
  }

  Future<LeaderboardResponse> leaderboard() async =>
      LeaderboardResponse.fromJson(await _req('GET', 'api/leaderboard'));

  Future<TransactionsResponse> transactions() async =>
      TransactionsResponse.fromJson(await _req('GET', 'api/transactions'));

  Future<TransferResult> transfer({
    required String toAppUserId,
    required double amount,
    required String clientTxId,
  }) async {
    final j = await _req('POST', 'api/transfer', body: {
      'toAppUserId': toAppUserId,
      'amount': amount,
      'clientTxId': clientTxId,
    });
    return TransferResult.fromJson(j);
  }
}