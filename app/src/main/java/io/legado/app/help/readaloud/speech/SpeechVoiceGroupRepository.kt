package io.legado.app.help.readaloud.speech

import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.ReadAloudSpeakerGroup
import io.legado.app.data.entities.ReadAloudSpeakerGroupItem
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.utils.GSON

data class ManagedSpeechVoiceGroup(
    val group: ReadAloudSpeakerGroup,
    val items: List<ReadAloudSpeakerGroupItem>,
    val routes: List<SpeechRoute>
)

object SpeechVoiceGroupRepository {

    const val INVALID_GROUP_NAME = "失效发言人"

    fun managedGroups(
        httpTtsList: List<HttpTTS> = appDb.httpTTSDao.all,
        enabledOnly: Boolean = true
    ): List<ManagedSpeechVoiceGroup> {
        val groups = if (enabledOnly) {
            appDb.readAloudSpeakerGroupDao.enabledGroups()
        } else {
            appDb.readAloudSpeakerGroupDao.groups()
        }.filterNot { isInvalidGroup(it) }
        if (groups.isEmpty()) return emptyList()
        val blockedKeys = invalidRouteKeys()
        val itemsByGroup = appDb.readAloudSpeakerGroupDao.items().groupBy { it.groupId }
        return groups.mapNotNull { group ->
            val validItems = itemsByGroup[group.id]
                .orEmpty()
                .filter { isValidItem(it, httpTtsList) }
                .filterNot { itemKey(it) in blockedKeys }
            if (validItems.isEmpty()) {
                null
            } else {
                ManagedSpeechVoiceGroup(
                    group = group,
                    items = validItems,
                    routes = validItems.map { it.toSpeechRoute() }.distinctBy {
                        "${it.engineType}|${it.engineValue}|${it.toneID}|${it.speakerName}"
                    }
                )
            }
        }
    }

    fun assignableRoutes(httpTtsList: List<HttpTTS> = appDb.httpTTSDao.all): List<SpeechRoute> {
        return managedGroups(httpTtsList)
            .flatMap { it.routes }
            .filterNot(::isBlockedRoute)
            .distinctBy { "${it.engineType}|${it.engineValue}|${it.toneID}|${it.speakerName}" }
    }

    fun isInvalidGroup(group: ReadAloudSpeakerGroup): Boolean {
        return isInvalidGroupName(group.name)
    }

    fun isInvalidGroupName(name: String): Boolean {
        return name.trim() == INVALID_GROUP_NAME
    }

    fun isBlockedRoute(route: SpeechRoute): Boolean {
        if (!route.isConfigured) return false
        return routeKey(route) in invalidRouteKeys()
    }

    fun isBlockedItem(item: ReadAloudSpeakerGroupItem): Boolean {
        return itemKey(item) in invalidRouteKeys()
    }

    fun markInvalidRoute(route: SpeechRoute?, reason: String = "自动标记失效"): Boolean {
        if (route == null || !route.isConfigured) return false
        return markInvalidItem(
            ReadAloudSpeakerGroupItem(
                engineType = route.engineType,
                engineValue = route.engineValue,
                engineName = route.speakerName,
                speakerName = route.speakerName,
                toneID = route.toneID,
                sourceGroupId = route.groupId,
                sourceGroupName = route.groupName.ifBlank { reason }
            ),
            reason = reason
        )
    }

    fun markInvalidItems(
        items: List<ReadAloudSpeakerGroupItem>,
        reason: String = "自动标记失效"
    ): Int {
        return items.count { markInvalidItem(it, reason) }
    }

    fun markInvalidItem(
        item: ReadAloudSpeakerGroupItem,
        reason: String = "自动标记失效"
    ): Boolean {
        if (item.engineType.isBlank()) return false
        val key = itemKey(item)
        if (key in invalidRouteKeys()) return false
        val now = System.currentTimeMillis()
        val groupId = ensureInvalidGroup(now)
        val sortOrder = (appDb.readAloudSpeakerGroupDao.maxItemOrder(groupId) ?: -1) + 1
        appDb.readAloudSpeakerGroupDao.insertItems(
            listOf(
                item.copy(
                    id = 0L,
                    groupId = groupId,
                    sourceGroupName = item.sourceGroupName.ifBlank { reason },
                    sortOrder = sortOrder,
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
        return true
    }

    fun isValidItem(
        item: ReadAloudSpeakerGroupItem,
        httpTtsList: List<HttpTTS> = appDb.httpTTSDao.all
    ): Boolean {
        return when (item.engineType) {
            SpeechRoute.ENGINE_SYSTEM -> item.speakerName.isNotBlank() || item.engineName.isNotBlank()
            SpeechRoute.ENGINE_HTTP -> {
                val httpTts = item.engineValue.toLongOrNull()
                    ?.let { id -> httpTtsList.firstOrNull { it.id == id } }
                    ?: return false
                val speakers = SpeechVoiceCatalogParser.parseSpeakerGroups(httpTts.speakersJson)
                    .flatMap { it.items }
                when {
                    item.toneID.isBlank() -> true
                    speakers.isEmpty() -> false
                    else -> speakers.any { speaker ->
                        speaker.toneID == item.toneID &&
                            (item.speakerName.isBlank() || speaker.speakerName == item.speakerName)
                    }
                }
            }
            else -> false
        }
    }

    fun itemFromOption(
        groupId: Long,
        option: SpeechVoiceOption,
        sortOrder: Int,
        now: Long = System.currentTimeMillis()
    ): ReadAloudSpeakerGroupItem {
        return ReadAloudSpeakerGroupItem(
            groupId = groupId,
            engineType = option.engineType,
            engineValue = option.engineValue,
            engineName = option.engineName,
            speakerName = option.speakerName,
            toneID = option.toneID,
            sourceGroupId = option.groupId,
            sourceGroupName = option.groupName,
            sortOrder = sortOrder,
            createdAt = now,
            updatedAt = now
        )
    }

    fun systemDefaultItem(
        groupId: Long,
        sortOrder: Int,
        now: Long = System.currentTimeMillis()
    ): ReadAloudSpeakerGroupItem {
        return ReadAloudSpeakerGroupItem(
            groupId = groupId,
            engineType = SpeechRoute.ENGINE_SYSTEM,
            engineValue = GSON.toJson(SelectItem("系统默认", "")),
            engineName = "系统默认",
            speakerName = "系统默认",
            sortOrder = sortOrder,
            createdAt = now,
            updatedAt = now
        )
    }

    fun routeKey(route: SpeechRoute): String {
        return "${route.engineType}|${route.engineValue}|${route.toneID}|${route.speakerName}"
    }

    fun itemKey(item: ReadAloudSpeakerGroupItem): String {
        return "${item.engineType}|${item.engineValue}|${item.toneID}|${item.speakerName}"
    }

    private fun invalidRouteKeys(): Set<String> {
        val invalidGroupIds = appDb.readAloudSpeakerGroupDao.groups()
            .filter { isInvalidGroup(it) }
            .map { it.id }
            .toSet()
        if (invalidGroupIds.isEmpty()) return emptySet()
        return appDb.readAloudSpeakerGroupDao.items()
            .asSequence()
            .filter { it.groupId in invalidGroupIds }
            .map(::itemKey)
            .toSet()
    }

    private fun ensureInvalidGroup(now: Long): Long {
        val existing = appDb.readAloudSpeakerGroupDao.groups()
            .firstOrNull { isInvalidGroup(it) }
        if (existing != null) {
            if (existing.enabled) {
                appDb.readAloudSpeakerGroupDao.updateGroup(
                    existing.copy(enabled = false, updatedAt = now)
                )
            }
            return existing.id
        }
        return appDb.readAloudSpeakerGroupDao.insertGroup(
            ReadAloudSpeakerGroup(
                name = INVALID_GROUP_NAME,
                enabled = false,
                sortOrder = Int.MAX_VALUE,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}

fun ReadAloudSpeakerGroupItem.toSpeechRoute(source: String = SpeechRoute.SOURCE_AUTO): SpeechRoute {
    return SpeechRoute(
        engineType = engineType,
        engineValue = engineValue,
        speakerName = speakerName.ifBlank { engineName },
        toneID = toneID,
        groupId = sourceGroupId,
        groupName = sourceGroupName,
        source = source
    )
}
