package ru.anisart.vv

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceGroup
import android.support.v7.widget.Toolbar

class StyleSettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var listener: OnIconClickListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preference_map)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_preference_style, container, false)
//        view.setBackgroundColor(Color.WHITE)
        val toolbar = view.findViewById<Toolbar>(R.id.styleToolbar)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { listener?.onIconClick() }
        return view
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        updatePreference(findPreference(key))
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        for (i in 0 until preferenceScreen.preferenceCount) {
            val preference = preferenceScreen.getPreference(i)
            if (preference is PreferenceGroup) {
                for (j in 0 until preference.preferenceCount) {
                    val singlePref = preference.getPreference(j)
                    updatePreference(singlePref)
                }
            } else {
                updatePreference(preference)
            }
        }
    }

    override fun onPause() {
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    private fun updatePreference(preference: Preference?) {
        preference ?: return
        if (preference is ListPreference) {
            preference.summary = preference.entry
            return
        }
    }

    fun setOnIconClickListener(listener: OnIconClickListener) {
        this.listener = listener
    }

    interface OnIconClickListener {
        fun onIconClick()
    }
}