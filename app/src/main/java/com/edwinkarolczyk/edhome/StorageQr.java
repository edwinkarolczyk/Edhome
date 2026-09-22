package com.edwinkarolczyk.edhome;

/** QR payload contains only kind and local ID; it is not a security credential. */
final class StorageQr {
    static final String PREFIX = "EDHOME:STORAGE:1:";
    private StorageQr() { }
    static String encode(String kind, long id) {
        if (id <= 0 || !("box".equals(kind) || "thing".equals(kind)))
            throw new IllegalArgumentException("Nieprawidłowy obiekt.");
        return PREFIX + kind + ":" + id;
    }
    static final class Target {
        final String kind;
        final long id;
        Target(String kind, long id) { this.kind = kind; this.id = id; }
    }
    static Target decode(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        String part = value.substring(PREFIX.length());
        int colon = part.indexOf(':');
        if (colon < 0 || part.indexOf(':', colon+1) != -1) return null;
        String kind = part.substring(0, colon);
        String id = part.substring(colon+1);
        if (!("box".equals(kind) || "thing".equals(kind))
                || !id.matches("[1-9][0-9]{0,17}")) return null;
        try { return new Target(kind, Long.parseLong(id)); }
        catch (NumberFormatException invalid) { return null; }
    }
}
