package com.winlator.cmod.core;

import android.util.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.dnsoverhttps.DnsOverHttps;

public final class DohOkHttp {
    private static final String TAG = "DohOkHttp";
    private static volatile OkHttpClient client;

    private DohOkHttp() {
    }

    public static OkHttpClient get() {
        OkHttpClient local = client;
        if (local != null) return local;

        synchronized (DohOkHttp.class) {
            local = client;
            if (local != null) return local;

            OkHttpClient bootstrap = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    // Large images on weak networks can easily exceed 30-40s
                    .readTimeout(3, TimeUnit.MINUTES)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    // Don't abort the call while streaming a large body
                    .callTimeout(0, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .build();

            HttpUrl dohUrl = HttpUrl.get("https://cloudflare-dns.com/dns-query");

            DnsOverHttps.Builder dohBuilder = new DnsOverHttps.Builder()
                    .client(bootstrap)
                    .url(dohUrl)
                    .post(true)
                    .includeIPv6(true);

            try {
                dohBuilder.bootstrapDnsHosts(
                        InetAddress.getByName("1.1.1.1"),
                        InetAddress.getByName("1.0.0.1"),
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("8.8.4.4")
                );
            } catch (UnknownHostException ignored) {
                // No bootstrap hosts; DoH may still work if system DNS can resolve the DoH endpoint.
            }

            DnsOverHttps dohDns = dohBuilder.build();
            Dns fallbackDns = Dns.SYSTEM;

            Dns combinedDns = new Dns() {
                @Override
                public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                    try {
                        List<InetAddress> res = dohDns.lookup(hostname);
                        if (res != null && !res.isEmpty()) return res;
                    } catch (Exception e) {
                        Log.w(TAG, "DoH lookup failed for host=" + hostname + ", falling back to SYSTEM DNS", e);
                    }

                    return fallbackDns.lookup(hostname);
                }
            };

            client = bootstrap.newBuilder()
                    .dns(combinedDns)
                    .protocols(java.util.Collections.singletonList(Protocol.HTTP_1_1))
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request req = original.newBuilder()
                                .header("User-Agent", "Mozilla/5.0 (Android) Winlator.CMOD")
                                .build();
                        return chain.proceed(req);
                    })
                    .build();

            return client;
        }
    }
}
