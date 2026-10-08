package com.edwinkarolczyk.edhome;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Deterministyczny klucz SQLite dla tej samej encji na telefonie i PC. */
final class PaycheckBudgetStableIds {
    private PaycheckBudgetStableIds() { }

    static long of(String domain,String key) throws Exception {
        if(domain==null || !domain.matches("budget_[a-z_]{3,32}")
                || key==null || key.isBlank() || key.length()>200)
            throw new IllegalArgumentException("Nieprawidłowa tożsamość rekordu budżetu.");
        byte[] digest=MessageDigest.getInstance("SHA-256")
            .digest((domain+"\u0000"+key).getBytes(StandardCharsets.UTF_8));
        long result=0L;
        for(int i=0;i<8;i++) result=(result<<8)|(digest[i]&255L);
        long stable=result&Long.MAX_VALUE;
        return stable==0L ? 1L : stable;
    }
}
