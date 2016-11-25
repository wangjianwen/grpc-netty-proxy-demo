package com.netty.grpc.proxy.demo.client;



public class Application {
    private static int proxyPort = 8443;
    public static void main(String[] args) throws Exception {

        for (int i = 0; i < 10; i++) {
            HelloWorldClient client = new HelloWorldClient("localhost", proxyPort);
            try {
                String user = "world_" + i;
                client.greet(user);
                Thread.sleep(500);

            } finally {
                client.shutdown();
            }

        }

    }
}
