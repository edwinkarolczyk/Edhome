package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.swing.table.DefaultTableModel;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Propozycje bank -> wspólny Budżet. WYŁĄCZNIE ODCZYT:
 * bez potwierdzania, tworzenia transakcji, zmiany salda i zapisu snapshotu.
 * Dzięki temu zgadywanie dopasowania nigdy nie księguje pieniędzy.
 */
final class DesktopBankBudgetSuggestions {
    static final class Proposal {
        final String evidenceKey, itemId, date, description, budgetName, kind;
        final long actualGrosz, plannedGrosz;
        final boolean confident;
        final String reason;

        Proposal(String evidenceKey,String itemId,String date,String description,
                String budgetName,String kind,long actualGrosz,long plannedGrosz,
                boolean confident,String reason) {
            this.evidenceKey=evidenceKey;
            this.itemId=itemId;
            this.date=date;
            this.description=description;
            this.budgetName=budgetName;
            this.kind=kind;
            this.actualGrosz=actualGrosz;
            this.plannedGrosz=plannedGrosz;
            this.confident=confident;
            this.reason=reason;
        }
    }

    private static final class Budget {
        final String id,name,kind;
        final long amount;
        final LocalDate due;
        Budget(String id,String name,String kind,long amount,LocalDate due) {
            this.id=id;this.name=name;this.kind=kind;
            this.amount=amount;this.due=due;
        }
    }

    private DesktopBankBudgetSuggestions() { }

    static List<Proposal> find(JsonObject snapshot,Set<String> focusKeys) {
        if(snapshot==null || !snapshot.has("settings")
                || !snapshot.get("settings").isJsonObject()
                || !snapshot.has("tables")
                || !snapshot.get("tables").isJsonObject())
            return List.of();
        JsonObject settings=snapshot.getAsJsonObject("settings");
        if(!DesktopBudgetMirror.hasSharedBudget(settings))
            return List.of();
        JsonObject tables=snapshot.getAsJsonObject("tables");
        JsonArray bank=tables.getAsJsonArray("bank_evidence_queue");
        if(bank==null)return List.of();
        JsonArray items=DesktopBudgetMirror.loadShared(settings);
        JsonArray recipients=JsonParser.parseString(
            string(settings,"paycheckRecipients","[]")).getAsJsonArray();
        Map<YearMonth,List<Budget>> months=new HashMap<>();
        List<Proposal> result=new ArrayList<>();
        Set<String> alreadyUsed=new HashSet<>();
        JsonArray payments=tables.getAsJsonArray("paycheck_transactions");
        if(payments!=null)for(JsonElement e:payments)if(e.isJsonObject()) {
            JsonObject tx=e.getAsJsonObject();
            if("confirmed".equals(string(tx,"status","")))
                alreadyUsed.add(string(tx,"statement_key",""));
        }

        // Wszystkie wpisy otwarte. Nie pomijamy starszych miesięcy/stron historii.
        for(JsonElement e:bank) {
            if(!e.isJsonObject())continue;
            JsonObject evidence=e.getAsJsonObject();
            if(!"open".equals(string(evidence,"state","")))continue;
            String key=string(evidence,"evidence_key","");
            if(key.isBlank()||alreadyUsed.contains(key)
                    ||focusKeys!=null&&!focusKeys.contains(key))continue;
            String kind=string(evidence,"kind","");
            if(!"expense".equals(kind)&&!"income".equals(kind))continue;
            LocalDate booked;
            long amount;
            try {
                booked=LocalDate.parse(string(evidence,"booking_date",""));
                amount=evidence.get("amount_grosz").getAsLong();
                if(amount<=0)continue;
            } catch(Exception invalid) {continue;}
            YearMonth month=YearMonth.from(booked);
            List<Budget> candidates=months.computeIfAbsent(month,
                m->budgetForMonth(items,recipients,m));
            String description=string(evidence,"description","");
            for(Budget budget:candidates) {
                if(!kind.equals(budget.kind)||budget.amount<=0)continue;
                boolean sameAmount=amount==budget.amount;
                boolean samePerson=overlap(description,budget.name);
                long days=Math.abs(ChronoUnit.DAYS.between(booked,budget.due));
                // Sama podobna kwota, sam odbiorca albo tylko miesiąc nie
                // wystarczają do sugerowania pary w dowolnym terminie.
                if(!(sameAmount&&days<=14 || samePerson&&days<=14))
                    continue;
                // Nawet przy identycznej kwocie bez nazwy odbiorcy
                // nie nadajemy statusu zielonego.
                boolean strong=sameAmount&&samePerson&&days<=7;
                String reason=strong?"Zgodna kwota, opis i termin":
                    sameAmount?"Zgodna kwota; sprawdź odbiorcę":
                    "Zbliżony opis; kwota wymaga sprawdzenia";
                result.add(new Proposal(key,budget.id,booked.toString(),
                    description,budget.name,kind,amount,budget.amount,
                    strong,reason));
            }
        }

        // Zielony tylko, jeśli odbiorca+kwota+termin wskazują jedną
        // pozycję Budżetu i jeden dowód bankowy, a nie konkurencyjne pary.
        Map<String,Integer> evidenceCounts=new HashMap<>(),itemCounts=new HashMap<>();
        for(Proposal p:result) {
            evidenceCounts.merge(p.evidenceKey,1,Integer::sum);
            itemCounts.merge(p.itemId+"|"+p.date.substring(0,7),1,Integer::sum);
        }
        List<Proposal> classified=new ArrayList<>(result.size());
        for(Proposal p:result) {
            boolean unique=p.confident
                &&evidenceCounts.getOrDefault(p.evidenceKey,0)==1
                &&itemCounts.getOrDefault(p.itemId+"|"+p.date.substring(0,7),0)==1;
            classified.add(new Proposal(p.evidenceKey,p.itemId,p.date,
                p.description,p.budgetName,p.kind,p.actualGrosz,p.plannedGrosz,
                unique,unique?p.reason:"Kandydat — wymaga ręcznego sprawdzenia"));
        }
        classified.sort(Comparator.comparing((Proposal p)->p.date).reversed()
            .thenComparing(p->p.evidenceKey)
            .thenComparing(p->p.itemId));
        return classified;
    }

    private static List<Budget> budgetForMonth(JsonArray items,
            JsonArray recipients,YearMonth month) {
        DefaultTableModel visible=DesktopBudgetMirror.month(
            items,recipients,month).rows;
        Map<String,JsonObject> byId=new HashMap<>();
        for(JsonElement e:items)if(e.isJsonObject()) {
            JsonObject item=e.getAsJsonObject();
            byId.put(string(item,"id",""),item);
        }
        List<Budget> result=new ArrayList<>();
        for(int i=0;i<visible.getRowCount();i++) {
            String id=String.valueOf(visible.getValueAt(i,4));
            JsonObject item=byId.get(id);
            if(item==null)continue;
            try {
                result.add(new Budget(id,
                    String.valueOf(visible.getValueAt(i,1)),
                    string(item,"kind",""),
                    DesktopBudgetMirror.amountFor(item,month),
                    LocalDate.parse(String.valueOf(visible.getValueAt(i,0)))));
            }catch(Exception invalid) {
                // Nie sugeruj dopasowania do błędnego terminu.
            }
        }
        return result;
    }

    private static boolean overlap(String bank,String budget) {
        Set<String> tokens=words(bank);
        for(String word:words(budget))
            if(tokens.contains(word))return true;
        return false;
    }

    private static Set<String> words(String text) {
        String normalized=Normalizer.normalize(text==null?"":text,
            Normalizer.Form.NFD).replaceAll("\\p{M}+","")
            .toLowerCase(Locale.ROOT).replace('ł','l');
        Set<String> tokens=new HashSet<>();
        for(String word:normalized.split("[^a-z0-9]+"))
            if(word.length()>=4&&!Set.of("przelew","platnosc",
                    "bank","konto","rachunek","wplata","wydatek","przychod",
                    "transakcja","bankowy","oplata","tytulem","zakup",
                    "zaplata").contains(word))
                tokens.add(word);
        return tokens;
    }

    private static String string(JsonObject row,String key,String fallback) {
        try{return row.has(key)&&!row.get(key).isJsonNull()
            ?row.get(key).getAsString():fallback;}
        catch(Exception ex){return fallback;}
    }
}
