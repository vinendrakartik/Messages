package org.fossify.messages.activities

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import org.fossify.commons.activities.CustomizationActivity
import org.fossify.commons.extensions.beGone

// CustomizationActivity in commons library is final, so we'll use a different strategy.
// We'll keep this file for future reference, but we won't use it in the manifest.
open class CustomCustomizationActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
