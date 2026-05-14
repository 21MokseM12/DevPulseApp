package com.devpulse.app.di

import okhttp3.CertificatePinner
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import javax.net.ssl.SSLPeerUnverifiedException

class CertificatePinningIntegrationTest {
    @Test
    fun pinnedHttpsCall_succeeds_withMatchingCertificatePin() {
        val localhostCertificate = createCertificateForHost("localhost")
        MockWebServer().use { server ->
            server.useHttps(localhostCertificate.serverCertificates.sslSocketFactory(), false)
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

            val client =
                createPinnedClient(
                    trustedCertificates = localhostCertificate.clientCertificates,
                    pin = localhostCertificate.pin,
                )

            client.newCall(
                Request.Builder()
                    .url(server.url("/health"))
                    .build(),
            ).execute().use { response ->
                assertEquals(200, response.code)
            }
        }
    }

    @Test
    fun pinnedHttpsCall_fails_withMismatchedCertificatePin() {
        val localhostCertificate = createCertificateForHost("localhost")
        MockWebServer().use { server ->
            server.useHttps(localhostCertificate.serverCertificates.sslSocketFactory(), false)
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val client =
                createPinnedClient(
                    trustedCertificates = localhostCertificate.clientCertificates,
                    pin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                )

            try {
                client.newCall(
                    Request.Builder()
                        .url(server.url("/health"))
                        .build(),
                ).execute().use { }
                throw AssertionError("Expected SSLPeerUnverifiedException for mismatched pin.")
            } catch (exception: SSLPeerUnverifiedException) {
                assertTrue(exception.message?.contains("pinning") == true)
            }
        }
    }

    @Test
    fun pinnedHttpsCall_succeeds_forSubdomainWithWildcardPin() {
        val wildcardCertificate = createCertificateForHost("api.localhost")
        MockWebServer().use { server ->
            server.useHttps(wildcardCertificate.serverCertificates.sslSocketFactory(), false)
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val client =
                createPinnedClient(
                    trustedCertificates = wildcardCertificate.clientCertificates,
                    pin = wildcardCertificate.pin,
                    hostPattern = "*.localhost",
                    dns = loopbackDns(),
                )
            val subdomainUrl = server.url("/health").newBuilder().host("api.localhost").build()

            client.newCall(
                Request.Builder()
                    .url(subdomainUrl)
                    .build(),
            ).execute().use { response ->
                assertEquals(200, response.code)
            }
        }
    }

    private fun createPinnedClient(
        trustedCertificates: HandshakeCertificates,
        pin: String,
        hostPattern: String = "localhost",
        dns: Dns? = null,
    ): OkHttpClient {
        val builder =
            OkHttpClient.Builder()
                .sslSocketFactory(
                    trustedCertificates.sslSocketFactory(),
                    trustedCertificates.trustManager,
                )
                .certificatePinner(
                    CertificatePinner.Builder()
                        .add(hostPattern, pin)
                        .build(),
                )
        dns?.let(builder::dns)
        return builder.build()
    }

    private fun createCertificateForHost(host: String): LocalhostCertificateBundle {
        val heldCertificate =
            HeldCertificate.Builder()
                .commonName(host)
                .addSubjectAlternativeName(host)
                .build()
        val serverCertificates =
            HandshakeCertificates.Builder()
                .heldCertificate(heldCertificate)
                .build()
        val clientCertificates =
            HandshakeCertificates.Builder()
                .addTrustedCertificate(heldCertificate.certificate)
                .build()
        return LocalhostCertificateBundle(
            serverCertificates = serverCertificates,
            clientCertificates = clientCertificates,
            pin = CertificatePinner.pin(heldCertificate.certificate),
        )
    }

    private fun loopbackDns(): Dns {
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return if (hostname.endsWith(".localhost")) {
                    listOf(InetAddress.getByName("127.0.0.1"))
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }
    }

    private data class LocalhostCertificateBundle(
        val serverCertificates: HandshakeCertificates,
        val clientCertificates: HandshakeCertificates,
        val pin: String,
    )
}
