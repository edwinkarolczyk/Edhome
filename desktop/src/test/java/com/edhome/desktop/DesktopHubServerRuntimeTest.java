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

        try(DesktopHubServer server=new DesktopHubServer(DESKTOP_ID,"0.7.0.100",TOKEN,host)) {
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

    @Test
    void uninitializedHubReturnsStateWithoutReadingSnapshotAndBootstraps()
            throws Exception {
        java.util.concurrent.atomic.AtomicBoolean initialized =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicInteger snapshotCalls =
            new java.util.concurrent.atomic.AtomicInteger();
        final String[] data = {null};

        DesktopHubServer.Host host=new DesktopHubServer.Host() {
            @Override public String snapshot() {
                snapshotCalls.incrementAndGet();
                if(data[0]==null) throw new IllegalStateException("NO_SNAPSHOT");
                return data[0];
            }
            @Override public String bootstrap(String incoming,
                    DesktopHubServer.ClientInfo client) {
                data[0]=incoming;
                initialized.set(true);
                return incoming;
            }
            @Override public String patch(String incoming,
                    DesktopHubServer.ClientInfo client,boolean phoneWins) {
                return data[0];
            }
            @Override public String replace(String incoming,String baseSha,
                    DesktopHubServer.ClientInfo client) {
                data[0]=incoming;
                initialized.set(true);
                return incoming;
            }
            @Override public long revision(){return initialized.get()?1L:0L;}
            @Override public boolean initialized(){return initialized.get();}
            @Override public void registered(DesktopHubServer.ClientInfo client){}
        };

        try(DesktopHubServer server=new DesktopHubServer(
                DESKTOP_ID,"0.7.0.100",TOKEN,host)) {
            server.start();

            HttpURLConnection state=open("GET","/state",null);
            assertEquals(200,state.getResponseCode());
            String before=read(state);
            assertTrue(before.contains("\"initialized\":false"));
            assertTrue(before.contains("\"snapshotSha256\":\"\""));
            assertTrue(before.contains("\"protocolVersion\":2"));
            assertEquals(0,snapshotCalls.get(),
                "Świeży Hub nie może żądać snapshotu przed bootstrapem.");
            state.disconnect();

            String android042="{\"format\":\"edhome-data-backup\","
                +"\"formatVersion\":1,\"databaseVersion\":45,"
                +"\"sourceVersion\":\"0.8.0.42\","
                +"\"settings\":{},\"syncRecords\":[],\"tables\":{}}";
            HttpURLConnection bootstrap=open("POST","/bootstrap",android042);
            assertEquals(200,bootstrap.getResponseCode());
            String accepted=read(bootstrap);
            assertTrue(accepted.contains("\"sourceVersion\":\"0.8.0.42\""));
            assertTrue(initialized.get());
            bootstrap.disconnect();

            HttpURLConnection after=open("GET","/state",null);
            assertEquals(200,after.getResponseCode());
            String afterBody=read(after);
            assertTrue(afterBody.contains("\"initialized\":true"));
            assertFalse(afterBody.contains("\"snapshotSha256\":\"\""));
            assertTrue(snapshotCalls.get()>0);
            after.disconnect();
        }
    }

    private static HttpURLConnection open(String method,String path,String body)
            throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(
            "http://127.0.0.1:"+DesktopHubServer.PORT+path).openConnection();
        connection.setConnectTimeout(2500);
        connection.setReadTimeout(2500);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("X-EDHOME-TOKEN",TOKEN);
        connection.setRequestProperty("X-EDHOME-DEVICE-ID","runtime-smoke");
        connection.setRequestProperty("X-EDHOME-ANDROID-VERSION","0.8.0.42");
        if(body!=null) {
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type",
                "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try(java.io.OutputStream output=connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        return connection;
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
