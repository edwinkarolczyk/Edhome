package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteConstraintException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Sidecar synchronization metadata.
 *
 * Domain tables keep their local numeric/composite keys. sync_uuid is the
 * cross-device identity and survives backups; revision/row_hash detect edits
 * made through any Android code path without requiring every store to know
 * about LAN synchronization.
 */
final class SyncRecordStore {
    private static final String TABLE = "sync_records";
    private static final int MAX_PATCH_OPS = 500;
    private static final int MAX_METADATA_ROWS = 50000;

    private SyncRecordStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + "sync_uuid TEXT PRIMARY KEY, "
            + "table_name TEXT NOT NULL, "
            + "row_key TEXT NOT NULL, "
            + "revision INTEGER NOT NULL CHECK(revision>=1), "
            + "updated_at INTEGER NOT NULL, "
            + "deleted_at INTEGER, "
            + "row_hash TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS sync_records_live_row_idx "
            + "ON " + TABLE + "(table_name,row_key) WHERE deleted_at IS NULL");
        db.execSQL("CREATE INDEX IF NOT EXISTS sync_records_table_idx "
            + "ON " + TABLE + "(table_name,row_key)");
        db.execSQL("CREATE INDEX IF NOT EXISTS sync_records_updated_idx "
            + "ON " + TABLE + "(updated_at)");
    }

    static void ensureAll(SQLiteDatabase db) throws Exception {
        create(db);
        long now = System.currentTimeMillis();
        Set<String> liveKeys = new HashSet<>();

        for (String[] definition : DataBackup.syncDefinitions()) {
            String table = definition[0];
            String[] columns = DataBackup.syncColumns(table);
            if (columns == null) continue;

            try (Cursor cursor = db.query(table, columns,
                    null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    JSONObject row = cursorRow(cursor, columns);
                    String rowKey = rowKey(table, row);
                    if (rowKey == null) continue;
                    liveKeys.add(table + "\u0000" + rowKey);
                    String hash = rowHash(row);
                    Meta meta = liveForRow(db, table, rowKey);
                    if (meta == null) {
                        insertMeta(db, UUID.randomUUID().toString(), table, rowKey,
                            1L, now, null, hash);
                    } else if (!hash.equals(meta.rowHash)) {
                        ContentValues values = new ContentValues();
                        values.put("revision", meta.revision + 1L);
                        values.put("updated_at", now);
                        values.put("row_hash", hash);
                        db.update(TABLE, values, "sync_uuid=?",
                            new String[]{meta.syncUuid});
                    }
                }
            }
        }

        try (Cursor cursor = db.query(TABLE,
                new String[]{"sync_uuid","table_name","row_key","revision",
                    "updated_at","deleted_at","row_hash"},
                "deleted_at IS NULL", null, null, null, null)) {
            while (cursor.moveToNext()) {
                Meta meta = meta(cursor);
                if (liveKeys.contains(meta.table + "\u0000" + meta.rowKey)) continue;
                ContentValues values = new ContentValues();
                values.put("revision", meta.revision + 1L);
                values.put("updated_at", now);
                values.put("deleted_at", now);
                values.put("row_hash", "DELETED");
                db.update(TABLE, values, "sync_uuid=?",
                    new String[]{meta.syncUuid});
            }
        }
    }

    static JSONArray exportMetadata(SQLiteDatabase db) throws Exception {
        ensureAll(db);
        JSONArray result = new JSONArray();
        try (Cursor cursor = db.query(TABLE,
                new String[]{"sync_uuid","table_name","row_key","revision",
                    "updated_at","deleted_at","row_hash"},
                null, null, null, null, "table_name,row_key,updated_at")) {
            while (cursor.moveToNext()) result.put(metaJson(meta(cursor)));
        }
        return result;
    }

    static void restoreMetadata(SQLiteDatabase db, JSONArray array) throws Exception {
        create(db);
        db.delete(TABLE, null, null);
        if (array != null) {
            if (array.length() > MAX_METADATA_ROWS)
                throw new IllegalArgumentException("Za dużo rekordów synchronizacji.");
            Set<String> uuids = new HashSet<>();
            Set<String> liveRows = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null)
                    throw new IllegalArgumentException("Nieprawidłowy rekord synchronizacji.");
                String uuid = item.optString("syncUuid", "");
                String table = item.optString("table", "");
                String rowKey = normalizeRowKey(table,
                    item.optString("rowKey", ""));
                long revision = item.optLong("revision", 0L);
                long updatedAt = item.optLong("updatedAt", 0L);
                Long deletedAt = item.isNull("deletedAt")
                    ? null : item.optLong("deletedAt", 0L);
                String hash = item.optString("rowHash", "");
                if (!uuid.matches("[0-9a-fA-F-]{36}")
                        || DataBackup.syncColumns(table) == null
                        || rowKey == null
                        || revision < 1 || updatedAt < 1
                        || (deletedAt != null && deletedAt < 1)
                        || !(hash.matches("[0-9a-f]{64}") || "DELETED".equals(hash))
                        || !uuids.add(uuid))
                    throw new IllegalArgumentException("Nieprawidłowa metadana synchronizacji.");

                String liveKey = table + "\u0000" + rowKey;
                if (deletedAt == null) {
                    if (!liveRows.add(liveKey) || !rowExists(db, table, rowKey))
                        throw new IllegalArgumentException(
                            "Metadana wskazuje nieistniejący lub zdublowany rekord.");
                }
                insertMeta(db, uuid.toLowerCase(Locale.ROOT), table, rowKey,
                    revision, updatedAt, deletedAt, hash);
            }
        }
        ensureAll(db);
    }

    static String applyPatch(SQLiteDatabase db, String incoming) throws Exception {
        JSONObject patch = new JSONObject(incoming);
        if (!"edhome-record-patch".equals(patch.optString("format")))
            throw new IllegalArgumentException("Nieobsługiwany format patcha.");
        int version = patch.optInt("version", -1);
        if (version != 1 && version != 2)
            throw new IllegalArgumentException("Nieobsługiwana wersja patcha.");
        JSONArray operations = patch.optJSONArray("operations");
        if (operations == null || operations.length() < 1
                || operations.length() > MAX_PATCH_OPS)
            throw new IllegalArgumentException(
                "Patch musi zawierać 1–" + MAX_PATCH_OPS + " operacji.");

        JSONArray results = new JSONArray();
        db.beginTransaction();
        try {
            ensureAll(db);
            Set<String> touched = new HashSet<>();
            for (int i = 0; i < operations.length(); i++) {
                JSONObject op = operations.optJSONObject(i);
                if (op == null)
                    throw new IllegalArgumentException("Operacja #" + i + " nie jest obiektem.");
                JSONObject result = version == 2
                    ? applyV2(db, op, touched)
                    : applyV1(db, op, touched);
                results.put(result);
            }
            db.setTransactionSuccessful();
        } catch (SQLiteConstraintException constraint) {
            throw new IllegalArgumentException(
                "Zmiana narusza reguły integralności danych.", constraint);
        } finally {
            db.endTransaction();
        }

        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("operations", results.length());
        response.put("results", results);
        return response.toString();
    }

    private static JSONObject applyV2(SQLiteDatabase db, JSONObject op,
            Set<String> touched) throws Exception {
        String table = op.optString("table", "");
        String rowKey = normalizeRowKey(table, op.optString("rowKey", ""));
        String syncUuid = op.optString("syncUuid", "").toLowerCase(Locale.ROOT);
        String action = op.optString("action", "");
        long baseRevision = op.optLong("baseRevision", -1L);

        if (DataBackup.syncColumns(table) == null
                || rowKey == null
                || !syncUuid.matches("[0-9a-f-]{36}")
                || !("upsert".equals(action) || "delete".equals(action))
                || baseRevision < 0)
            throw new IllegalArgumentException("Nieprawidłowa operacja synchronizacji.");
        if (!touched.add(syncUuid))
            throw new IllegalArgumentException("Ten sam sync_uuid występuje dwa razy.");

        long now = System.currentTimeMillis();
        Meta meta = byUuid(db, syncUuid);

        if (baseRevision == 0L) {
            if (!"upsert".equals(action) || meta != null
                    || liveForRow(db, table, rowKey) != null
                    || rowExists(db, table, rowKey))
                throw new SyncConflict(table, rowKey, syncUuid, 0L,
                    meta == null ? -1L : meta.revision);
            JSONObject row = requiredRow(op, table, rowKey);
            ContentValues values = rowValues(table, row);
            db.insertOrThrow(table, null, values);
            String hash = rowHash(row);
            insertMeta(db, syncUuid, table, rowKey, 1L, now, null, hash);
            return metaJson(byUuid(db, syncUuid));
        }

        if (meta == null || !table.equals(meta.table) || !rowKey.equals(meta.rowKey)
                || meta.deletedAt != null)
            throw new SyncConflict(table, rowKey, syncUuid, baseRevision,
                meta == null ? -1L : meta.revision);

        refreshOne(db, meta);
        meta = byUuid(db, syncUuid);
        if (meta == null || meta.deletedAt != null || meta.revision != baseRevision)
            throw new SyncConflict(table, rowKey, syncUuid, baseRevision,
                meta == null ? -1L : meta.revision);

        if ("delete".equals(action)) {
            Selection selection = selection(table, rowKey);
            if (db.delete(table, selection.where, selection.args) != 1)
                throw new SyncConflict(table, rowKey, syncUuid, baseRevision, meta.revision);
            ContentValues update = new ContentValues();
            update.put("revision", meta.revision + 1L);
            update.put("updated_at", now);
            update.put("deleted_at", now);
            update.put("row_hash", "DELETED");
            db.update(TABLE, update, "sync_uuid=?", new String[]{syncUuid});
            return metaJson(byUuid(db, syncUuid));
        }

        JSONObject row = requiredRow(op, table, rowKey);
        ContentValues values = rowValues(table, row);
        Selection selection = selection(table, rowKey);
        if (db.update(table, values, selection.where, selection.args) != 1)
            throw new SyncConflict(table, rowKey, syncUuid, baseRevision, meta.revision);
        ContentValues update = new ContentValues();
        update.put("revision", meta.revision + 1L);
        update.put("updated_at", now);
        update.putNull("deleted_at");
        update.put("row_hash", rowHash(row));
        db.update(TABLE, update, "sync_uuid=?", new String[]{syncUuid});
        return metaJson(byUuid(db, syncUuid));
    }

    private static JSONObject applyV1(SQLiteDatabase db, JSONObject op,
            Set<String> touched) throws Exception {
        String table = op.optString("table", "");
        String id = op.optString("id", "");
        String action = op.optString("action", "");
        String baseHash = op.optString("baseRowSha256", "");
        String[] keys = keyColumns(table);
        if (keys == null || keys.length != 1 || !"id".equals(keys[0])
                || !id.matches("-?[0-9]+")
                || !("upsert".equals(action) || "delete".equals(action))
                || !("ABSENT".equals(baseHash) || baseHash.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("Nieprawidłowa operacja patcha v1.");
        String rowKey = canonicalLong(id);
        if (!touched.add(table + "\u0000" + rowKey))
            throw new IllegalArgumentException("Ten sam rekord występuje dwa razy.");

        JSONObject existing = readRow(db, table, rowKey);
        String actual = existing == null ? "ABSENT" : rowHash(existing);
        if (!actual.equals(baseHash))
            throw new SyncConflict(table, rowKey, "", -1L, -1L);

        Meta meta = liveForRow(db, table, rowKey);
        long now = System.currentTimeMillis();
        if ("delete".equals(action)) {
            if (existing != null) {
                Selection selection = selection(table, rowKey);
                db.delete(table, selection.where, selection.args);
            }
            if (meta != null) {
                ContentValues update = new ContentValues();
                update.put("revision", meta.revision + 1L);
                update.put("updated_at", now);
                update.put("deleted_at", now);
                update.put("row_hash", "DELETED");
                db.update(TABLE, update, "sync_uuid=?",
                    new String[]{meta.syncUuid});
                return metaJson(byUuid(db, meta.syncUuid));
            }
            JSONObject legacy = new JSONObject();
            legacy.put("table", table);
            legacy.put("rowKey", rowKey);
            legacy.put("deletedAt", now);
            return legacy;
        }

        JSONObject row = requiredRow(op, table, rowKey);
        ContentValues values = rowValues(table, row);
        if (existing == null) db.insertOrThrow(table, null, values);
        else {
            Selection selection = selection(table, rowKey);
            db.update(table, values, selection.where, selection.args);
        }
        String hash = rowHash(row);
        if (meta == null) {
            String uuid = UUID.randomUUID().toString();
            insertMeta(db, uuid, table, rowKey, 1L, now, null, hash);
            return metaJson(byUuid(db, uuid));
        }
        ContentValues update = new ContentValues();
        update.put("revision", meta.revision + 1L);
        update.put("updated_at", now);
        update.putNull("deleted_at");
        update.put("row_hash", hash);
        db.update(TABLE, update, "sync_uuid=?", new String[]{meta.syncUuid});
        return metaJson(byUuid(db, meta.syncUuid));
    }

    private static JSONObject requiredRow(JSONObject op, String table,
            String rowKey) throws Exception {
        JSONObject row = op.optJSONObject("row");
        if (row == null || !rowKey.equals(rowKey(table, row)))
            throw new IllegalArgumentException("Klucz rekordu nie zgadza się z operacją.");
        if (row.toString().getBytes(StandardCharsets.UTF_8).length > 256 * 1024)
            throw new IllegalArgumentException("Rekord synchronizacji jest za duży.");
        return row;
    }

    private static ContentValues rowValues(String table, JSONObject row)
            throws Exception {
        String[] columns = DataBackup.syncColumns(table);
        if (columns == null) throw new IllegalArgumentException("Nieznana tabela.");
        if (row.length() != columns.length)
            throw new IllegalArgumentException("Nieprawidłowy zestaw pól rekordu.");
        ContentValues values = new ContentValues();
        Set<String> allowed = new HashSet<>();
        for (String column : columns) allowed.add(column);
        Iterator<String> keys = row.keys();
        while (keys.hasNext()) if (!allowed.contains(keys.next()))
            throw new IllegalArgumentException("Niedozwolone pole rekordu.");

        for (String column : columns) {
            if (!row.has(column))
                throw new IllegalArgumentException("Brakuje pola " + column + ".");
            Object value = row.get(column);
            if (value == JSONObject.NULL) values.putNull(column);
            else if (value instanceof Number) {
                java.math.BigDecimal number = new java.math.BigDecimal(value.toString());
                if (number.scale() > 0 && number.stripTrailingZeros().scale() > 0)
                    throw new IllegalArgumentException("Pole " + column + " nie jest liczbą całkowitą.");
                values.put(column, number.longValueExact());
            } else if (value instanceof String) {
                String text = (String) value;
                if (text.indexOf('\0') >= 0 || text.length() > 20000)
                    throw new IllegalArgumentException("Nieprawidłowy tekst w polu " + column + ".");
                values.put(column, text);
            } else {
                throw new IllegalArgumentException("Nieprawidłowy typ pola " + column + ".");
            }
        }
        return values;
    }

    private static void refreshOne(SQLiteDatabase db, Meta meta) throws Exception {
        JSONObject row = readRow(db, meta.table, meta.rowKey);
        long now = System.currentTimeMillis();
        if (row == null) {
            if (meta.deletedAt == null) {
                ContentValues update = new ContentValues();
                update.put("revision", meta.revision + 1L);
                update.put("updated_at", now);
                update.put("deleted_at", now);
                update.put("row_hash", "DELETED");
                db.update(TABLE, update, "sync_uuid=?",
                    new String[]{meta.syncUuid});
            }
            return;
        }
        String hash = rowHash(row);
        if (meta.deletedAt != null) return;
        if (!hash.equals(meta.rowHash)) {
            ContentValues update = new ContentValues();
            update.put("revision", meta.revision + 1L);
            update.put("updated_at", now);
            update.put("row_hash", hash);
            db.update(TABLE, update, "sync_uuid=?",
                new String[]{meta.syncUuid});
        }
    }

    private static JSONObject readRow(SQLiteDatabase db, String table,
            String rowKey) throws Exception {
        String[] columns = DataBackup.syncColumns(table);
        if (columns == null) return null;
        Selection selection = selection(table, rowKey);
        try (Cursor cursor = db.query(table, columns,
                selection.where, selection.args, null, null, null, "1")) {
            return cursor.moveToFirst() ? cursorRow(cursor, columns) : null;
        }
    }

    private static boolean rowExists(SQLiteDatabase db, String table,
            String rowKey) throws Exception {
        return readRow(db, table, rowKey) != null;
    }

    private static JSONObject cursorRow(Cursor cursor, String[] columns)
            throws Exception {
        JSONObject row = new JSONObject();
        for (int i = 0; i < columns.length; i++) {
            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_NULL:
                    row.put(columns[i], JSONObject.NULL); break;
                case Cursor.FIELD_TYPE_INTEGER:
                    row.put(columns[i], cursor.getLong(i)); break;
                case Cursor.FIELD_TYPE_STRING:
                    row.put(columns[i], cursor.getString(i)); break;
                default:
                    throw new IllegalStateException("Unsupported sync field type");
            }
        }
        return row;
    }

    static String rowKey(String table, JSONObject row) {
        try {
            String[] keys = keyColumns(table);
            if (keys == null) return null;
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < keys.length; i++) {
                Object value = row.opt(keys[i]);
                if (!(value instanceof Number)) return null;
                if (i > 0) out.append(':');
                out.append(canonicalLong(value.toString()));
            }
            return out.toString();
        } catch (Exception invalid) {
            return null;
        }
    }

    private static String normalizeRowKey(String table, String raw) {
        try {
            String[] keys = keyColumns(table);
            if (keys == null) return null;
            String[] values = raw.split(":", -1);
            if (values.length != keys.length) return null;
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) out.append(':');
                out.append(canonicalLong(values[i]));
            }
            return out.toString();
        } catch (Exception invalid) {
            return null;
        }
    }

    private static Selection selection(String table, String rowKey) {
        String[] keys = keyColumns(table);
        if (keys == null) throw new IllegalArgumentException("Nieznana tabela.");
        String[] values = rowKey.split(":", -1);
        if (values.length != keys.length)
            throw new IllegalArgumentException("Nieprawidłowy klucz rekordu.");
        StringBuilder where = new StringBuilder();
        String[] args = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            args[i] = canonicalLong(values[i]);
            if (i > 0) where.append(" AND ");
            where.append(keys[i]).append("=?");
        }
        return new Selection(where.toString(), args);
    }

    private static String[] keyColumns(String table) {
        if (DataBackup.syncColumns(table) == null) return null;
        if ("task_rotation_members".equals(table))
            return new String[]{"task_id","member_id"};
        if ("pantry_packages".equals(table))
            return new String[]{"pantry_id"};
        return new String[]{"id"};
    }

    private static String canonicalLong(String raw) {
        return new java.math.BigDecimal(raw).longValueExact() + "";
    }

    private static Meta liveForRow(SQLiteDatabase db, String table, String rowKey) {
        try (Cursor cursor = db.query(TABLE,
                new String[]{"sync_uuid","table_name","row_key","revision",
                    "updated_at","deleted_at","row_hash"},
                "table_name=? AND row_key=? AND deleted_at IS NULL",
                new String[]{table,rowKey}, null, null, null, "1")) {
            return cursor.moveToFirst() ? meta(cursor) : null;
        }
    }

    private static Meta byUuid(SQLiteDatabase db, String uuid) {
        try (Cursor cursor = db.query(TABLE,
                new String[]{"sync_uuid","table_name","row_key","revision",
                    "updated_at","deleted_at","row_hash"},
                "sync_uuid=?", new String[]{uuid}, null, null, null, "1")) {
            return cursor.moveToFirst() ? meta(cursor) : null;
        }
    }

    private static Meta meta(Cursor cursor) {
        return new Meta(cursor.getString(0), cursor.getString(1),
            cursor.getString(2), cursor.getLong(3), cursor.getLong(4),
            cursor.isNull(5) ? null : cursor.getLong(5), cursor.getString(6));
    }

    private static void insertMeta(SQLiteDatabase db, String uuid, String table,
            String rowKey, long revision, long updatedAt, Long deletedAt,
            String rowHash) {
        ContentValues values = new ContentValues();
        values.put("sync_uuid", uuid);
        values.put("table_name", table);
        values.put("row_key", rowKey);
        values.put("revision", revision);
        values.put("updated_at", updatedAt);
        if (deletedAt == null) values.putNull("deleted_at");
        else values.put("deleted_at", deletedAt);
        values.put("row_hash", rowHash);
        db.insertOrThrow(TABLE, null, values);
    }

    private static JSONObject metaJson(Meta meta) throws Exception {
        JSONObject json = new JSONObject();
        json.put("syncUuid", meta.syncUuid);
        json.put("table", meta.table);
        json.put("rowKey", meta.rowKey);
        json.put("revision", meta.revision);
        json.put("updatedAt", meta.updatedAt);
        if (meta.deletedAt == null) json.put("deletedAt", JSONObject.NULL);
        else json.put("deletedAt", meta.deletedAt);
        json.put("rowHash", meta.rowHash);
        return json;
    }

    private static String rowHash(JSONObject row) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical(row).getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest)
            out.append(String.format(Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }

    private static String canonical(Object value) throws Exception {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            java.util.Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                String key = keys.get(i);
                out.append(JSONObject.quote(key)).append(':')
                    .append(canonical(object.get(key)));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) out.append(',');
                out.append(canonical(array.get(i)));
            }
            return out.append(']').toString();
        }
        if (value instanceof Number) {
            java.math.BigDecimal number = new java.math.BigDecimal(value.toString());
            if (number.compareTo(java.math.BigDecimal.ZERO) == 0) return "0";
            return number.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean) return value.toString();
        return JSONObject.quote(String.valueOf(value));
    }

    static final class SyncConflict extends Exception {
        final String table;
        final String rowKey;
        final String syncUuid;
        final long expectedRevision;
        final long actualRevision;

        SyncConflict(String table, String rowKey, String syncUuid,
                long expectedRevision, long actualRevision) {
            super(table + "#" + rowKey);
            this.table = table;
            this.rowKey = rowKey;
            this.syncUuid = syncUuid;
            this.expectedRevision = expectedRevision;
            this.actualRevision = actualRevision;
        }
    }

    private static final class Meta {
        final String syncUuid;
        final String table;
        final String rowKey;
        final long revision;
        final long updatedAt;
        final Long deletedAt;
        final String rowHash;

        Meta(String syncUuid, String table, String rowKey, long revision,
                long updatedAt, Long deletedAt, String rowHash) {
            this.syncUuid = syncUuid;
            this.table = table;
            this.rowKey = rowKey;
            this.revision = revision;
            this.updatedAt = updatedAt;
            this.deletedAt = deletedAt;
            this.rowHash = rowHash;
        }
    }

    private static final class Selection {
        final String where;
        final String[] args;
        Selection(String where, String[] args) {
            this.where = where;
            this.args = args;
        }
    }
}
