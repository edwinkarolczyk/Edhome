package com.edhome.desktop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lokalny Hub/API EDHOME Desktop.
 *
 * Telefon jest klientem. API nie jest wystawiane do Internetu: akceptowane są
 * wyłącznie adresy site-local/loopback i token z QR. Raspberry Pi może później
 * przejąć ten sam kontrakt bez zmiany modelu danych.
 */
final class DesktopHubServer implements AutoCloseable {
    static final int PORT=45823;
    static final int DISCOVERY_PORT=45822;
    private static final int MAX_BODY=8*1024*1024;

    interface Host {
        String snapshot() throws Exception;
        String bootstrap(String incoming,ClientInfo client) throws Exception;
        String patch(String incoming,ClientInfo client,boolean phoneWins)
            throws Exception;
        long revision() throws Exception;
        boolean initialized();
        void registered(ClientInfo client);
    }

    static final class ClientInfo {
        final String deviceId;
        final String userId;
        final String userName;
        final String version;
        final String address;
        final long seenAt;
        ClientInfo(String deviceId,String userId,String userName,String version,
                String address,long seenAt) {
            this.deviceId=deviceId;
            this.userId=userId;
            this.userName=userName;
            this.version=version;
            this.address=address;
            this.seenAt=seenAt;
        }
    }

    private final String desktopId;
    private final String desktopVersion;
    private final String token;
    private final Host host;
    private final Map<String,ClientInfo> clients=new ConcurrentHashMap<>();
    private final ExecutorService workers=Executors.newCachedThreadPool(r->{
        Thread t=new Thread(r,"edhome-hub-client");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running;
    private volatile ServerSocket server;
    private volatile DatagramSocket discovery;
    private Thread acceptThread;
    private Thread discoveryThread;

    DesktopHubServer(String desktopId,String desktopVersion,String token,Host host) {
        this.desktopId=desktopId;
        this.desktopVersion=desktopVersion;
        this.token=token;
        this.host=host;
    }

    synchronized void start() throws Exception {
        if(running)return;
        ServerSocket listener=new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(
            InetAddress.getByName("0.0.0.0"),PORT),24);
        server=listener;
        running=true;
        acceptThread=new Thread(this::acceptLoop,"edhome-hub-api");
        acceptThread.setDaemon(true);
        acceptThread.start();
        startDiscovery();
        DesktopDiagnosticLog.event("HUB_API_LISTENING",
            "port="+PORT+" desktop="+desktopId);
    }

    boolean isRunning() {
        ServerSocket current=server;
        return running&&current!=null&&!current.isClosed();
    }

    String desktopId(){return desktopId;}
    String token(){return token;}

    ClientInfo newestClient() {
        ClientInfo best=null;
        for(ClientInfo info:clients.values())
            if(best==null||info.seenAt>best.seenAt)best=info;
        return best;
    }

    int recentClientCount(long maxAgeMs) {
        long now=System.currentTimeMillis();
        int count=0;
        for(ClientInfo info:clients.values())
            if(now-info.seenAt<=maxAgeMs)count++;
        return count;
    }

    String qrText() {
        java.util.List<String> addresses=EdhomeDesktop.localLanAddresses();
        String host=addresses.isEmpty()?"":addresses.get(0);
        String hosts=String.join(",",addresses);
        return "edhome://desktop-pair?v=2&host="+host
            +"&hosts="+hosts+"&port="+PORT
            +"&id="+desktopId+"&token="+token;
    }

    private void acceptLoop() {
        try {
            while(running) {
                Socket peer=server.accept();
                workers.execute(()->handle(peer));
            }
        } catch(Exception error) {
            if(running)DesktopDiagnosticLog.error("HUB_API_ACCEPT",error);
        } finally {
            running=false;
        }
    }

    private void handle(Socket peer) {
        try(peer) {
            peer.setSoTimeout(15000);
            InetAddress remote=peer.getInetAddress();
            if(remote==null||!(remote instanceof Inet4Address)
                    ||(!remote.isSiteLocalAddress()&&!remote.isLoopbackAddress())) {
                reply(peer,403,"{\"error\":\"LAN_ONLY\"}",null);
                return;
            }
            BufferedReader in=new BufferedReader(new InputStreamReader(
                peer.getInputStream(),StandardCharsets.UTF_8));
            String request=in.readLine();
            if(request==null){return;}
            String[] parts=request.split(" ");
            if(parts.length<2){reply(peer,400,"{\"error\":\"BAD_REQUEST\"}",null);return;}
            String method=parts[0].toUpperCase(Locale.ROOT);
            String path=parts[1].split("\?",2)[0];
            String supplied="";
            int contentLength=0;
            String deviceId="";
            String userId="";
            String userName="";
            String androidVersion="";
            int headerBytes=request.length();
            for(String line;(line=in.readLine())!=null&&!line.isEmpty();) {
                headerBytes+=line.length();
                if(headerBytes>32768){reply(peer,431,"{\"error\":\"HEADERS_TOO_LARGE\"}",null);return;}
                int colon=line.indexOf(':');
                if(colon<=0)continue;
                String name=line.substring(0,colon).trim().toLowerCase(Locale.ROOT);
                String value=line.substring(colon+1).trim();
                if("x-edhome-token".equals(name))supplied=value;
                else if("content-length".equals(name)) {
                    try{contentLength=Integer.parseInt(value);}
                    catch(Exception ignored){contentLength=-1;}
                } else if("x-edhome-device-id".equals(name))deviceId=value;
                else if("x-edhome-user-id".equals(name))userId=value;
                else if("x-edhome-user-name".equals(name))userName=value;
                else if("x-edhome-android-version".equals(name))androidVersion=value;
            }
            if(!constantTimeEquals(token,supplied)) {
                reply(peer,401,"{\"error\":\"PAIRING_REQUIRED\"}",null);
                return;
            }
            if(contentLength<0||contentLength>MAX_BODY) {
                reply(peer,413,"{\"error\":\"BODY_TOO_LARGE\"}",null);
                return;
            }
            String body=contentLength==0?"":readBody(peer.getInputStream(),contentLength);
            ClientInfo client=new ClientInfo(clean(deviceId,96),clean(userId,96),
                clean(userName,120),clean(androidVersion,64),
                remote.getHostAddress(),System.currentTimeMillis());
            if(!client.deviceId.isBlank()) {
                clients.put(client.deviceId,client);
                host.registered(client);
            }

            if("GET".equals(method)&&"/status".equals(path)) {
                JsonObject root=new JsonObject();
                root.addProperty("ok",true);
                root.addProperty("desktopId",desktopId);
                root.addProperty("version",desktopVersion);
                root.addProperty("port",PORT);
                root.addProperty("initialized",host.initialized());
                root.addProperty("revision",host.revision());
                root.addProperty("phones",recentClientCount(180000L));
                reply(peer,200,root.toString(),null);
                return;
            }
            if("GET".equals(method)&&"/state".equals(path)) {
                String snapshot=host.snapshot();
                JsonObject root=new JsonObject();
                root.addProperty("revision",host.revision());
                root.addProperty("initialized",host.initialized());
                root.addProperty("snapshotSha256",sha256(snapshot));
                root.addProperty("desktopId",desktopId);
                root.addProperty("version",desktopVersion);
                reply(peer,200,root.toString(),null);
                return;
            }
            if("POST".equals(method)&&"/register".equals(path)) {
                if(body.length()>4096) {
                    reply(peer,413,"{\"error\":\"REGISTER_TOO_LARGE\"}",null);
                    return;
                }
                if(!body.isBlank()) {
                    JsonObject json=JsonParser.parseString(body).getAsJsonObject();
                    String id=clean(json.has("deviceId")?json.get("deviceId").getAsString():"",96);
                    String uid=clean(json.has("userId")?json.get("userId").getAsString():"",96);
                    String uname=clean(json.has("userName")?json.get("userName").getAsString():"",120);
                    String ver=clean(json.has("version")?json.get("version").getAsString():"",64);
                    if(!id.isBlank()) {
                        client=new ClientInfo(id,uid,uname,ver,
                            remote.getHostAddress(),System.currentTimeMillis());
                        clients.put(id,client);
                        host.registered(client);
                    }
                }
                reply(peer,200,"{\"ok\":true}",null);
                return;
            }
            if("GET".equals(method)&&"/snapshot".equals(path)) {
                String snapshot=host.snapshot();
                reply(peer,200,snapshot,sha256(snapshot));
                return;
            }
            if("POST".equals(method)&&"/bootstrap".equals(path)) {
                String snapshot=host.bootstrap(body,client);
                reply(peer,200,snapshot,sha256(snapshot));
                return;
            }
            if("POST".equals(method)&&("/patch".equals(path)
                    ||"/resolve-phone".equals(path))) {
                try {
                    String snapshot=host.patch(body,client,
                        "/resolve-phone".equals(path));
                    reply(peer,200,snapshot,sha256(snapshot));
                } catch(Conflict conflict) {
                    JsonObject error=new JsonObject();
                    error.addProperty("error","CONFLICT");
                    error.addProperty("table",conflict.table);
                    error.addProperty("rowKey",conflict.rowKey);
                    error.addProperty("message",conflict.getMessage());
                    reply(peer,409,error.toString(),null);
                }
                return;
            }
            reply(peer,404,"{\"error\":\"NOT_FOUND\"}",null);
        } catch(Exception error) {
            DesktopDiagnosticLog.error("HUB_API_CLIENT",error);
            try{reply(peer,500,"{\"error\":\"SERVER_ERROR\"}",null);}
            catch(Exception ignored){}
        }
    }

    private void startDiscovery() {
        try {
            DatagramSocket socket=new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("0.0.0.0",DISCOVERY_PORT));
            discovery=socket;
            discoveryThread=new Thread(this::discoveryLoop,"edhome-hub-discovery");
            discoveryThread.setDaemon(true);
            discoveryThread.start();
            DesktopDiagnosticLog.event("HUB_DISCOVERY_LISTENING",
                "udp="+DISCOVERY_PORT);
        } catch(Exception error) {
            DesktopDiagnosticLog.error("HUB_DISCOVERY_START",error);
        }
    }

    private void discoveryLoop() {
        byte[] buffer=new byte[512];
        while(running&&discovery!=null&&!discovery.isClosed()) {
            try {
                DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
                discovery.receive(packet);
                InetAddress remote=packet.getAddress();
                if(remote==null||!(remote instanceof Inet4Address)
                        ||(!remote.isSiteLocalAddress()&&!remote.isLoopbackAddress()))
                    continue;
                String request=new String(packet.getData(),packet.getOffset(),
                    packet.getLength(),StandardCharsets.US_ASCII).trim();
                if(!request.startsWith("EDHOME_DISCOVER_V1"))continue;
                String wanted="";
                int space=request.indexOf(' ');
                if(space>0)wanted=request.substring(space+1).trim();
                if(!wanted.isBlank()&&!desktopId.equals(wanted))continue;
                String response="EDHOME_DESKTOP_V1|"+desktopId+"|"+PORT+"|"+desktopVersion;
                byte[] bytes=response.getBytes(StandardCharsets.US_ASCII);
                discovery.send(new DatagramPacket(bytes,bytes.length,
                    packet.getAddress(),packet.getPort()));
            } catch(Exception error) {
                if(running)DesktopDiagnosticLog.error("HUB_DISCOVERY",error);
            }
        }
    }

    @Override public synchronized void close() {
        running=false;
        try{if(server!=null)server.close();}catch(Exception ignored){}
        try{if(discovery!=null)discovery.close();}catch(Exception ignored){}
        workers.shutdownNow();
    }

    static final class Conflict extends Exception {
        final String table;
        final String rowKey;
        Conflict(String table,String rowKey,String message) {
            super(message);
            this.table=table==null?"":table;
            this.rowKey=rowKey==null?"":rowKey;
        }
    }

    private static String readBody(InputStream in,int length) throws Exception {
        byte[] out=new byte[length];
        int offset=0;
        while(offset<length) {
            int n=in.read(out,offset,length-offset);
            if(n<0)break;
            offset+=n;
        }
        if(offset!=length)throw new IOException("Niepełne body HTTP.");
        return new String(out,StandardCharsets.UTF_8);
    }

    private static String clean(String value,int max) {
        if(value==null)return "";
        String out=value.replace("\r"," ").replace("\n"," ").trim();
        return out.length()>max?out.substring(0,max):out;
    }

    private static boolean constantTimeEquals(String expected,String supplied) {
        if(expected==null||supplied==null)return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
            supplied.getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256(String text) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256")
            .digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder(64);
        for(byte value:digest)
            out.append(String.format(Locale.ROOT,"%02x",value&0xff));
        return out.toString();
    }

    private static void reply(Socket socket,int status,String body,String sha)
            throws Exception {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        BufferedWriter out=new BufferedWriter(new OutputStreamWriter(
            socket.getOutputStream(),StandardCharsets.US_ASCII));
        String reason=status==200?"OK":status==401?"Unauthorized":
            status==404?"Not Found":status==409?"Conflict":"Error";
        out.write("HTTP/1.1 "+status+" "+reason+"\r\n");
        out.write("Content-Type: application/json; charset=utf-8\r\n");
        out.write("Content-Length: "+bytes.length+"\r\n");
        if(sha!=null&&!sha.isBlank())
            out.write("X-EDHOME-SNAPSHOT-SHA256: "+sha+"\r\n");
        out.write("Connection: close\r\n\r\n");
        out.flush();
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    static String newToken() {
        byte[] random=new byte[24];
        new java.security.SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    static String newDesktopId() {
        return java.util.UUID.randomUUID().toString();
    }
}
