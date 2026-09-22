package com.edwinkarolczyk.edhome;

public final class MoneyRulesSmoke {
    private static int passed;
    private static void check(boolean yes,String label) {
        if(!yes)throw new AssertionError(label);
        passed++;
    }
    private static void reject(String input){
        try{MoneyRules.parse(input);throw new AssertionError("Accepted "+input);}
        catch(IllegalArgumentException expected){passed++;}
    }
    public static void main(String[] args){
        check(MoneyRules.parse("1")==100,"PLN to grosze");
        check(MoneyRules.parse("12,50")==1250,"comma decimal");
        check(MoneyRules.parse("12.50")==1250,"dot decimal");
        check(MoneyRules.parse("0,01")==1,"smallest positive");
        check(MoneyRules.parse("999999999,99")==MoneyRules.MAX_GROSZ,
            "maximum money without overflow");
        check(MoneyRules.format(1250).equals("12,50 zł"),"format");
        check(MoneyRules.format(-1250).equals("−12,50 zł"),"negative balance");
        reject("0");reject("0,00");reject("-1");reject("1,234");
        reject("NaN");reject("9999999999");reject("1e2");
        check(MoneyRules.category("shopping"),"category");
        check(!MoneyRules.category("private"),"reject unsupported category");
        System.out.println("PayCheck integer-grosz accounting: PASS "+passed);
    }
}
