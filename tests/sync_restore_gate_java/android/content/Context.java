package android.content;
import java.util.HashMap;
import java.util.Map;
public class Context {
    public static final int MODE_PRIVATE = 0;
    private static final Map<String,MemoryPrefs> FILES=new HashMap<>();
    public Context getApplicationContext() { return this; }
    public synchronized SharedPreferences getSharedPreferences(String name,int mode) {
        return FILES.computeIfAbsent(name,key->new MemoryPrefs());
    }
    private static final class MemoryPrefs implements SharedPreferences {
        private final Map<String,Boolean> values=new HashMap<>();
        @Override public boolean getBoolean(String key,boolean fallback) {
            return values.getOrDefault(key,fallback);
        }
        @Override public Editor edit() {
            return new Editor() {
                private final Map<String,Boolean> pending=new HashMap<>();
                @Override public Editor putBoolean(String key,boolean value) {
                    pending.put(key,value); return this;
                }
                @Override public boolean commit() {
                    values.putAll(pending); return true;
                }
            };
        }
    }
}