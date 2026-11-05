package io.github.selimerenkaya.phoneviewer.link

import android.content.Context
import android.net.ConnectivityManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.Inet4Address

/** Shows this phone's Wi-Fi address, the link status and, while a PC is pairing, its code with Allow/Deny. */
class LinkPanel(context: Context) : LinearLayout(context) {
    private val address = TextView(context).apply { textSize = 16f }
    private val status = TextView(context).apply { textSize = 16f; setPadding(0, 24, 0, 0) }
    private val code = TextView(context).apply { textSize = 28f; setPadding(0, 24, 0, 24) }
    private val buttons = LinearLayout(context).apply {
        addView(Button(context).apply { text = "Allow"; setOnClickListener { LinkService.decide(true) } })
        addView(Button(context).apply { text = "Deny"; setOnClickListener { LinkService.decide(false) } })
    }

    init {
        orientation = VERTICAL
        setPadding(48, 260, 48, 48) // sits below MainActivity's title
        listOf(address, status, code, buttons).forEach(::addView)
    }

    private fun refresh() {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val ip = cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
        address.text = if (ip != null) "Type this on the PC: $ip" else "Connect this phone to Wi-Fi"
        status.text = LinkService.status
        val pairing = LinkService.code
        code.text = "$pairing\nAllow only if the PC shows the same code."
        code.visibility = if (pairing != null) View.VISIBLE else View.GONE
        buttons.visibility = code.visibility
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        LinkService.onChange = { post(::refresh) }
        refresh()
    }

    override fun onDetachedFromWindow() {
        LinkService.onChange = null
        super.onDetachedFromWindow()
    }
}
