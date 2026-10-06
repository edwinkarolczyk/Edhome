package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android jako klient lokalnego API EDHOME Desktop.
 *
 * Telefon pozostaje offline-first. Po sparowaniu wysyła tylko zmienione rekordy,
 * sprawdza stan API co 60 s i pobiera snapshot dopiero gdy serwer ma nowe dane.
 */
final class DesktopHubSync {
    static final int PORT=45823;
    private static final int DISCOVERY_PORT=45822;
    private static final long POLL_SECONDS=60L;
    private static final String PREFS_NAME="edhome_beta_prefs";
    private static final String PREF_ID="desktop_hub_id";
    private static final String PREF_HOST="desktop_hub_host";
    private static final String PREF_HOSTS="desktop_hub_hosts";
    private static final String PREF_TOKEN="desktop_hub_token";
    private static final String PREF_DEVICE="desktop_hub_device_id";
    private static final String PREF_BASE_SHA="desktop_hub_baseline_sha";
    private static final String PREF_LAST_SYNC="desktop_hub_last_sync";
    private static final String PREF_LAST_STATE="desktop_hub_last_state";
    private static final String PREF_CONFLICT="desktop_hub_conflict";
    private static final String PREF_FIRST_BACKUP="desktop_hub_first_backup_done";
    private static final String BASELINE_FILE="desktop-hub-baseline.json";
    private static final String CONFLICT_FILE="desktop-hub-conflict-patch.json";

    private static final ScheduledExecutorService EXEC=
        Executors.newSingleThreadScheduledExecutor(r->{
            Thread t=new Thread(r,"edhome-desktop-hub-client");
            t.setDaemon(true);
            return t;
        });
    private static final AtomicBoolean SCHEDULED=new AtomicBoolean();
    private static final AtomicBoolean SYNCING=new AtomicBoolean();
    private static final AtomicBoolean KICK_PENDING=new AtomicBoolean();

    private DesktopHubSync(){}

    static void ensureScheduled(Context context) {
        Context app=context.getApplicationContext();
        if(SCHEDULED.compareAndSet(false,true)) {
            EXEC.scheduleWithFixedDelay(()->syncQuietly(app),
                2L,POLL_SECONDS,TimeUnit.SECONDS);
        } else kick(app);
    }

    static void kick(Context context) {
        Context app=context.getApplicationContext();
        if(!paired(app)||!KICK_PENDING.compareAndSet(false,true))return;
        EXEC.schedule(()->{
            KICK_PENDING.set(false);
            syncQuietly(app);
        },1200L,TimeUnit.MILLISECONDS);
    }

    static boolean paired(Context context) {
        SharedPreferences prefs=prefs(context);
        return validId(prefs.getString(PREF_ID,""))
            &&validToken(prefs.getString(PREF_TOKEN,""));
    }

    static String status(Context context) {
        SharedPreferences prefs=prefs(context);
        if(!paired(context))return "Nie sparowano z EDHOME Desktop.";
        String host=prefs.getString(PREF_HOST,"");
        String state=prefs.getString(PREF_LAST_STATE,"OFFLINE");
        long last=prefs.getLong(PREF_LAST_SYNC,0L);
        String when=last<=0L?"nigdy":
            java.time.Instant.ofEpochMilli(last)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm:ss"));
        return state+"\nDesktop: "+(host.isBlank()?"szukanie w LAN":host+":"+PORT)
            +"\nOstatnia synchronizacja: "+when
            +"\nAPK: "+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")";
    }

    static String conflict(Context context) {
        return prefs(context).getString(PREF_CONFLICT,"");
    }

    static void clearPairing(Context context) {
        SharedPreferences prefs=prefs(context);
        prefs.edit().remove(PREF_ID).remove(PREF_HOST).remove(PREF_HOSTS)
            .remove(PREF_TOKEN).remove(PREF_BASE_SHA).remove(PREF_LAST_SYNC)
            .remove(PREF_LAST_STATE).remove(PREF_CONFLICT).apply();
        baselineFile(context).delete();
        conflictFile(context).delete();
        DiagnosticLog.event("HUB_PAIRING_CLEARED");
    }

    static void pair(Context context,String desktopId,String token,
            List<String> hosts) {
        if(!validId(desktopId)||!validToken(token))
            throw new IllegalArgumentException("Nieprawidłowe dane Desktopu.");
        LinkedHashSet<String> validHosts=new LinkedHashSet<>();
        if(hosts!=null)for(String host:hosts)
            if(isPrivateLanIpv4(host))validHosts.add(host);
        if(validHosts.isEmpty())
            throw new IllegalArgumentException("QR nie zawiera adresu PC w sieci LAN.");
        String first=validHosts.iterator().next();
        SharedPreferences prefs=prefs(context);
        String device=prefs.getString(PREF_DEVICE,"");
        if(!validId(device)) {
            device=UUID.randomUUID().toString();
            prefs.edit().putString(PREF_DEVICE,device).apply();
        }
        prefs.edit().putString(PREF_ID,desktopId.toLowerCase(Locale.ROOT))
            .putString(PREF_TOKEN,token)
            .putString(PREF_HOST,first)
            .putString(PREF_HOSTS,android.text.TextUtils.join(",",validHosts))
            .putString(PREF_LAST_STATE,"PAROWANIE • szukam API Desktopu")
            .remove(PREF_CONFLICT).apply();
        baselineFile(context).delete();
        conflictFile(context).delete();
        DiagnosticLog.event("HUB_QR_SAVED",
            "desktop="+desktopId+" hosts="+android.text.TextUtils.join(",",validHosts));
        ensureScheduled(context);
        kick(context);
    }

    static void resolveDesktop(Context context) {
        Context app=context.getApplicationContext();
        EXEC.execute(()->{
            if(!SYNCING.compareAndSet(false,true))return;
            try {
                SharedPreferences prefs=prefs(app);
                String host=resolveHost(app,prefs);
                if(host==null)throw new IllegalStateException("Nie znaleziono Desktopu.");
                HttpResult result=request(app,prefs,host,"GET","/snapshot",null,null);
                if(result.code!=200)throw new IllegalStateException(
                    "Desktop odpowiedział HTTP "+result.code+".");
                applyServerSnapshot(app,prefs,result.body,result.sha256);
                clearConflict(app,prefs);
                prefs.edit().putString(PREF_LAST_STATE,
                    "ONLINE • konflikt rozwiązany wersją Desktop").apply();
                DiagnosticLog.event("HUB_CONFLICT_DESKTOP_WON");
            } catch(Exception error) {
                DiagnosticLog.error("HUB_CONFLICT_DESKTOP",error);
            } finally {
                SYNCING.set(false);
            }
        });
    }

    static void resolvePhone(Context context) {
        Context app=context.getApplicationContext();
        EXEC.execute(()->{
            if(!SYNCING.compareAndSet(false,true))return;
            try {
                SharedPreferences prefs=prefs(app);
                String patch=readText(conflictFile(app));
                if(patch.isBlank())
                    throw new IllegalStateException("Brak zapisanej zmiany konfliktowej.");
                String host=resolveHost(app,prefs);
                if(host==null)throw new IllegalStateException("Nie znaleziono Desktopu.");
                HttpResult result=request(app,prefs,host,"POST",
                    "/resolve-phone",patch,"application/json; charset=utf-8");
                if(result.code!=200)throw new IllegalStateException(
                    "Desktop odpowiedział HTTP "+result.code+".");
                applyServerSnapshot(app,prefs,result.body,result.sha256);
                clearConflict(app,prefs);
                prefs.edit().putString(PREF_LAST_STATE,
                    "ONLINE • konflikt rozwiązany wersją telefonu").apply();
                DiagnosticLog.event("HUB_CONFLICT_PHONE_WON");
            } catch(Exception error) {
                DiagnosticLog.error("HUB_CONFLICT_PHONE",error);
            } finally {
                SYNCING.set(false);
            }
        });
    }

    private static void syncQuietly(Context context) {
        if(!paired(context)||!SYNCING.compareAndSet(false,true))return;
        try {
            syncOnce(context);
        } catch(Exception error) {
            SharedPreferences prefs=prefs(context);
            prefs.edit().putString(PREF_LAST_STATE,
                "OFFLINE • "+shortMessage(error)).apply();
            DiagnosticLog.error("HUB_SYNC",error);
        } finally {
            SYNCING.set(false);
        }
    }

    private static void syncOnce(Context context) throws Exception {
        SharedPreferences prefs=prefs(context);
        if(!prefs.getString(PREF_CONFLICT,"").isBlank())return;
        String host=resolveHost(context,prefs);
        if(host==null)throw new IllegalStateException("Nie znaleziono EDHOME Desktop w LAN.");
        register(context,prefs,host);

        HubState state=state(context,prefs,host);
        try(MainActivity.LocalDb helper=new MainActivity.LocalDb(context)) {
            SQLiteDatabase database=helper.getWritableDatabase();
            SyncRecordStore.ensureAll(database);
            String current=DataBackup.exportJson(database,prefs);

            if(!state.initialized) {
                ensureFirstBackup(context,database,prefs);
                HttpResult boot=request(context,prefs,host,"POST",
                    "/bootstrap",current,"application/json; charset=utf-8");
                if(boot.code!=200)
                    throw new IllegalStateException("Bootstrap Desktop HTTP "+boot.code+".");
                applyServerSnapshot(context,prefs,boot.body,boot.sha256);
                prefs.edit().putString(PREF_LAST_STATE,
                    "ONLINE • pierwszy backup i synchronizacja OK").apply();
                DiagnosticLog.event("HUB_BOOTSTRAP_OK");
                return;
            }

            File baseline=baselineFile(context);
            if(!baseline.isFile()) {
                ensureFirstBackup(context,database,prefs);
                HttpResult server=request(context,prefs,host,"GET","/snapshot",null,null);
                if(server.code!=200)
                    throw new IllegalStateException("Desktop snapshot HTTP "+server.code+".");
                applyServerSnapshot(context,prefs,server.body,server.sha256);
                prefs.edit().putString(PREF_LAST_STATE,
                    "ONLINE • pobrano stan Desktopu").apply();
                DiagnosticLog.event("HUB_BASELINE_CREATED");
                return;
            }

            String base=readText(baseline);
            PatchPlan plan=buildPatch(base,current);
            String baselineSha=prefs.getString(PREF_BASE_SHA,"");
            if(plan.fullSnapshot) {
                HttpResult written=request(context,prefs,host,"POST","/snapshot",
                    current,"application/json; charset=utf-8",
                    "X-EDHOME-BASE-SHA256",baselineSha);
                if(written.code==409) {
                    rememberConflict(context,prefs,
                        "Konflikt ustawień / pełnego stanu. Wybierz Telefon albo Desktop.",
                        null);
                    return;
                }
                if(written.code!=200)
                    throw new IllegalStateException("Desktop zapis HTTP "+written.code+".");
                applyServerSnapshot(context,prefs,written.body,written.sha256);
                prefs.edit().putString(PREF_LAST_STATE,
                    "ONLINE • zsynchronizowano pełny stan").apply();
                return;
            }

            if(plan.operations>0) {
                HttpResult patched=request(context,prefs,host,"POST","/patch",
                    plan.payload.toString(),"application/json; charset=utf-8");
                if(patched.code==409) {
                    String detail=conflictText(patched.body);
                    rememberConflict(context,prefs,detail,plan.payload.toString());
                    return;
                }
                if(patched.code!=200)
                    throw new IllegalStateException("Desktop patch HTTP "+patched.code+".");
                applyServerSnapshot(context,prefs,patched.body,patched.sha256);
                prefs.edit().putString(PREF_LAST_STATE,
                    "ONLINE • wysłano "+plan.operations
                        +(plan.operations==1?" zmianę":" zmiany")).apply();
                DiagnosticLog.event("HUB_PATCH_OK","operations="+plan.operations);
                return;
            }

            if(!state.snapshotSha256.isBlank()
                    &&!state.snapshotSha256.equalsIgnoreCase(baselineSha)) {
                HttpResult server=request(context,prefs,host,"GET","/snapshot",null,null);
                if(server.code!=200)
                    throw new IllegalStateException("Desktop snapshot HTTP "+server.code+".");
                applyServerSnapshot(context,prefs,server.body,server.sha256);
                prefs.edit().putString(PREF_LAST_STATE,
                    "ONLINE • pobrano zmiany z Desktopu").apply();
                DiagnosticLog.event("HUB_PULL_OK");
            } else {
                prefs.edit().putLong(PREF_LAST_SYNC,System.currentTimeMillis())
                    .putString(PREF_LAST_STATE,"ONLINE • dane aktualne").apply();
            }
        }
    }

    private static void register(Context context,SharedPreferences prefs,String host)
            throws Exception {
        String device=deviceId(prefs);
        long userId=prefs.getLong("active_member_id",0L);
        String userName="";
        try(MainActivity.LocalDb helper=new MainActivity.LocalDb(context);
            Cursor c=helper.getReadableDatabase().rawQuery(
                "SELECT name FROM household_members WHERE id=?",
                new String[]{Long.toString(userId)})) {
            if(c.moveToFirst())userName=c.getString(0);
        }
        JSONObject body=new JSONObject();
        body.put("deviceId",device);
        body.put("userId",Long.toString(userId));
        body.put("userName",userName);
        body.put("version",BuildConfig.VERSION_NAME);
        HttpResult result=request(context,prefs,host,"POST","/register",
            body.toString(),"application/json; charset=utf-8");
        if(result.code!=200)
            throw new IllegalStateException("Rejestracja telefonu HTTP "+result.code+".");
    }

    private static HubState state(Context context,SharedPreferences prefs,String host)
            throws Exception {
        HttpResult result=request(context,prefs,host,"GET","/state",null,null);
        if(result.code!=200)
            throw new IllegalStateException("Stan Desktopu HTTP "+result.code+".");
        JSONObject json=new JSONObject(result.body);
        String id=json.optString("desktopId","");
        if(!id.equalsIgnoreCase(prefs.getString(PREF_ID,"")))
            throw new IllegalStateException("To inny EDHOME Desktop.");
        return new HubState(json.optBoolean("initialized",false),
            json.optString("snapshotSha256",""),
            json.optLong("revision",0L),
            json.optString("version",""));
    }

    private static String resolveHost(Context context,SharedPreferences prefs) {
        LinkedHashSet<String> candidates=new LinkedHashSet<>();
        String saved=prefs.getString(PREF_HOST,"").trim();
        if(isPrivateLanIpv4(saved))candidates.add(saved);
        String hosts=prefs.getString(PREF_HOSTS,"");
        for(String host:hosts.split(","))
            if(isPrivateLanIpv4(host.trim()))candidates.add(host.trim());
        for(String host:candidates) {
            try {
                DiagnosticLog.event("HUB_HOST_CHECK","host="+host+" port="+PORT);
                HttpResult status=request(context,prefs,host,"GET","/status",null,null);
                if(status.code!=200) {
                    DiagnosticLog.event("HUB_HOST_HTTP_REJECTED",
                        "host="+host+" code="+status.code);
                    continue;
                }
                JSONObject body=new JSONObject(status.body);
                String expected=prefs.getString(PREF_ID,"");
                String actual=body.optString("desktopId","");
                if(expected.equalsIgnoreCase(actual)) {
                    prefs.edit().putString(PREF_HOST,host).apply();
                    DiagnosticLog.event("HUB_HOST_OK",
                        "host="+host+" version="+body.optString("version","?"));
                    return host;
                }
                DiagnosticLog.event("HUB_HOST_ID_MISMATCH","host="+host);
            } catch(Exception problem) {
                DiagnosticLog.event("HUB_HOST_UNREACHABLE",
                    "host="+host+" type="+problem.getClass().getSimpleName());
            }
        }
        String discovered=discover(prefs.getString(PREF_ID,""));
        if(discovered!=null) {
            prefs.edit().putString(PREF_HOST,discovered).apply();
            DiagnosticLog.event("HUB_REDISCOVERED","host="+discovered);
        }
        return discovered;
    }

    private static String discover(String desktopId) {
        if(!validId(desktopId))return null;
        try(DatagramSocket socket=new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(1800);
            byte[] query=("EDHOME_DISCOVER_V1 "+desktopId)
                .getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(query,query.length,
                InetAddress.getByName("255.255.255.255"),DISCOVERY_PORT));
            long deadline=System.currentTimeMillis()+1800L;
            byte[] buffer=new byte[512];
            while(System.currentTimeMillis()<deadline) {
                DatagramPacket reply=new DatagramPacket(buffer,buffer.length);
                socket.receive(reply);
                String text=new String(reply.getData(),reply.getOffset(),
                    reply.getLength(),StandardCharsets.US_ASCII).trim();
                String[] parts=text.split("\\|");
                if(parts.length>=4&&"EDHOME_DESKTOP_V1".equals(parts[0])
                        &&desktopId.equalsIgnoreCase(parts[1])
                        &&Integer.toString(PORT).equals(parts[2]))
                    return reply.getAddress().getHostAddress();
            }
        } catch(Exception problem) {
            DiagnosticLog.event("HUB_DISCOVERY_FAILED",
                "type="+problem.getClass().getSimpleName());
        }
        return null;
    }

    static String diagnostics(Context context) {
        SharedPreferences prefs=prefs(context);
        StringBuilder out=new StringBuilder();
        out.append("EDHOME Android ").append(BuildConfig.VERSION_NAME)
            .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        out.append("Tryb: telefon = klient API Desktopu\n");
        out.append("Sparowano: ").append(paired(context)?"TAK":"NIE").append("\n");
        out.append("Desktop ID: ").append(prefs.getString(PREF_ID,"brak")).append("\n");
        out.append("Zapamiętany host: ")
            .append(prefs.getString(PREF_HOST,"brak")).append(":").append(PORT).append("\n");
        out.append("Stan: ").append(prefs.getString(PREF_LAST_STATE,"brak")).append("\n");
        out.append("Ostatnia synchronizacja: ")
            .append(prefs.getLong(PREF_LAST_SYNC,0L)).append("\n");
        if(!paired(context))return out.toString();
        LinkedHashSet<String> candidates=new LinkedHashSet<>();
        String saved=prefs.getString(PREF_HOST,"").trim();
        if(isPrivateLanIpv4(saved))candidates.add(saved);
        for(String candidate:prefs.getString(PREF_HOSTS,"").split(","))
            if(isPrivateLanIpv4(candidate.trim()))candidates.add(candidate.trim());
        for(String host:candidates) {
            try {
                long started=System.currentTimeMillis();
                HttpResult result=request(context,prefs,host,"GET","/status",null,null);
                long ms=System.currentTimeMillis()-started;
                out.append("TCP/HTTP ").append(host).append(":").append(PORT)
                    .append(" → HTTP ").append(result.code)
                    .append(" • ").append(ms).append(" ms");
                if(result.code==200) {
                    JSONObject json=new JSONObject(result.body);
                    out.append(" • Desktop ").append(json.optString("version","?"))
                        .append(" • ID ")
                        .append(prefs.getString(PREF_ID,"").equalsIgnoreCase(
                            json.optString("desktopId",""))?"ZGODNE":"INNE");
                }
                out.append("\n");
            } catch(Exception problem) {
                out.append("TCP/HTTP ").append(host).append(":").append(PORT)
                    .append(" → BRAK POŁĄCZENIA • ")
                    .append(problem.getClass().getSimpleName()).append("\n");
            }
        }
        String discovered=discover(prefs.getString(PREF_ID,""));
        out.append("Discovery UDP ").append(DISCOVERY_PORT).append(": ")
            .append(discovered==null?"BRAK ODPOWIEDZI":discovered).append("\n");
        out.append("Token: zapisany, celowo nie jest pokazywany.");
        return out.toString();
    }

    private static HttpResult request(Context context,SharedPreferences prefs,
            String host,String method,String path,String body,String contentType,
            String...extraHeaders) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(
            "http://"+host+":"+PORT+path).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(16000);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("X-EDHOME-TOKEN",prefs.getString(PREF_TOKEN,""));
        connection.setRequestProperty("X-EDHOME-DEVICE-ID",deviceId(prefs));
        connection.setRequestProperty("X-EDHOME-ANDROID-VERSION",BuildConfig.VERSION_NAME);
        for(int i=0;i+1<extraHeaders.length;i+=2)
            connection.setRequestProperty(extraHeaders[i],extraHeaders[i+1]);
        if(body!=null) {
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            if(bytes.length>DataBackup.MAX_BYTES)
                throw new IllegalArgumentException("Pakiet synchronizacji przekracza 8 MB.");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type",
                contentType==null?"application/json; charset=utf-8":contentType);
            connection.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=connection.getOutputStream()) {
                out.write(bytes);
                out.flush();
            }
        }
        int code=connection.getResponseCode();
        InputStream input=code>=400?connection.getErrorStream():connection.getInputStream();
        String response=input==null?"":readBounded(input,DataBackup.MAX_BYTES);
        String sha=connection.getHeaderField("X-EDHOME-SNAPSHOT-SHA256");
        connection.disconnect();
        return new HttpResult(code,response,sha==null?"":sha);
    }

    private static void applyServerSnapshot(Context context,SharedPreferences prefs,
            String server,String suppliedSha) throws Exception {
        JSONObject parsed=new JSONObject(server);
        String current;
        try(MainActivity.LocalDb helper=new MainActivity.LocalDb(context)) {
            SQLiteDatabase database=helper.getWritableDatabase();
            SyncRecordStore.ensureAll(database);
            current=DataBackup.exportJson(database,prefs);
            if(!canonical(new JSONObject(current)).equals(canonical(parsed))) {
                DataBackup.restoreJson(database,prefs,server);
                ReminderReceiver.schedule(context);
                DeviceTimerReceiver.scheduleAll(context);
            }
        }
        String sha=suppliedSha==null||suppliedSha.isBlank()
            ?sha256(server):suppliedSha.toLowerCase(Locale.ROOT);
        writeText(baselineFile(context),server);
        prefs.edit().putString(PREF_BASE_SHA,sha)
            .putLong(PREF_LAST_SYNC,System.currentTimeMillis())
            .putString(PREF_LAST_STATE,"ONLINE • zsynchronizowano").apply();
    }

    private static void ensureFirstBackup(Context context,SQLiteDatabase database,
            SharedPreferences prefs) throws Exception {
        if(prefs.getBoolean(PREF_FIRST_BACKUP,false))return;
        DataBackupArchive.Created created=DataBackupArchive.create(context,database,prefs);
        try {
            File dir=new File(context.getFilesDir(),"hub-first-backup");
            if(!dir.exists()&&!dir.mkdirs())
                throw new IllegalStateException("Nie utworzono katalogu pierwszego backupu.");
            String stamp=java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.now());
            File target=new File(dir,"EDHOME-before-first-desktop-"+stamp+".zip");
            try(FileInputStream in=new FileInputStream(created.file);
                FileOutputStream out=new FileOutputStream(target)) {
                byte[] buffer=new byte[8192];
                for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);
                out.flush();
            }
            prefs.edit().putBoolean(PREF_FIRST_BACKUP,true).apply();
            DiagnosticLog.event("HUB_FIRST_PHONE_BACKUP_SAVED",
                "bytes="+target.length());
        } finally {
            created.file.delete();
        }
    }

    private static PatchPlan buildPatch(String baseline,String current) throws Exception {
        JSONObject beforeRoot=new JSONObject(baseline);
        JSONObject afterRoot=new JSONObject(current);
        if(!canonical(beforeRoot.optJSONObject("settings"))
                .equals(canonical(afterRoot.optJSONObject("settings"))))
            return PatchPlan.full();

        JSONObject beforeTables=beforeRoot.getJSONObject("tables");
        JSONObject afterTables=afterRoot.getJSONObject("tables");
        Map<String,JSONObject> baseMeta=metaByRow(beforeRoot);
        Map<String,JSONObject> currentMeta=metaByRow(afterRoot);
        List<String> tables=new ArrayList<>();
        for(Iterator<String> it=beforeTables.keys();it.hasNext();)tables.add(it.next());
        for(Iterator<String> it=afterTables.keys();it.hasNext();) {
            String table=it.next();
            if(!tables.contains(table))tables.add(table);
        }
        Collections.sort(tables);
        JSONArray operations=new JSONArray();
        for(String table:tables) {
            JSONArray before=beforeTables.optJSONArray(table);
            JSONArray after=afterTables.optJSONArray(table);
            if(before==null||after==null)return PatchPlan.full();
            if(canonical(before).equals(canonical(after)))continue;
            Map<String,JSONObject> beforeRows=rowsByKey(table,before);
            Map<String,JSONObject> afterRows=rowsByKey(table,after);
            if(beforeRows==null||afterRows==null)return PatchPlan.full();
            Set<String> keys=new java.util.TreeSet<>();
            keys.addAll(beforeRows.keySet());
            keys.addAll(afterRows.keySet());
            for(String rowKey:keys) {
                JSONObject oldRow=beforeRows.get(rowKey);
                JSONObject newRow=afterRows.get(rowKey);
                if(oldRow!=null&&newRow!=null
                        &&canonical(oldRow).equals(canonical(newRow)))continue;
                String metaKey=table+"\u0000"+rowKey;
                JSONObject meta=oldRow==null?currentMeta.get(metaKey):baseMeta.get(metaKey);
                if(meta==null)return PatchPlan.full();
                String uuid=meta.optString("syncUuid","").toLowerCase(Locale.ROOT);
                long baseRevision=oldRow==null?0L:meta.optLong("revision",-1L);
                if(!validId(uuid)||baseRevision<0L)return PatchPlan.full();
                JSONObject op=new JSONObject();
                op.put("table",table);
                op.put("rowKey",rowKey);
                op.put("syncUuid",uuid);
                op.put("baseRevision",baseRevision);
                if(newRow==null)op.put("action","delete");
                else {
                    op.put("action","upsert");
                    op.put("row",new JSONObject(newRow.toString()));
                }
                operations.put(op);
                if(operations.length()>500)return PatchPlan.full();
            }
        }
        if(operations.length()==0)return PatchPlan.none();
        JSONObject payload=new JSONObject();
        payload.put("format","edhome-record-patch");
        payload.put("version",2);
        payload.put("operations",operations);
        return new PatchPlan(payload,operations.length(),false);
    }

    private static Map<String,JSONObject> metaByRow(JSONObject root) {
        Map<String,JSONObject> out=new LinkedHashMap<>();
        JSONArray array=root.optJSONArray("syncRecords");
        if(array==null)return out;
        for(int i=0;i<array.length();i++) {
            JSONObject meta=array.optJSONObject(i);
            if(meta==null)continue;
            String table=meta.optString("table","");
            String key=meta.optString("rowKey","");
            if(!table.isBlank()&&!key.isBlank())
                out.put(table+"\u0000"+key,meta);
        }
        return out;
    }

    private static Map<String,JSONObject> rowsByKey(String table,JSONArray array) {
        Map<String,JSONObject> out=new LinkedHashMap<>();
        for(int i=0;i<array.length();i++) {
            JSONObject row=array.optJSONObject(i);
            if(row==null)return null;
            String key=rowKey(table,row);
            if(key==null||out.put(key,row)!=null)return null;
        }
        return out;
    }

    private static String rowKey(String table,JSONObject row) {
        try {
            if("task_rotation_members".equals(table))
                return canonicalLong(row.get("task_id"))+":"+canonicalLong(row.get("member_id"));
            if("project_task_dependencies".equals(table))
                return canonicalLong(row.get("task_id"))+":"+canonicalLong(row.get("depends_on_task_id"));
            if("pantry_packages".equals(table))
                return canonicalLong(row.get("pantry_id"));
            return canonicalLong(row.get("id"));
        } catch(Exception error) {
            return null;
        }
    }

    private static String canonicalLong(Object raw) {
        return Long.toString(new BigDecimal(String.valueOf(raw)).longValueExact());
    }

    private static String canonical(Object value) throws Exception {
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject) {
            JSONObject object=(JSONObject)value;
            List<String> keys=new ArrayList<>();
            for(Iterator<String> it=object.keys();it.hasNext();)keys.add(it.next());
            Collections.sort(keys);
            StringBuilder out=new StringBuilder("{");
            for(int i=0;i<keys.size();i++) {
                if(i>0)out.append(',');
                String key=keys.get(i);
                out.append(JSONObject.quote(key)).append(':')
                    .append(canonical(object.get(key)));
            }
            return out.append('}').toString();
        }
        if(value instanceof JSONArray) {
            JSONArray array=(JSONArray)value;
            StringBuilder out=new StringBuilder("[");
            for(int i=0;i<array.length();i++) {
                if(i>0)out.append(',');
                out.append(canonical(array.get(i)));
            }
            return out.append(']').toString();
        }
        if(value instanceof Boolean)return value.toString();
        if(value instanceof Number) {
            BigDecimal number=new BigDecimal(String.valueOf(value));
            if(number.compareTo(BigDecimal.ZERO)==0)return "0";
            return number.stripTrailingZeros().toPlainString();
        }
        return JSONObject.quote(String.valueOf(value));
    }

    private static void rememberConflict(Context context,SharedPreferences prefs,
            String summary,String patch) throws Exception {
        if(patch!=null)writeText(conflictFile(context),patch);
        prefs.edit().putString(PREF_CONFLICT,summary)
            .putString(PREF_LAST_STATE,"KONFLIKT • wymaga decyzji").apply();
        DiagnosticLog.event("HUB_CONFLICT","summary="+summary);
    }

    private static void clearConflict(Context context,SharedPreferences prefs) {
        conflictFile(context).delete();
        prefs.edit().remove(PREF_CONFLICT).apply();
    }

    private static String conflictText(String body) {
        try {
            JSONObject json=new JSONObject(body);
            String table=json.optString("table","");
            String key=json.optString("rowKey","");
            return "Konflikt: "+table+(key.isBlank()?"":" #"+key)
                +". Wybierz wersję Telefon albo Desktop.";
        } catch(Exception ignored) {
            return "Konflikt tego samego rekordu. Wybierz wersję Telefon albo Desktop.";
        }
    }

    private static String deviceId(SharedPreferences prefs) {
        String id=prefs.getString(PREF_DEVICE,"");
        if(validId(id))return id;
        id=UUID.randomUUID().toString();
        prefs.edit().putString(PREF_DEVICE,id).apply();
        return id;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE);
    }

    private static File baselineFile(Context context) {
        return new File(context.getFilesDir(),BASELINE_FILE);
    }

    private static File conflictFile(Context context) {
        return new File(context.getFilesDir(),CONFLICT_FILE);
    }

    private static String readText(File file) throws Exception {
        if(file==null||!file.isFile())return "";
        try(FileInputStream in=new FileInputStream(file)) {
            return readBounded(in,DataBackup.MAX_BYTES);
        }
    }

    private static void writeText(File file,String text) throws Exception {
        try(FileOutputStream out=new FileOutputStream(file,false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static String readBounded(InputStream input,int max) throws Exception {
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];
            int total=0;
            for(int n;(n=in.read(buffer))!=-1;) {
                total+=n;
                if(total>max)throw new IllegalArgumentException("Odpowiedź przekracza limit.");
                out.write(buffer,0,n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String sha256(String text) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256")
            .digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder(64);
        for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&0xff));
        return out.toString();
    }

    private static boolean validId(String value) {
        return value!=null&&value.matches("[0-9a-fA-F-]{36}");
    }

    private static boolean validToken(String value) {
        return value!=null&&value.matches("[A-Za-z0-9_-]{20,128}");
    }

    private static boolean isPrivateLanIpv4(String host) {
        if(host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
        try {
            InetAddress address=InetAddress.getByName(host);
            return address instanceof java.net.Inet4Address
                &&address.isSiteLocalAddress()&&!address.isLoopbackAddress();
        } catch(Exception ignored) {
            return false;
        }
    }

    private static String shortMessage(Throwable error) {
        Throwable current=error;
        while(current.getCause()!=null)current=current.getCause();
        String message=current.getMessage();
        if(message==null||message.isBlank())message=current.getClass().getSimpleName();
        return message.length()>120?message.substring(0,120):message;
    }

    private static final class HubState {
        final boolean initialized;
        final String snapshotSha256;
        final long revision;
        final String version;
        HubState(boolean initialized,String snapshotSha256,long revision,String version) {
            this.initialized=initialized;
            this.snapshotSha256=snapshotSha256==null?"":snapshotSha256;
            this.revision=revision;
            this.version=version==null?"":version;
        }
    }

    private static final class HttpResult {
        final int code;
        final String body;
        final String sha256;
        HttpResult(int code,String body,String sha256) {
            this.code=code;
            this.body=body==null?"":body;
            this.sha256=sha256==null?"":sha256;
        }
    }

    private static final class PatchPlan {
        final JSONObject payload;
        final int operations;
        final boolean fullSnapshot;
        PatchPlan(JSONObject payload,int operations,boolean fullSnapshot) {
            this.payload=payload;
            this.operations=operations;
            this.fullSnapshot=fullSnapshot;
        }
        static PatchPlan full(){return new PatchPlan(new JSONObject(),0,true);}
        static PatchPlan none(){return new PatchPlan(new JSONObject(),0,false);}
    }
}
