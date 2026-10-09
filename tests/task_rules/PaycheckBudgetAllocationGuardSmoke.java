package com.edwinkarolczyk.edhome;

/** Test wykonywalny P0: jedna potwierdzona wpłata nie może zapłacić dwa razy. */
public final class PaycheckBudgetAllocationGuardSmoke {
    private static int tests=0;

    private static long add(long used,long amount,boolean matched,
            Long allocation,Long surplus) {
        tests++;
        return PaycheckBudgetAllocationGuard.addUsed(
            used,amount,matched,allocation,surplus);
    }

    private static void deny(long used,long amount,boolean matched,
            Long allocation,Long surplus) {
        boolean rejected=false;
        try { add(used,amount,matched,allocation,surplus); }
        catch(IllegalArgumentException|ArithmeticException correct) {
            rejected=true;
        }
        if(!rejected)throw new AssertionError(
            "Dopuszczono przekroczenie kwoty potwierdzonego przelewu.");
    }

    private static void equal(long expected,long actual) {
        if(actual!=expected)throw new AssertionError(
            "Oczekiwano "+expected+" gr, otrzymano "+actual+" gr.");
    }

    public static void main(String[] args) {
        final long payment=10000L;
        equal(0L,add(0L,payment,false,null,null));
        equal(10000L,add(0L,payment,true,null,null));
        deny(10000L,payment,true,100L,null);       // pełne + kolejny rachunek
        equal(4000L,add(0L,payment,true,4000L,null));
        equal(10000L,add(4000L,payment,true,6000L,null));
        deny(4000L,payment,true,6001L,null);      // 100,01 zł
        equal(10000L,add(0L,payment,true,4000L,6000L));
        deny(10000L,payment,true,1L,null);        // nadpłata też jest wydana
        deny(0L,payment,false,1000L,null);        // alokacja bez przypisania
        deny(0L,payment,false,null,1000L);        // nadpłata bez przypisania
        deny(0L,payment,true,0L,null);
        deny(0L,payment,true,-1L,null);
        deny(0L,payment,true,1000L,-1L);
        deny(0L,payment,true,null,1L);            // pełne plus nadpłata
        deny(0L,Long.MAX_VALUE,true,Long.MAX_VALUE,1L);
        System.out.println("PayCheck P0: "+tests
            +" testów pełnych/częściowych przypisań i nadpłat PASS");
    }
}
