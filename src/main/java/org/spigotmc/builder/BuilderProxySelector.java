package org.spigotmc.builder;

import com.google.common.collect.Lists;

import java.io.IOException;
import java.net.*;
import java.util.List;

public class BuilderProxySelector extends ProxySelector {
    private boolean proxyAvailable = false;
    private String proxyAddress = "";
    private int proxyPort = 0;

    public BuilderProxySelector() {
    }

    public BuilderProxySelector(String address, int port) {
        this(true, address, port);
    }

    public BuilderProxySelector(boolean proxyAvailable, String address, int port) {
        this.proxyAvailable = proxyAvailable;
        this.proxyAddress = address;
        this.proxyPort = port;
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (proxyAvailable) {
            try {
                Proxy proxy = ProxyHelper.newHTTPProxy(proxyAddress, proxyPort);
                return Lists.asList(proxy, new Proxy[0]);
            } catch (UnknownHostException e) {
                throw new RuntimeException(String.format("Cannot find the target proxy address: %s", proxyAddress), e);
            }
        } else {
            return Lists.asList(Proxy.NO_PROXY, new Proxy[0]);
        }
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        System.err.println("Failed to connect to the target proxy. Please ensure the proxy given exists.");
        ioe.printStackTrace();
    }
}
