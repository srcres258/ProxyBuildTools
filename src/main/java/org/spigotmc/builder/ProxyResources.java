package org.spigotmc.builder;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;

public class ProxyResources {
    public static byte[] toByteArray(URL url, String ip, int port) throws IOException {
        return toByteArray(url, ProxyHelper.newHTTPProxy(ip, port));
    }

    public static byte[] toByteArray(URL url, Proxy proxy) throws IOException {
        Closer closer = Closer.create();
        byte[] result;
        try {
            InputStream in = closer.register(url.openConnection(proxy).getInputStream());
            result = ByteStreams.toByteArray(in);
        } finally {
            closer.close();
        }
        return result;
    }
}
