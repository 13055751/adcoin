package com.adcoin.app.data

/** 与后端 JSON 对应的 DTO（Gson）。 */

data class UserDto(
    val id: String? = null,
    val username: String? = null,
    val appUserId: String? = null,
    val linkedPlayerUuid: String? = null,
    val linkedPlayerName: String? = null,
    val linkedAt: Long? = null,
)

data class AuthResponse(
    val ok: Boolean = false,
    val token: String? = null,
    val user: UserDto? = null,
    val error: String? = null,
)

data class MeResponse(
    val ok: Boolean = false,
    val user: UserDto? = null,
    val balance: Double = 0.0,
    val linked: Boolean = false,
    val error: String? = null,
)

data class ClaimResponse(
    val ok: Boolean = false,
    val credited: Boolean? = null,
    val duplicate: Boolean? = null,
    val balance: Double? = null,
    val error: String? = null,
)

data class BindResponse(
    val ok: Boolean = false,
    val user: UserDto? = null,
    val error: String? = null,
)

data class GenericResponse(
    val ok: Boolean = false,
    val error: String? = null,
)

data class SearchResult(
    val username: String? = null,
    val appUserId: String? = null,
    val playerName: String? = null,
)

data class SearchResponse(
    val ok: Boolean = false,
    val results: List<SearchResult>? = null,
    val error: String? = null,
)

data class FriendItem(
    val uuid: String? = null,
    val name: String? = null,
    val online: Boolean? = null,
    val appUserId: String? = null,
)

data class FriendListResponse(
    val ok: Boolean = false,
    val friends: List<FriendItem>? = null,
    val error: String? = null,
)

/** /api/friend 各动作的响应：list 带 friends，pending 带 requests。 */
data class FriendActionResponse(
    val ok: Boolean = false,
    val friends: List<FriendItem>? = null,
    val requests: List<SearchResult>? = null,
    val error: String? = null,
)

data class LeaderboardEntry(
    val uuid: String? = null,
    val name: String? = null,
    val balance: Double = 0.0,
)

data class LeaderboardResponse(
    val ok: Boolean = false,
    val top: List<LeaderboardEntry>? = null,
    val error: String? = null,
)

data class TransferResponse(
    val ok: Boolean = false,
    val fromBalance: Double? = null,
    val toBalance: Double? = null,
    val toName: String? = null,
    val error: String? = null,
)
