package com.edwinkarolczyk.edhome;

final class BankBudgetMatchSmoke {
    private static void check(boolean yes,String what) {
        if(!yes)throw new AssertionError(what);
    }
    public static void main(String[] args) {
        check(BankBudgetMatchRules.descriptionIdentifies(
            "Przelew do TAURON Dystrybucja","Prąd","Tauron"),"Tauron odbiorca");
        check(BankBudgetMatchRules.descriptionIdentifies(
            "NETFLIX.COM subskrypcja 2026","Netflix",""),"Netflix");
        check(BankBudgetMatchRules.descriptionIdentifies(
            "Opłata za ubezpieczenie PZU POLISA","PZU Polisa","PZU Polisa"),
            "PZU Polisa");
        check(!BankBudgetMatchRules.descriptionIdentifies(
            "Internet - opłata","Internet",""),"ogólny internet");
        check(!BankBudgetMatchRules.descriptionIdentifies(
            "Przelew RATA 2026","Rata 5/12",""),"ogólna rata");
        check(!BankBudgetMatchRules.descriptionIdentifies(
            "Przelew do ENERGA","Rata TAURON","Tauron"),
            "inny odbiorca");
        check(!BankBudgetMatchRules.descriptionIdentifies(
            "PRZELEW","Energia",""),"brak nazwy");
        check(BankBudgetMatchRules.descriptionIdentifies(
            "Velo - WODOCIĄGI BIELSKO 2026","Wodociągi Bielsko",""),
            "znaki diakrytyczne");
        check(!BankBudgetMatchRules.descriptionIdentifies(
            "Opłata za PRĄD","Prąd",""),"sama kwota/kategoria niedopuszczalna");
        System.out.println("PASS budzet bank: tylko charakterystyczna nazwa odbiorcy, brak dopasowan po kwocie lub ogolnikach");
    }
}
