package wtf.buddyworks.wireguardtrigger.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONArray
import org.json.JSONObject

/** Settings and the activity list, kept in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("trigger", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(
        Config(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            tunnel = prefs.getString(KEY_TUNNEL, null).orEmpty(),
            trusted = prefs.getStringSet(KEY_TRUSTED, null)?.toSet().orEmpty(),
            offOnTrusted = prefs.getBoolean(KEY_OFF_ON_TRUSTED, true),
        ),
    )
    val config: StateFlow<Config> = _config.asStateFlow()

    private val _remoteControlConfirmed = MutableStateFlow(prefs.getBoolean(KEY_CONFIRMED, false))

    /** True once WireGuard has reacted to one of our commands, so remote control is on. */
    val remoteControlConfirmed: StateFlow<Boolean> = _remoteControlConfirmed.asStateFlow()

    private val _activity = MutableStateFlow(loadActivity())
    val activity: StateFlow<List<ActivityEntry>> = _activity.asStateFlow()

    fun setEnabled(enabled: Boolean) = updateConfig { it.copy(enabled = enabled) }

    fun setTunnel(name: String) = updateConfig { it.copy(tunnel = name.trim()) }

    fun setOffOnTrusted(value: Boolean) = updateConfig { it.copy(offOnTrusted = value) }

    fun addTrusted(ssid: String) {
        val name = ssid.trim()
        if (name.isNotEmpty()) updateConfig { it.copy(trusted = it.trusted + name) }
    }

    fun removeTrusted(ssid: String) = updateConfig { it.copy(trusted = it.trusted - ssid) }

    fun setRemoteControlConfirmed(value: Boolean) {
        if (_remoteControlConfirmed.value == value) return
        _remoteControlConfirmed.value = value
        prefs.edit { putBoolean(KEY_CONFIRMED, value) }
    }

    fun log(entry: ActivityEntry) {
        _activity.update { (listOf(entry) + it).take(MAX_ACTIVITY) }
        prefs.edit { putString(KEY_ACTIVITY, JSONArray(_activity.value.map { it.toJson() }).toString()) }
    }

    fun clearActivity() {
        _activity.value = emptyList()
        prefs.edit { remove(KEY_ACTIVITY) }
    }

    private fun updateConfig(change: (Config) -> Config) {
        val next = _config.updateAndGet(change)
        prefs.edit {
            putBoolean(KEY_ENABLED, next.enabled)
            putString(KEY_TUNNEL, next.tunnel)
            putStringSet(KEY_TRUSTED, next.trusted)
            putBoolean(KEY_OFF_ON_TRUSTED, next.offOnTrusted)
        }
    }

    private fun loadActivity(): List<ActivityEntry> {
        val raw = prefs.getString(KEY_ACTIVITY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { activityFromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun ActivityEntry.toJson() = JSONObject().apply {
        put("time", time)
        put("kind", kind.name)
        put("tunnel", tunnel)
        if (link != null) {
            put("link", link.type.name)
            if (link.ssid != null) put("ssid", link.ssid)
        }
    }

    private fun activityFromJson(o: JSONObject): ActivityEntry? {
        val kind = runCatching { ActivityEntry.Kind.valueOf(o.getString("kind")) }.getOrNull() ?: return null
        val type = o.optString("link").takeIf { it.isNotEmpty() }?.let { name -> LinkType.entries.firstOrNull { it.name == name } }
        val link = type?.let { Link(it, o.optString("ssid").takeIf { s -> o.has("ssid") && s.isNotEmpty() }) }
        return ActivityEntry(o.getLong("time"), kind, link, o.optString("tunnel"))
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_TUNNEL = "tunnel"
        const val KEY_TRUSTED = "trusted"
        const val KEY_OFF_ON_TRUSTED = "off_on_trusted"
        const val KEY_CONFIRMED = "remote_control_confirmed"
        const val KEY_ACTIVITY = "activity"
        const val MAX_ACTIVITY = 50
    }
}

