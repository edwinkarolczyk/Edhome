package com.edwinkarolczyk.edhome;

/** Wspólne reguły prostego tekstu wpisywanego przez użytkownika. */
final class TextEntryRules {
    private TextEntryRules() { }

    static String capitalizeLabel(String raw) {
        String text=raw==null?"":raw.trim();
        if(text.isEmpty())return text;
        for(int i=0;i<text.length();i++) {
            char ch=text.charAt(i);
            if(!Character.isLetter(ch))continue;
            char upper=Character.toUpperCase(ch);
            if(upper==ch)return text;
            return text.substring(0,i)+upper+text.substring(i+1);
        }
        return text;
    }
}
