package com.devpulse.app.di

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.net.ssl.SSLPeerUnverifiedException

class CertificatePinningIntegrationTest {
    @Test
    fun pinnedHttpsCall_succeeds_withMatchingCertificatePin() {
        val localhostCertificate = createLocalhostCertificate()
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
        val localhostCertificate = createLocalhostCertificate()
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

    private fun createPinnedClient(
        trustedCertificates: HandshakeCertificates,
        pin: String,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory(
                trustedCertificates.sslSocketFactory(),
                trustedCertificates.trustManager,
            )
            .certificatePinner(
                CertificatePinner.Builder()
                    .add("localhost", pin)
                    .build(),
            )
            .build()
    }

    private fun createLocalhostCertificate(): LocalhostCertificateBundle {
        val heldCertificate =
            HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
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

    private data class LocalhostCertificateBundle(
        val serverCertificates: HandshakeCertificates,
        val clientCertificates: HandshakeCertificates,
        val pin: String,
    )
}
