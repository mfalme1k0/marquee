package com.marquee;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

/** Entry point: starts the HTTP server and listens until stopped. */
public final class Main {

    private Main() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // TODO : init DB (schema + PRAGMA foreign_keys = ON), register router.
        server.start();
        System.out.println("Marquee listening on :" + port);
    }
}
