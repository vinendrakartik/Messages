package com.vk.messages.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import ezvcard.VCard
import ezvcard.property.Email
import ezvcard.property.Telephone
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.sendEmailIntent
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import com.vk.messages.R
import com.vk.messages.adapters.VCardViewerAdapter
import com.vk.messages.databinding.ActivityVcardViewerBinding
import com.vk.messages.extensions.dialNumber
import com.vk.messages.helpers.EXTRA_VCARD_URI
import com.vk.messages.helpers.parseVCardFromUri
import com.vk.messages.models.VCardPropertyWrapper
import com.vk.messages.models.VCardWrapper

class VCardViewerActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityVcardViewerBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.contactsList))
        setupMaterialScrollListener(binding.contactsList, binding.vcardAppbar)

        val vCardUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_VCARD_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_VCARD_URI)
        }

        if (vCardUri != null) {
            setupOptionsMenu(vCardUri)
            parseVCardFromUri(this, vCardUri) {
                runOnUiThread {
                    setupContactsList(it)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.vcardAppbar, NavigationIcon.Arrow)
    }

    private fun setupOptionsMenu(vCardUri: Uri) {
        binding.vcardToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_contact -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        val mimetype = contentResolver.getType(vCardUri)
                        setDataAndType(vCardUri, mimetype?.lowercase())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                }

                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupContactsList(vCards: List<VCard>) {
        val items = prepareData(vCards)
        val adapter = VCardViewerAdapter(this, items.toMutableList()) { item ->
            val property = item as? VCardPropertyWrapper
            if (property != null) {
                handleClick(property)
            }
        }
        binding.contactsList.adapter = adapter
    }

    private fun handleClick(property: VCardPropertyWrapper) {
        when (property.property) {
            is Telephone -> dialNumber(property.value.normalizePhoneNumber())
            is Email -> sendEmailIntent(property.value)
        }
    }

    private fun prepareData(vCards: List<VCard>): List<VCardWrapper> {
        return vCards.map { vCard -> VCardWrapper.from(this, vCard) }
    }
}
