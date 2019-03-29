package ru.anisart.vv

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup

class StyleSettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var listener: OnIconClickListener? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_map, rootKey)
    }

//    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
//        val view = inflater.inflate(R.layout.fragment_preference_style, container, false)
////        view.setBackgroundColor(Color.WHITE)
//        val toolbar = view.findViewById<Toolbar>(R.id.styleToolbar)
//        toolbar.setNavigationIcon(R.drawable.ic_back)
//        toolbar.setNavigationOnClickListener { listener?.onIconClick() }
//        return view
//    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String) {
        updatePreference(findPreference(key))
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        for (i in 0 until preferenceScreen.preferenceCount) {
            val preference = preferenceScreen.getPreference(i)
            preference.isIconSpaceReserved = false
            if (preference is PreferenceGroup) {
                for (j in 0 until preference.preferenceCount) {
                    val singlePref = preference.getPreference(j)
                    singlePref.isIconSpaceReserved = false
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