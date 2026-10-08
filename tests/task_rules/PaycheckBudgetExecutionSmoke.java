package com.edwinkarolczyk.edhome;

/** Rejestr wykonania planu musi wykluczać transakcje niezwiązane z Budżetem. */
public final class PaycheckBudgetExecutionSmoke {
    private static void eq(long wanted,long got,String name) {
        if(wanted!=got)
            throw new AssertionError(name+": "+got+" zamiast "+wanted);
    }
    public static void main(String[] args) {
        eq(12000L,PaycheckBudgetExecutionRules.outsideBudget(
            260000L,248000L),"wydatki spoza budżetu");
        eq(0L,PaycheckBudgetExecutionRules.outsideBudget(
            97000L,97000L),"grupowy przelew + nadpłata bez reszty poza planem");
        eq(20000L,PaycheckBudgetExecutionRules.outsideBudget(
            70000L,50000L),"częściowo rozliczony plan + zakupy poza planem");
        eq(0L,PaycheckBudgetExecutionRules.outsideBudget(0L,0L),
            "pusty miesiąc");
        eq(150000L,PaycheckBudgetExecutionRules.coveredPlan(
            248000L,98000L),"realizacja zobowiązań");
        eq(248000L,PaycheckBudgetExecutionRules.coveredPlan(
            248000L,0L),"wykonane wszystkie zobowiązania");
        eq(0L,PaycheckBudgetExecutionRules.coveredPlan(
            248000L,248000L),"nic nie zapłacono");
        eq(0L,PaycheckBudgetExecutionRules.coveredPlan(
            248000L,260000L),"zaległość nie może tworzyć ujemnego wykonania");
        boolean rejected=false;
        try { PaycheckBudgetExecutionRules.outsideBudget(10000L,12000L); }
        catch(IllegalStateException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Niespójne przypisania zostały ukryte!");
        rejected=false;
        try { PaycheckBudgetExecutionRules.outsideBudget(-1L,0L); }
        catch(IllegalStateException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Ujemny stan został zaakceptowany!");
        System.out.println("Budżet etap 4: 10 scenariuszy rozdzielenia planu PASS");
    }
}
