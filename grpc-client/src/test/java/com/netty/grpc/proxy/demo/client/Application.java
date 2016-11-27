package com.netty.grpc.proxy.demo.client;



public class Application {
    private static int proxyPort = 8443;
    public static void main(String[] args) throws Exception {
        HelloWorldClient client = new HelloWorldClient("localhost", 10511);
        for (int i = 0; i < 1; i++) {
            String user = "world_" + i;
            client.greet(user);
            Thread.sleep(500);


        }
        client.shutdown();

    }
}
