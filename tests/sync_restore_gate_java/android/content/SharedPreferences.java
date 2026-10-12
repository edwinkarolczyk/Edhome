package android.content;
public interface SharedPreferences {
    boolean getBoolean(String name, boolean defaultValue);
    Editor edit();
    interface Editor {
        Editor putBoolean(String name, boolean value);
        boolean commit();
    }
}