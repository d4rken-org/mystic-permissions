package eu.darken.myperm.settings.ui.support

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.myperm.R
import eu.darken.myperm.common.ClipboardHelper
import eu.darken.myperm.common.observe2
import eu.darken.myperm.common.uix.PreferenceFragment2
import eu.darken.myperm.settings.core.GeneralSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SupportFragment : PreferenceFragment2() {

    private val vm: SupportFragmentVM by viewModels()

    override val preferenceFile: Int = R.xml.preferences_support
    @Inject lateinit var generalSettings: GeneralSettings

    override val settings: GeneralSettings by lazy { generalSettings }

    @Inject lateinit var clipboardHelper: ClipboardHelper

    private val debugLogPref by lazy { findPreference<Preference>("support.debuglog")!! }

    override fun onPreferencesCreated() {
        debugLogPref.setOnPreferenceClickListener {
            vm.startDebugLog()
            true
        }
        super.onPreferencesCreated()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.isRecording.observe2(this) {
            debugLogPref.isEnabled = !it
        }

        super.onViewCreated(view, savedInstanceState)
    }
}