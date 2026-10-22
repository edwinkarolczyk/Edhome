package com.edhome.desktop;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

final class DesktopProjectCostTest {
    private static Object call(String name,Class<?>[] types,Object... args) {
        try {
            Method method=EdhomeDesktop.class.getDeclaredMethod(name,types);
            method.setAccessible(true);
            return method.invoke(null,args);
        } catch(InvocationTargetException target) {
            if(target.getCause() instanceof RuntimeException)
                throw (RuntimeException)target.getCause();
            throw new RuntimeException(target.getCause());
        } catch(Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static long qty(String raw,boolean zero) {
        return (Long)call("desktopProjectCostQtyMilli",
            new Class<?>[]{String.class,boolean.class},raw,zero);
    }

    private static long price(String raw) {
        return (Long)call("desktopProjectCostPriceGrosz",
            new Class<?>[]{String.class},raw);
    }

    private static long total(long milli,long grosz) {
        JsonObject cost=new JsonObject();
        cost.addProperty("qty_milli",milli);
        cost.addProperty("unit_price_grosz",grosz);
        return (Long)call("desktopProjectCostValue",
            new Class<?>[]{JsonObject.class},cost);
    }

    @Test void iloscSztukiMetryICzesci() {
        assertEquals(1000L,qty("1",false));
        assertEquals(2000L,qty("2",false));
        assertEquals(1250L,qty("1,25",false));
        assertEquals(100L,qty("0.1",false));
        assertEquals(0L,qty("0",true));
        assertThrows(IllegalArgumentException.class,()->qty("0",false));
        assertThrows(IllegalArgumentException.class,()->qty("-1",true));
        assertThrows(IllegalArgumentException.class,()->qty("1,0001",true));
        assertThrows(IllegalArgumentException.class,()->qty("1000001",true));
    }

    @Test void kosztPrzeliczanyZeStalaCenaJednostkowa() {
        assertEquals(400L,price("4"));
        assertEquals(450L,price("4,50"));
        assertEquals(400L,total(1000,400));
        assertEquals(800L,total(2000,400));
        assertEquals(500L,total(1250,400));
        assertEquals(0L,total(0,400));
        assertEquals(1L,total(500,1)); // Zaokrąglanie do grosza.
        assertThrows(IllegalArgumentException.class,()->price("-1"));
        assertThrows(IllegalArgumentException.class,()->price("4,001"));
    }
}
