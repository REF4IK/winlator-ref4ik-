package com.winlator.cmod.core

import android.content.SharedPreferences
import com.tencent.mmkv.MMKV

class MmkvPreferences(name: String? = null) : SharedPreferences {
    private val mmkv: MMKV = if (name != null) MMKV.mmkvWithID(name) else MMKV.defaultMMKV()

    companion object {
        private val globalListeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        private val listenerLock = Any()

        fun registerGlobalListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            synchronized(listenerLock) { globalListeners.add(listener) }
        }

        fun unregisterGlobalListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            synchronized(listenerLock) { globalListeners.remove(listener) }
        }

        fun notifyGlobalListeners(prefs: SharedPreferences, keys: Set<String>) {
            val snapshot: List<SharedPreferences.OnSharedPreferenceChangeListener>
            synchronized(listenerLock) { snapshot = globalListeners.toList() }
            for (key in keys) {
                for (listener in snapshot) {
                    listener.onSharedPreferenceChanged(prefs, key)
                }
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? = mmkv.decodeString(key, defValue)
    override fun getInt(key: String, defValue: Int): Int = mmkv.decodeInt(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = mmkv.decodeLong(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = mmkv.decodeBool(key, defValue)
    override fun getFloat(key: String, defValue: Float): Float = mmkv.decodeFloat(key, defValue)
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        val raw = mmkv.decodeStringSet(key) ?: return defValues
        return raw
    }
    override fun contains(key: String): Boolean = mmkv.contains(key)
    override fun getAll(): Map<String, *> = throw UnsupportedOperationException("getAll is not supported by MmkvPreferences")
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        registerGlobalListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        unregisterGlobalListener(listener)
    }

    override fun edit(): SharedPreferences.Editor = EditorM(this, mmkv)
    private class EditorM(
        private val prefs: MmkvPreferences,
        private val mmkv: MMKV,
    ) : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply {
            pending[key] = values
        }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { pending.clear() }

        override fun commit(): Boolean {
            apply()
            return true
        }

        @Suppress("UNCHECKED_CAST")
        override fun apply() {
            val changedKeys = mutableSetOf<String>()
            for ((key, value) in pending) {
                if (value != null) {
                    when (value) {
                        is String -> mmkv.encode(key, value)
                        is Set<*> -> mmkv.encode(key, value as Set<String>)
                        is Int -> mmkv.encode(key, value)
                        is Long -> mmkv.encode(key, value)
                        is Boolean -> mmkv.encode(key, value)
                        is Float -> mmkv.encode(key, value)
                    }
                } else {
                    mmkv.remove(key)
                }
                changedKeys.add(key)
            }
            mmkv.sync()
            pending.clear()
            if (changedKeys.isNotEmpty()) {
                notifyGlobalListeners(prefs, changedKeys)
            }
        }
    }
}
