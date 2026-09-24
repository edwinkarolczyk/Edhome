package com.edwinkarolczyk.edhome;
final class BankNotificationRulesSmoke {
    private static void check(boolean value,String label) {
        if(!value)throw new AssertionError(label);
    }
    public static void main(String[] args) {
        BankNotificationRules.Hint expense=
            BankNotificationRules.parse("Zapłacono 12,50 PLN w sklepie");
        check(expense!=null && expense.kind.equals("expense")
            && expense.amountGrosz==1250,"exact expense");
        BankNotificationRules.Hint velo=
            BankNotificationRules.parse("Transakcja kartą\\nNowa transakcja kartą 5375 **** **** 2166 w wysokości 60.10 PLN w STACJA PALIW AELIN");
        check(velo!=null && velo.kind.equals("expense")
            && velo.amountGrosz==6010,"VeloBank card notification while app closed");
        check(BankNotificationRules.parse("Transakcja na rachunku 60,10 PLN")==null,
            "generic transaction is not necessarily card expense");
        BankNotificationRules.Hint income=
            BankNotificationRules.parse("Otrzymano przelew 1 200,00 zł");
        check(income!=null && income.kind.equals("income")
            && income.amountGrosz==120000,"exact income");
        check(BankNotificationRules.parse("Kod logowania 12,50 PLN")==null,
            "never parse authorization code");
        check(BankNotificationRules.parse("Płatność 12,50 PLN, saldo 30,50 PLN")==null,
            "reject ambiguous money");
        check(BankNotificationRules.parse("Otrzymano płatność 12,50 PLN")==null,
            "reject mixed direction");
        check(BankNotificationRules.parse("Wpływ 7,50 EUR")==null,
            "PLN only");
        check(BankNotificationRules.parse("Zapłacono 0 PLN")==null,
            "zero amount not evidence");
        check(BankNotificationRules.parse("12,50 zł")==null,
            "missing direction");
        System.out.println(
            "Bank notification hints: PLN, direction, no codes, no ambiguous posting PASS");
    }
}
