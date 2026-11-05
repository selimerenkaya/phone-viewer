package io.github.selimerenkaya.phoneviewer.link

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/** This phone's TLS identity: an EC key and self-signed certificate kept in the Android Keystore. */
object Tls {
    private const val ALIAS = "phone-viewer-link"

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
        if (!containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        // TLS signs a ready-made digest (NONE); SHA-256 signs the self-signed certificate.
                        .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                        .setCertificateSubject(X500Principal("CN=Phone Viewer"))
                        .build()
                )
            }.generateKeyPair()
        }
    }
    private val cert = keyStore.getCertificate(ALIAS) as X509Certificate
    private val key = keyStore.getKey(ALIAS, null) as PrivateKey

    val fingerprint: ByteArray = sha256(cert.encoded)

    /** TLS 1.3 server that demands a client certificate. */
    fun serverSocket(port: Int): SSLServerSocket {
        val keyManager = object : X509ExtendedKeyManager() {
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
            override fun getCertificateChain(alias: String?) = arrayOf(cert)
            override fun getPrivateKey(alias: String?) = key
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = null
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = null
        }
        // Any certificate passes TLS; LinkService trusts a PC only by its pinned fingerprint (docs/PROTOCOL.md).
        val pinnedLater = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
        }
        val ctx = SSLContext.getInstance("TLSv1.3").apply { init(arrayOf(keyManager), arrayOf(pinnedLater), null) }
        return (ctx.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
            enabledProtocols = arrayOf("TLSv1.3")
            needClientAuth = true
            reuseAddress = true
            bind(InetSocketAddress(port))
        }
    }
}
