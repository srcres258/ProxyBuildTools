package org.spigotmc.builder;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;

public class ProxyHelper {
    public static Proxy newHTTPProxy(String address, int port) throws UnknownHostException {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(Inet4Address.getByName(address), port));
    }
}
