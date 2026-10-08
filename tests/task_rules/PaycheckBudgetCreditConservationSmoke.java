package com.edwinkarolczyk.edhome;

/** Wykonawczy test P0: ta sama nadpłata nie może zasilić kilku miesięcy ponad saldo. */
public final class PaycheckBudgetCreditConservationSmoke {
    private static int tested;
    private static void allow(long paid,long surplus,long planned,long applied) {
        PaycheckBudgetCreditMath.requireSourceAvailable(paid,surplus,planned,applied);
        tested++;
    }
    private static void deny(long paid,long surplus,long planned,long applied) {
        boolean rejected=false;
        try {PaycheckBudgetCreditMath.requireSourceAvailable(
            paid,surplus,planned,applied);}
        catch(IllegalArgumentException|ArithmeticException expected) {
            rejected=true;
        }
        if(!rejected)throw new AssertionError(
            "Nadpłata nie została zablokowana: wykorzystano "+applied);
        tested++;
    }
    private static void target(long plan,long applied,boolean accepted) {
        boolean succeeded=true;
        try {PaycheckBudgetCreditMath.requireTargetWithinPlan(plan,applied);}
        catch(IllegalArgumentException invalid) {succeeded=false;}
        if(succeeded!=accepted)throw new AssertionError(
            "Niepoprawny wynik docelowego limitu: "+plan+" / "+applied);
        tested++;
    }
    public static void main(String[] args) {
        allow(15000,0,10000,5000);  // 50 zł nadpłaty, odliczono 50 zł
        deny(15000,0,10000,5001);   // ten sam kredyt w dwóch miesiącach = 50,01
        allow(12000,3000,13000,2000); // 20 zł nadwyżki grupowego przelewu
        deny(12000,3000,13000,2001);
        allow(5000,0,10000,0);      // niedopłata nie jest źródłem kredytu
        deny(5000,0,10000,1);
        target(10000,10000,true);
        target(10000,10001,false);
        target(0,0,true);
        target(0,1,false);
        deny(-1,0,10000,0);
        deny(Long.MAX_VALUE,1,10000,1);
        System.out.println("P0 Nadpłaty 5C: "+tested+" testów groszowych PASS");
    }
}
