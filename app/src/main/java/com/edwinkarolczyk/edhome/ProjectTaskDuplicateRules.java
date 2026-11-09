package com.edwinkarolczyk.edhome;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Conservative, offline name-similarity rules for project task duplicate warnings. */
final class ProjectTaskDuplicateRules {
    private ProjectTaskDuplicateRules() {}

    static boolean isSimilar(String first,String second) {
        String a=normalize(first),b=normalize(second);
        if(a.isEmpty()||b.isEmpty()||Math.min(a.length(),b.length())<5)return false;
        if(a.equals(b))return true;
        int shorter=Math.min(a.length(),b.length());
        int longer=Math.max(a.length(),b.length());
        if((a.contains(b)||b.contains(a))&&shorter/(double)longer>=0.52)return true;
        Set<String> left=tokens(a),right=tokens(b);
        Set<String> common=new HashSet<>(left);
        common.retainAll(right);
        if(common.size()>=2) {
            Set<String> all=new HashSet<>(left);
            all.addAll(right);
            if(common.size()/(double)all.size()>=0.60)return true;
        }
        if(left.size()==1&&right.size()==1) {
            int max=Math.max(a.length(),b.length());
            return max>=6&&distance(a,b)<=Math.max(1,(int)Math.floor(max*0.18));
        }
        return false;
    }

    static String normalize(String value) {
        if(value==null)return "";
        String plain=Normalizer.normalize(value.toLowerCase(Locale.ROOT),
            Normalizer.Form.NFD).replaceAll("\\p{M}+","");
        return plain.replaceAll("[^\\p{Alnum}]+"," ")
            .trim().replaceAll("\\s+"," ");
    }

    private static Set<String> tokens(String value) {
        Set<String> result=new HashSet<>();
        for(String token:value.split(" "))
            if(token.length()>=3)result.add(token);
        return result;
    }

    private static int distance(String a,String b) {
        int[] previous=new int[b.length()+1],current=new int[b.length()+1];
        for(int j=0;j<=b.length();j++)previous[j]=j;
        for(int i=1;i<=a.length();i++) {
            current[0]=i;
            for(int j=1;j<=b.length();j++)
                current[j]=Math.min(Math.min(current[j-1]+1,previous[j]+1),
                    previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            int[] swap=previous;previous=current;current=swap;
        }
        return previous[b.length()];
    }
}
