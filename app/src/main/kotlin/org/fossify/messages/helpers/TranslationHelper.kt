package org.fossify.messages.helpers

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import java.util.Locale

class TranslationHelper(private val context: Context) {

    companion object {
        private const val TAG = "TranslationHelper"
        private var instance: TranslationHelper? = null

        fun getInstance(context: Context): TranslationHelper {
            if (instance == null) {
                instance = TranslationHelper(context)
            }
            return instance!!
        }
    }

    fun translate(text: String, callback: (String) -> Unit) {
        if (!context.config.autoTranslate || text.isBlank()) {
            callback(text)
            return
        }

        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (languageCode == "und") {
                    Log.d(TAG, "Language unidentified for text: $text")
                    callback(text)
                } else {
                    Log.d(TAG, "Identified language: $languageCode")
                    performTranslation(text, languageCode, callback)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Language identification failed", e)
                callback(text)
            }
    }

    private fun performTranslation(text: String, sourceLanguageCode: String, callback: (String) -> Unit) {
        val targetLanguageCode = Locale.getDefault().language
        Log.d(TAG, "Attempting translation from $sourceLanguageCode to $targetLanguageCode")
        
        val sourceLanguage = TranslateLanguage.fromLanguageTag(sourceLanguageCode)
        val targetLanguage = TranslateLanguage.fromLanguageTag(targetLanguageCode) ?: TranslateLanguage.ENGLISH

        if (sourceLanguage == null || sourceLanguage == targetLanguage) {
            Log.d(TAG, "Source language $sourceLanguageCode is either unsupported or same as target $targetLanguageCode")
            callback(text)
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()

        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder()
            .build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                Log.d(TAG, "Model downloaded or already present for $sourceLanguageCode -> $targetLanguageCode")
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        Log.d(TAG, "Translation successful: $translatedText")
                        callback(translatedText)
                        translator.close()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Translation failed", e)
                        callback(text)
                        translator.close()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Model download failed", e)
                // Notify user that translation models are downloading or failed
                // Only show toast once in a while or handle silently
                callback(text)
                translator.close()
            }
    }
}
