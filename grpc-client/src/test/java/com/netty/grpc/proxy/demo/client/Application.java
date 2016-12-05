package com.netty.grpc.proxy.demo.client;



public class Application {
    private static int proxyPort = 8443;
    public static void main(String[] args) throws Exception {


//        String user = "world_" ;
//        client.greet(user);

       for (int i = 0; i < 4; i++) {
           HelloWorldClient client = new HelloWorldClient("localhost", proxyPort);
            String user = "world_" + i;
            client.greet(user);

           client.shutdown();
        }


        //



    }
}
