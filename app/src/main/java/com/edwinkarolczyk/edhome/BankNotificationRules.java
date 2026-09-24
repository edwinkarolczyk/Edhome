package com.edwinkarolczyk.edhome;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;

/** Conservative, unauthenticated bank notification HINT, never a posting. */
final class BankNotificationRules {
    private static final Pattern PLN=Pattern.compile(
        "(?iu)(?:\\bPLN\\s*)([0-9]{1,9}(?:[ \\u00a0][0-9]{3})*(?:[.,][0-9]{2})?)"
        +"|([0-9]{1,9}(?:[ \\u00a0][0-9]{3})*(?:[.,][0-9]{2})?)\\s*(?:PLN|zł)\\b");
    static final class Hint {
        final String kind;
        final long amountGrosz;
        Hint(String kind,long amountGrosz){
            this.kind=kind;this.amountGrosz=amountGrosz;
        }
    }

    private BankNotificationRules(){}

    static Hint parse(String input) {
        if(input==null || input.length()>2400)return null;
        String t=input.toLowerCase(Locale.ROOT);
        // Notification access is NOT banking authentication. Ignore login,
        // codes and requests for authorization, not store their text.
        if(t.matches("(?s).*(?:\\bkod\\b|\\botp\\b|\\bpin\\b|hasło|"
                +"logowani|autoryzacj|potwierdź|autoryzuj|\\bcvv\\b).*"))
            return null;
        boolean expense=t.matches("(?s).*(?:płatnoś|zapłac|zakup|"
            +"obciąż|wydatk|przelew wychodzący|przelew wysłan|"
            +"przelew wykonany|wypłat).*");
        boolean income=t.matches("(?s).*(?:wpłat|uznan|otrzyman|"
            +"przelew przychodzący|przelew otrzymany|wpływ).*");
        if(expense==income)return null;
        Matcher amounts=PLN.matcher(t);
        Set<Long> found=new HashSet<>();
        while(amounts.find()) {
            String raw=amounts.group(1)!=null?amounts.group(1):amounts.group(2);
            try {
                found.add(MoneyRules.parse(raw.replace(" ","")
                    .replace("\u00a0","")));
            } catch(IllegalArgumentException invalid){return null;}
        }
        if(found.size()!=1)return null;
        return new Hint(income?"income":"expense",found.iterator().next());
    }
}
