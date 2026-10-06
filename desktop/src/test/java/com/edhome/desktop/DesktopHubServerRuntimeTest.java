package com.edhome.desktop;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

final class DesktopHubServerRuntimeTest {
    private static final String DESKTOP_ID="11111111-2222-3333-4444-555555555555";
    private static final String TOKEN="test_token_12345678901234567890";

    @Test
    void servesAuthenticatedStatusRejectsBadTokenAndAnswersDiscovery() throws Exception {
        DesktopHubServer.Host host=new DesktopHubServer.Host() {
            @Override public String snapshot(){return "{\"schema\":1}";}
            @Override public String bootstrap(String incoming,DesktopHubServer.ClientInfo client){return incoming;}
            @Override public String patch(String incoming,DesktopHubServer.ClientInfo client,boolean phoneWins){return "{\"schema\":1}";}
            @Override public String replace(String incoming,String baseSha,DesktopHubServer.ClientInfo client){return incoming;}
            @Override public long revision(){return 7L;}
            @Override public boolean initialized(){return true;}
            @Override public void registered(DesktopHubServer.ClientInfo client){}
        };

        try(DesktopHubServer server=new DesktopHubServer(DESKTOP_ID,"0.7.0.99",TOKEN,host)) {
            server.start();

            HttpURLConnection ok=openStatus(TOKEN);
            assertEquals(200,ok.getResponseCode());
            String body=read(ok);
            assertTrue(body.contains("\"desktopId\":\""+DESKTOP_ID+"\""));
            assertTrue(body.contains("\"version\":\"0.7.0.99\""));
            assertTrue(body.contains("\"port\":"+DesktopHubServer.PORT));
            ok.disconnect();

            HttpURLConnection denied=openStatus("wrong_token_12345678901234567890");
            assertEquals(401,denied.getResponseCode());
            denied.disconnect();

            try(DatagramSocket udp=new DatagramSocket()) {
                udp.setSoTimeout(2500);
                byte[] query=("EDHOME_DISCOVER_V1 "+DESKTOP_ID)
                    .getBytes(StandardCharsets.US_ASCII);
                udp.send(new DatagramPacket(query,query.length,
                    InetAddress.getByName("127.0.0.1"),DesktopHubServer.DISCOVERY_PORT));
                byte[] buffer=new byte[512];
                DatagramPacket reply=new DatagramPacket(buffer,buffer.length);
                udp.receive(reply);
                String text=new String(reply.getData(),reply.getOffset(),reply.getLength(),
                    StandardCharsets.US_ASCII);
                assertEquals("EDHOME_DESKTOP_V1|"+DESKTOP_ID+"|"
                    +DesktopHubServer.PORT+"|0.7.0.99",text);
            }
        }
    }

    private static HttpURLConnection openStatus(String token) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(
            "http://127.0.0.1:"+DesktopHubServer.PORT+"/status").openConnection();
        connection.setConnectTimeout(2500);
        connection.setReadTimeout(2500);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("X-EDHOME-TOKEN",token);
        connection.setRequestProperty("X-EDHOME-DEVICE-ID","runtime-smoke");
        connection.setRequestProperty("X-EDHOME-ANDROID-VERSION","test");
        return connection;
    }

    private static String read(HttpURLConnection connection) throws Exception {
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                connection.getInputStream(),StandardCharsets.UTF_8))) {
            StringBuilder out=new StringBuilder();
            for(String line;(line=reader.readLine())!=null;)out.append(line);
            return out.toString();
        }
    }
}
