/// 与后端 JSON 对应的模型。
library;

class Session {
  final String token;
  final String username;
  final String appUserId;
  final String? linkedPlayerName;

  const Session({
    required this.token,
    required this.username,
    required this.appUserId,
    this.linkedPlayerName,
  });
}

class UserDto {
  final String? id;
  final String? username;
  final String? appUserId;
  final String? linkedPlayerUuid;
  final String? linkedPlayerName;

  const UserDto({
    this.id,
    this.username,
    this.appUserId,
    this.linkedPlayerUuid,
    this.linkedPlayerName,
  });

  factory UserDto.fromJson(Map<String, dynamic> j) => UserDto(
        id: j['id'] as String?,
        username: j['username'] as String?,
        appUserId: j['appUserId'] as String?,
        linkedPlayerUuid: j['linkedPlayerUuid'] as String?,
        linkedPlayerName: j['linkedPlayerName'] as String?,
      );
}

class AuthResult {
  final bool ok;
  final String? token;
  final UserDto? user;
  final String? error;

  const AuthResult({required this.ok, this.token, this.user, this.error});

  factory AuthResult.fromJson(Map<String, dynamic> j) => AuthResult(
        ok: j['ok'] == true,
        token: j['token'] as String?,
        user: j['user'] is Map<String, dynamic> ? UserDto.fromJson(j['user']) : null,
        error: j['error'] as String?,
      );
}

class MeInfo {
  final bool ok;
  final UserDto? user;
  final double balance;
  final int dailyUsed;
  final int dailyLimit;
  final double adReward;
  final bool linked;
  final String? error;

  const MeInfo({
    required this.ok,
    this.user,
    this.balance = 0,
    this.dailyUsed = 0,
    this.dailyLimit = 20,
    this.adReward = 50,
    this.linked = false,
    this.error,
  });

  factory MeInfo.fromJson(Map<String, dynamic> j) => MeInfo(
        ok: j['ok'] == true,
        user: j['user'] is Map<String, dynamic> ? UserDto.fromJson(j['user']) : null,
        balance: (j['balance'] as num?)?.toDouble() ?? 0,
        dailyUsed: (j['dailyUsed'] as num?)?.toInt() ?? 0,
        dailyLimit: (j['dailyLimit'] as num?)?.toInt() ?? 20,
        adReward: (j['adReward'] as num?)?.toDouble() ?? 50,
        linked: j['linked'] == true,
        error: j['error'] as String?,
      );
}

class ClaimResult {
  final bool ok;
  final bool credited;
  final bool duplicate;
  final String? error;

  const ClaimResult({required this.ok, this.credited = false, this.duplicate = false, this.error});

  factory ClaimResult.fromJson(Map<String, dynamic> j) => ClaimResult(
        ok: j['ok'] == true,
        credited: j['credited'] == true,
        duplicate: j['duplicate'] == true,
        error: j['error'] as String?,
      );
}

class FriendItem {
  final String? uuid;
  final String? name;
  final bool online;
  final String? appUserId;

  const FriendItem({this.uuid, this.name, this.online = false, this.appUserId});

  factory FriendItem.fromJson(Map<String, dynamic> j) => FriendItem(
        uuid: j['uuid'] as String?,
        name: j['name'] as String?,
        online: j['online'] == true,
        appUserId: j['appUserId'] as String?,
      );
}

class FriendActionResult {
  final bool ok;
  final List<FriendItem> friends;
  final List<Map<String, dynamic>> requests;
  final String? error;

  const FriendActionResult({
    required this.ok,
    this.friends = const [],
    this.requests = const [],
    this.error,
  });

  factory FriendActionResult.fromJson(Map<String, dynamic> j) => FriendActionResult(
        ok: j['ok'] == true,
        friends: (j['friends'] as List<dynamic>? ?? [])
            .map((e) => FriendItem.fromJson(e as Map<String, dynamic>))
            .toList(),
        requests: (j['requests'] as List<dynamic>? ?? [])
            .map((e) => e as Map<String, dynamic>)
            .toList(),
        error: j['error'] as String?,
      );
}

class SearchResult {
  final String? username;
  final String? appUserId;
  final String? playerName;
  final String? name;

  const SearchResult({this.username, this.appUserId, this.playerName, this.name});

  factory SearchResult.fromJson(Map<String, dynamic> j) => SearchResult(
        username: j['username'] as String?,
        appUserId: j['appUserId'] as String?,
        playerName: j['playerName'] as String?,
        name: j['name'] as String?,
      );
}

class SearchResponse {
  final bool ok;
  final List<SearchResult> results;

  const SearchResponse({required this.ok, this.results = const []});

  factory SearchResponse.fromJson(Map<String, dynamic> j) => SearchResponse(
        ok: j['ok'] == true,
        results: (j['results'] as List<dynamic>? ?? [])
            .map((e) => SearchResult.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class LeaderboardEntry {
  final String? uuid;
  final String? name;
  final double balance;

  const LeaderboardEntry({this.uuid, this.name, this.balance = 0});

  factory LeaderboardEntry.fromJson(Map<String, dynamic> j) => LeaderboardEntry(
        uuid: j['uuid'] as String?,
        name: j['name'] as String?,
        balance: (j['balance'] as num?)?.toDouble() ?? 0,
      );
}

class LeaderboardResponse {
  final bool ok;
  final List<LeaderboardEntry> top;

  const LeaderboardResponse({required this.ok, this.top = const []});

  factory LeaderboardResponse.fromJson(Map<String, dynamic> j) => LeaderboardResponse(
        ok: j['ok'] == true,
        top: (j['top'] as List<dynamic>? ?? [])
            .map((e) => LeaderboardEntry.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class TransactionEntry {
  final String? txId;
  final double amount;
  final String? adNetwork;
  final int ts;
  final String? fromAppUserId;

  const TransactionEntry({
    this.txId,
    this.amount = 0,
    this.adNetwork,
    required this.ts,
    this.fromAppUserId,
  });

  factory TransactionEntry.fromJson(Map<String, dynamic> j) => TransactionEntry(
        txId: j['txId'] as String?,
        amount: (j['amount'] as num?)?.toDouble() ?? 0,
        adNetwork: j['adNetwork'] as String?,
        ts: (j['ts'] as num?)?.toInt() ?? 0,
        fromAppUserId: j['fromAppUserId'] as String?,
      );
}

class TransactionsResponse {
  final bool ok;
  final List<TransactionEntry> entries;

  const TransactionsResponse({required this.ok, this.entries = const []});

  factory TransactionsResponse.fromJson(Map<String, dynamic> j) => TransactionsResponse(
        ok: j['ok'] == true,
        entries: (j['entries'] as List<dynamic>? ?? [])
            .map((e) => TransactionEntry.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class TransferResult {
  final bool ok;
  final String? error;

  const TransferResult({required this.ok, this.error});

  factory TransferResult.fromJson(Map<String, dynamic> j) =>
      TransferResult(ok: j['ok'] == true, error: j['error'] as String?);
}

class GenericResult {
  final bool ok;
  final String? error;

  const GenericResult({required this.ok, this.error});

  factory GenericResult.fromJson(Map<String, dynamic> j) =>
      GenericResult(ok: j['ok'] == true, error: j['error'] as String?);
}