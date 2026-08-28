package com.billing.simple.shell;

import java.io.IOException;
import java.net.ServerSocket;

public class PortUtil {
    /**
     * Finds a free TCP port on the localhost.
     * @return an available port number
     * @throws IOException if unable to open a socket
     */
    public static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
