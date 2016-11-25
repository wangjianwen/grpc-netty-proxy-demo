package com.netty.grpc.proxy.demo.handler.server;


public class Application1 {
    private static int port = 10511;

    public static void main(String[] args) throws Exception {
        final HelloWorldServer server = new HelloWorldServer(port);
        server.start();
        server.blockUntilShutdown();

    }
}
