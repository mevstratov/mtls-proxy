package ru.tinkoff.integration.openbanking.client;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import ru.CryptoPro.Crypto.CryptoProvider;
import ru.CryptoPro.JCP.JCP;
import ru.CryptoPro.JCSP.JCSP;
import ru.CryptoPro.reprov.RevCheck;
import ru.CryptoPro.ssl.Provider;
import ru.CryptoPro.ssl.util.TLSContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;

public class Client {
    public void connect(URL url, SSLContext context) throws Exception {
        var target = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
        var httpGet = new HttpGet(url.getPath());

        try (var client = HttpAsyncClients.custom()
                .setSSLHostnameVerifier(new NoopHostnameVerifier())
                .setSSLContext(context)
                .build()) {
            client.start();

            var future = client.execute(target, httpGet, null);

        }
    }

    public static void init() {
        System.setProperty("com.sun.security.enableCRLDP", "true");
        System.setProperty("com.ibm.security.enableCRLDP", "true");

        Security.addProvider(new JCP());
        Security.addProvider(new CryptoProvider());
        Security.addProvider(new RevCheck());
        Security.addProvider(new Provider());
    }

    private SSLContext createContext(String trustStorePath, String trustStoreType,
                                     char[] trustStorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance(trustStoreType);

        trustStore.load(new FileInputStream(trustStorePath), trustStorePassword);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("GostX509");
        tmf.init(trustStore);

        var ctx = SSLContext.getInstance("GostTLS");
        ctx.init(null, tmf.getTrustManagers(), null);

        var trustManagers = new TrustManager[1];
        TLSContext.initAuthClientSSL(
                Provider.PROVIDER_NAME,
                "TLSv1.2",
                JCSP.PROVIDER_NAME,
                JCSP.HD_STORE_NAME,
                "ok_client",
                JCP.PROVIDER_NAME,
                JCP.CERT_STORE_NAME,
                trustStorePath,
                "1",
                trustManagers);
        return ctx;
    }
}
