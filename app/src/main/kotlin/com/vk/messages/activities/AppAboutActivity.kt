package com.vk.messages.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.vk.messages.BuildConfig
import com.vk.messages.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.models.FAQItem
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AppAboutActivity : BaseSimpleActivity() {

    override fun getAppIconIDs() = arrayListOf(R.mipmap.ic_launcher)
    override fun getAppLauncherName() = getString(R.string.app_name)
    override fun getRepositoryName() = "vinendrakartik/Messages"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_about)

        findViewById<Toolbar>(R.id.about_toolbar).setNavigationOnClickListener { finish() }

        val versionTextView = findViewById<TextView>(R.id.about_version_text)
        val packageTextView = findViewById<TextView>(R.id.about_package_text)
        val forkTextView = findViewById<TextView>(R.id.about_fork)
        val shareTextView = findViewById<TextView>(R.id.about_share)
        val releasesTextView = findViewById<TextView>(R.id.about_releases)
        val versionName = BuildConfig.VERSION_NAME
        val appName = getString(R.string.app_name)


        versionTextView.text = "$appName ${getString(R.string.version)} $versionName"
        packageTextView.text = BuildConfig.APPLICATION_ID
        forkTextView.text = getString(R.string.fork_from)

        // CHECK FOR UPDATES ON CLICK
        versionTextView.setOnClickListener {
            checkAppUpdate(versionName)
        }

        shareTextView.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, appName)
            val shareMessage = "Check out $appName, a fork of Fossify Messages with additional great features: https://github.com/vinendrakartik/Messages"
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        releasesTextView.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/vinendrakartik/Messages/releases".toUri())
            startActivity(intent)
        }

        setupFAQ(
            arrayListOf(
                FAQItem(R.string.faq_2_title, R.string.faq_2_text),
                FAQItem(R.string.faq_3_title, R.string.faq_3_text),
                FAQItem(R.string.faq_4_title, R.string.faq_4_text),
                FAQItem(R.string.faq_auto_copy_title, R.string.faq_auto_copy_text),
                FAQItem(R.string.faq_disable_tts_title, R.string.faq_disable_tts_text),
                FAQItem(R.string.faq_tts_voice_title, R.string.faq_tts_voice_text),
                FAQItem(R.string.faq_mute_title, R.string.faq_mute_text)
            )
        )
    }

    private fun setupFAQ(faqItems: ArrayList<FAQItem>) {
        val faqHolder = findViewById<LinearLayout>(R.id.item_faq)
        faqHolder.removeAllViews()
        val inflater = layoutInflater

        for (faq in faqItems) {
            val view = inflater.inflate(R.layout.item_faq, faqHolder, false)
            val titleView = view.findViewById<TextView>(R.id.faq_title)
            val textView = view.findViewById<TextView>(R.id.faq_text)

            titleView.text = getString(faq.title as Int)
            textView.text = getString(faq.text as Int)

            view.setOnClickListener {
                textView.isVisible = !textView.isVisible
            }
            faqHolder.addView(view)
        }
    }

    // --- UPDATE LOGIC START ---

    private fun checkAppUpdate(currentVersion: String) {
        val repo = getRepositoryName()
        if (repo.isEmpty()) return

        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()

        thread {
            try {
                val url = URL("https://api.github.com/repos/$repo/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Messages")

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val latestTag = json.getString("tag_name")

                    // FIND APK URL IN ASSETS
                    var apkUrl = ""
                    val assets = json.getJSONArray("assets")
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    Handler(Looper.getMainLooper()).post {
                        if (isUpdateAvailable(currentVersion, latestTag) && apkUrl.isNotEmpty()) {
                            showUpdateDialog(latestTag, apkUrl)
                        } else {
                            Toast.makeText(this, "You have the latest version", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Check failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(version: String, url: String) {
        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("Version $version is available. Download and install now?")
            .setPositiveButton("Update") { _, _ ->
                downloadAPK(url)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadAPK(urlStr: String) {
        // Android 8+ requires permission check for installing packages
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "Please allow permission to install updates", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()))
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_update_progress, null)
        val progressBar = dialogView.findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        val progressPercent = dialogView.findViewById<TextView>(R.id.progress_percent)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Updating")
            .setView(dialogView)
            .setCancelable(false) // Prevent user from closing it by accident
            .create()

        dialog.show()

        thread {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                val fileLength = connection.contentLength
                val input = connection.inputStream

                val file = File(externalCacheDir, "update.apk")
                val output = FileOutputStream(file)

                val data = ByteArray(1024)
                var total: Long = 0
                var count: Int

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)

                    // FIX: Only calculate progress if we know the file size
                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()

                        Handler(Looper.getMainLooper()).post {
                            progressBar.progress = progress
                            progressPercent.text = "$progress%"
                        }
                    } else {
                        // Optional: Show indeterminate state if size is unknown
                        Handler(Looper.getMainLooper()).post {
                            progressPercent.text = "${total / 1024} KB"
                        }
                    }
                }
                output.close()
                input.close()

                Handler(Looper.getMainLooper()).post {
                    dialog.dismiss()
                    installAPK(file)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    dialog.dismiss()
                    Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installAPK(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isUpdateAvailable(current: String, latest: String): Boolean {
        // Logic to compare versions (e.g. 1.0.0 vs 1.0.1)
        // Returns true if latest > current
        val c = current.replace(Regex("[^0-9.]"), "")
        val l = latest.replace(Regex("[^0-9.]"), "")
        return l.compareTo(c) > 0
    }
}
