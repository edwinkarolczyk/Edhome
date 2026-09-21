package com.edwinkarolczyk.edhome;

public final class StorageQrSmoke {
    private static int passed;
    private static void check(boolean ok,String label){
        if(!ok)throw new AssertionError(label);
        passed++;
    }
    public static void main(String[] args){
        String box=StorageQr.encode("box",42L);
        String thing=StorageQr.encode("thing",42L);
        check(!box.equals(thing),"typed identifiers");
        check(StorageQr.decode(box).id==42,"box stable id");
        check("box".equals(StorageQr.decode(box).kind),"box type");
        check("thing".equals(StorageQr.decode(thing).kind),"thing type");
        check(StorageQr.decode("5901234123457")==null,"EAN is not object QR");
        check(StorageQr.decode("EDHOME:STORAGE:2:thing:42")==null,"wrong version");
        check(StorageQr.decode("EDHOME:STORAGE:1:box:0")==null,"zero rejected");
        check(StorageQr.decode("EDHOME:STORAGE:1:thing:01")==null,"leading zeros");
        check(StorageQr.decode("EDHOME:STORAGE:1:thing:42:garbage")==null,
            "excess fields");
        check(StorageQr.decode("EDHOME:STORAGE:1:thing:999999999999999999")==null,
            "overflow");
        try{StorageQr.encode("box",0);throw new AssertionError("invalid id");}
        catch(IllegalArgumentException expected){passed++;}
        System.out.println("EDHOME storage QR typed local identifiers: PASS "+passed);
    }
}
