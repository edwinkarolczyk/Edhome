package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

/** Shared vehicle attachments live INSIDE the backed-up SQLite model.
 * Do not persist external picker URIs: they break after moving to a new phone.
 * Exported regular household JSON contains these attachments UNENCRYPTED.
 */
final class VehicleDocumentStore {
    static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    static final int MAX_ALL_BASE64_CHARS = 8 * 1024 * 1024;
    static final String[] KINDS={"policy","inspection","registration","other"};
    static final String[] LABELS={"Polisa OC","Przegląd","Dowód rejestracyjny","Inne"};
    private VehicleDocumentStore(){}

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE vehicle_documents ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "operation_id TEXT NOT NULL UNIQUE, "
            + "vehicle_id INTEGER NOT NULL, policy_id INTEGER, "
            + "kind TEXT NOT NULL CHECK(kind IN "
            + "('policy','inspection','registration','other')), "
            + "file_name TEXT NOT NULL, mime TEXT NOT NULL "
            + "CHECK(mime IN ('application/pdf','image/jpeg','image/png')), "
            + "base64_data TEXT NOT NULL, sha256 TEXT NOT NULL, "
            + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX vehicle_documents_no_duplicate "
            + "ON vehicle_documents(vehicle_id,sha256)");
        db.execSQL("CREATE INDEX vehicle_documents_vehicle_idx "
            + "ON vehicle_documents(vehicle_id,id)");
    }

    static boolean validKind(String kind){
        for(String candidate:KINDS)if(candidate.equals(kind))return true;
        return false;
    }
    static String label(String kind){
        for(int i=0;i<KINDS.length;i++)if(KINDS[i].equals(kind))return LABELS[i];
        return "Inne";
    }
    static boolean validName(String name){
        return name!=null && !name.trim().isEmpty() && name.length()<=120
            && name.indexOf('/')<0 && name.indexOf('\\')<0
            && name.indexOf('\n')<0 && name.indexOf('\r')<0;
    }
    static boolean validMime(String mime, byte[] bytes){
        if(bytes==null||bytes.length<4||bytes.length>MAX_FILE_BYTES)return false;
        if("application/pdf".equals(mime))
            return bytes.length>=5 && bytes[0]=='%' && bytes[1]=='P'
                && bytes[2]=='D' && bytes[3]=='F' && bytes[4]=='-';
        if("image/jpeg".equals(mime))
            return (bytes[0]&255)==255 && (bytes[1]&255)==216
                && (bytes[2]&255)==255
                && (bytes[bytes.length-2]&255)==255
                && (bytes[bytes.length-1]&255)==217;
        if("image/png".equals(mime)) {
            byte[] signature={(byte)137,80,78,71,13,10,26,10};
            if(bytes.length<signature.length)return false;
            for(int i=0;i<signature.length;i++)
                if(bytes[i]!=signature[i])return false;
            return true;
        }
        return false;
    }
    static String sha256(byte[] bytes){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out=new StringBuilder(64);
            for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));
            return out.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){
            throw new IllegalStateException(impossible);
        }
    }
    static byte[] decodeAndVerify(String mime,String base64,String digest){
        if(base64==null||base64.length()>(MAX_FILE_BYTES+2)/3*4+4)
            throw new IllegalArgumentException("Za duży załącznik.");
        byte[] bytes;
        try{bytes=Base64.getDecoder().decode(base64);}
        catch(IllegalArgumentException bad){
            throw new IllegalArgumentException("Załącznik ma błędne kodowanie.");
        }
        if(!validMime(mime,bytes)||digest==null||!digest.equals(sha256(bytes)))
            throw new IllegalArgumentException("Załącznik jest uszkodzony lub ma inny format.");
        return bytes;
    }

    static String add(SQLiteDatabase db,long vehicleId,Long policyId,
            String operationId,String kind,String fileName,String mime,byte[] bytes){
        if(operationId==null||!operationId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    +"[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                ||!validKind(kind)||!validName(fileName)||!validMime(mime,bytes))
            throw new IllegalArgumentException(
                "Wybierz PDF/JPG/PNG do 2 MB, nazwę i typ dokumentu.");
        if(policyId!=null && !"policy".equals(kind))
            throw new IllegalArgumentException("Powiązanie z polisą tylko dla OC.");
        String encoded=Base64.getEncoder().encodeToString(bytes);
        String digest=sha256(bytes);
        db.beginTransaction();
        try{
            try(Cursor previous=db.rawQuery(
                    "SELECT 1 FROM vehicle_documents WHERE operation_id=?",
                    new String[]{operationId})){
                if(previous.moveToFirst()){
                    db.setTransactionSuccessful();return "DUPLICATE";
                }
            }
            if(VehicleStore.find(db,vehicleId)==null)return "MISSING_VEHICLE";
            if(policyId!=null){
                try(Cursor policy=db.rawQuery(
                        "SELECT 1 FROM vehicle_policies WHERE id=? AND vehicle_id=?",
                        new String[]{Long.toString(policyId),Long.toString(vehicleId)})){
                    if(!policy.moveToFirst())return "MISSING_POLICY";
                }
            }
            try(Cursor duplicate=db.rawQuery(
                    "SELECT 1 FROM vehicle_documents WHERE vehicle_id=? AND sha256=?",
                    new String[]{Long.toString(vehicleId),digest})){
                if(duplicate.moveToFirst()){
                    db.setTransactionSuccessful();return "ALREADY_ATTACHED";
                }
            }
            try(Cursor used=db.rawQuery(
                    "SELECT COALESCE(SUM(LENGTH(base64_data)),0) "
                    +"FROM vehicle_documents",null)){
                if(used.moveToFirst()
                        && used.getLong(0)+encoded.length()>MAX_ALL_BASE64_CHARS)
                    return "DOCUMENT_LIMIT";
            }
            ContentValues row=new ContentValues();
            row.put("operation_id",operationId);
            row.put("vehicle_id",vehicleId);
            if(policyId==null)row.putNull("policy_id");
            else row.put("policy_id",policyId);
            row.put("kind",kind);
            row.put("file_name",fileName.trim());
            row.put("mime",mime);
            row.put("base64_data",encoded);
            row.put("sha256",digest);
            row.put("created_at",System.currentTimeMillis());
            db.insertOrThrow("vehicle_documents",null,row);
            db.setTransactionSuccessful();return "COMMITTED";
        }finally{db.endTransaction();}
    }
}
