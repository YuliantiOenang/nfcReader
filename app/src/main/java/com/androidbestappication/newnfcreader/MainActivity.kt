package com.androidbestappication.newnfcreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.*
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_WIRELESS_SETTINGS
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import com.androidbestappication.newnfcreader.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    val error: String = "No NFC Tag detected"
    val writeSuccess: String = "Text Written Successfully"
    val writeError: String = "Error during writing. Try again!"
    var nfcAdapter: NfcAdapter? = null
    lateinit var pendingIntent: PendingIntent
    var writingTagFilter = arrayOf<IntentFilter>()
    var writeMode by Delegates.notNull<Boolean>()
    var tag: Tag? = null

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        binding.buttonFirst.setOnClickListener {
            try {
                if (tag == null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } else {
                    write("PlainText|" + binding.edittextMultiLine.text, tag!!)
                    Toast.makeText(this, writeSuccess, Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this, writeError, Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } catch (e: FormatException) {
                Toast.makeText(this, writeError, Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "This Device didn't support NFC", Toast.LENGTH_LONG).show()
            this.finish()
        }
        readFromIntent(intent)
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        val tagDetected = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT)
        writingTagFilter = arrayOf(tagDetected)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        readFromIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent?.action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(
                intent?.action
            )
        ) {
            tag = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }

    private fun readFromIntent(intent: Intent?) {
        val action = intent?.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
            || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
            || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
        ) {
            val rawMsg = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            val msg = arrayOfNulls<NdefMessage>(rawMsg?.size ?: 0)
            rawMsg?.forEachIndexed { index, element ->
                msg[index] = element as NdefMessage
            }
            buildTagViews(msg)
        }
    }

    private fun buildTagViews(msg: Array<NdefMessage?>) {
        if (msg.isEmpty()) {
            return
        }
        var text = ""
        var payload = msg[0]!!.records[0].payload
        val textEncoding = if ((payload[0].toInt() and 128) == 0) {
            charset("UTF-8")
        } else {
            charset("UTF-16")
        }
        val languageCodeLength = (payload[0].toInt() and 63)

        try {
            text = String(
                payload,
                languageCodeLength + 1,
                payload.size - languageCodeLength - 1,
                textEncoding
            )
        } catch (e: UnsupportedEncodingException) {
            //Log.e("UnsupportedEncodingException", e.toString())
        }
        binding.textviewFirst.text = "NFC content:$text"
    }

    @Throws(IOException::class, FormatException::class)
    fun write(text: String, tag: Tag) {
        var records = arrayOf(createRecord(text))
        var message = NdefMessage(records)
        var ndef = Ndef.get(tag)
        ndef.connect()
        ndef.writeNdefMessage(message)
        ndef.close()
    }

    @Throws(UnsupportedEncodingException::class)
    fun createRecord(text: String): NdefRecord {
        var lang = "en"
        var textByte = text.toByteArray()
        var langByte = lang.toByteArray(Charset.forName("US-ASCII"))
        var langLength = langByte.size
        var textLength = textByte.size
        var payload = ByteArray(1 + langLength + textLength)
        payload[0] = langLength.toByte()

        System.arraycopy(langByte, 0, payload, 1, langLength)
        System.arraycopy(textByte, 0, payload, 1 + langLength, textLength)
        var recordNfc =
            NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
        return recordNfc
    }

    override fun onPause() {
        super.onPause()
        writeModeOff()
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter != null) {
            if (!nfcAdapter?.isEnabled!!)
                showWirelessSettings();

            writeModeOn()
        }
    }

    private fun showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        val intent = Intent(ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    private fun writeModeOff() {
        writeMode = false
        nfcAdapter?.disableForegroundDispatch(this)
    }

    private fun writeModeOn() {
        writeMode = true
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }
}