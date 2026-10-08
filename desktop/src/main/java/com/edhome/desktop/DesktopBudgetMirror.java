package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wspólny, bezpieczny podgląd definicji budżetu z synchronizacji Android ↔ PC.
 * Nie tworzy drugiego budżetu i nie nadpisuje starszego lokalnego pliku.
 * Zapis na Desktopie zostanie włączony dopiero po wdrożeniu rekordów delta.
 */
final class DesktopBudgetMirror {
    private DesktopBudgetMirror() { }

    static final class Summary {
        final DefaultTableModel rows;
        final long income;
        final long expenses;
        Summary(DefaultTableModel rows,long income,long expenses) {
            this.rows=rows;
            this.income=income;
            this.expenses=expenses;
        }
    }

    static boolean hasSharedBudget(JsonObject settings) {
        return settings != null && settings.has("paycheckMonthlyBudget")
            && settings.get("paycheckMonthlyBudget").isJsonPrimitive();
    }

    static JsonArray loadShared(JsonObject settings) {
        if (!hasSharedBudget(settings))
            throw new IllegalArgumentException("Brak wspólnego budżetu w synchronizacji.");
        JsonElement parsed=JsonParser.parseString(
            settings.get("paycheckMonthlyBudget").getAsString());
        if (!parsed.isJsonArray())
            throw new IllegalArgumentException("Błędna struktura Budżetu miesiąca.");
        return parsed.getAsJsonArray();
    }

    static Summary month(JsonArray items,JsonArray recipients,YearMonth month) {
        String[] names={"Termin","Odbiorca / zobowiązanie","Plan","Rodzaj","ID"};
        DefaultTableModel table=new DefaultTableModel(names,0) {
            @Override public boolean isCellEditable(int row,int column) { return false; }
        };
        Map<String,String> recipientsById=new HashMap<>();
        if(recipients!=null) for(JsonElement e:recipients) if(e.isJsonObject()) {
            JsonObject p=e.getAsJsonObject();
            recipientsById.put(str(p,"id",""),str(p,"name",""));
        }
        long income=0L,expenses=0L;
        List<Object[]> incoming=new ArrayList<>();
        List<Object[]> outgoing=new ArrayList<>();
        for(JsonElement e:items) {
            if(!e.isJsonObject()) continue;
            JsonObject item=e.getAsJsonObject();
            if(!occurs(item,month)) continue;
            boolean isIncome="income".equals(str(item,"kind","expense"));
            long amount=amountFor(item,month);
            if(isIncome) income=Math.addExact(income,amount);
            else expenses=Math.addExact(expenses,amount);
            String recipient=recipientsById.getOrDefault(
                str(item,"recipientId",""),"");
            String label=str(item,"name","Bez nazwy");
            if(!recipient.isBlank()) label=recipient+" • "+label;
            Object[] entry=new Object[]{
                plannedDate(item,month),label,money(amount),
                isIncome?"Wpływ":"Wydatek",str(item,"id","")};
            (isIncome?incoming:outgoing).add(entry);
        }
        // Wpływy u góry; bieżące wydatki według daty zapłaty, a nie
        // według przypadkowej kolejności zapisu na urządzeniu.
        Comparator<Object[]> byDate=Comparator
            .comparing((Object[] entry)->(String)entry[0])
            .thenComparing(entry->(String)entry[1],String.CASE_INSENSITIVE_ORDER);
        incoming.sort(byDate);
        outgoing.sort(byDate);
        for(Object[] entry:incoming)table.addRow(entry);
        for(Object[] entry:outgoing)table.addRow(entry);
        return new Summary(table,income,expenses);
    }

    /** Kartoteka faktycznych odbiorców i ich zobowiązań w wybranym miesiącu. */
    static DefaultTableModel recipientOverview(JsonArray items,
            JsonArray recipients,YearMonth month) {
        DefaultTableModel table=new DefaultTableModel(
                new String[]{"Odbiorca","Zobowiązania w miesiącu",
                    "Plan wydatków","ID"},0) {
            @Override public boolean isCellEditable(int row,int col) {return false;}
        };
        if(recipients==null)return table;
        List<JsonObject> directory=new ArrayList<>();
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(JsonElement candidate:recipients) {
            if(!candidate.isJsonObject())continue;
            JsonObject recipient=candidate.getAsJsonObject();
            String id=str(recipient,"id","");
            if(id.isBlank()||!seen.add(id))
                throw new IllegalArgumentException(
                    "Kartoteka zawiera pusty lub powtórzony identyfikator odbiorcy.");
            directory.add(recipient);
        }
        directory.sort(Comparator.comparing(
            (JsonObject entry)->str(entry,"name",""),String.CASE_INSENSITIVE_ORDER));
        for(JsonObject recipient:directory) {
            String id=str(recipient,"id","");
            int obligations=0;
            long amount=0L;
            for(JsonElement e:items) {
                if(!e.isJsonObject())continue;
                JsonObject item=e.getAsJsonObject();
                if(!id.equals(str(item,"recipientId",""))
                        ||!"expense".equals(str(item,"kind","expense"))
                        ||!occurs(item,month))continue;
                obligations++;
                amount=Math.addExact(amount,amountFor(item,month));
            }
            table.addRow(new Object[]{
                str(recipient,"name","Bez nazwy"),obligations,money(amount),id});
        }
        return table;
    }

    private static boolean occurs(JsonObject item,YearMonth month) {
        try {
            YearMonth start=YearMonth.parse(str(item,"startMonth",""));
            if(month.isBefore(start)) return false;
            String end=str(item,"endMonth","");
            if(!end.isBlank() && month.isAfter(YearMonth.parse(end))) return false;
            if(!bool(item,"active",true)) {
                String inactive=str(item,"inactiveFromMonth","");
                if(inactive.isBlank() || !month.isBefore(YearMonth.parse(inactive)))
                    return false;
            }
            JsonArray skipped=array(item,"skippedMonths");
            for(JsonElement e:skipped)
                if(e.isJsonPrimitive() && month.toString().equals(e.getAsString()))
                    return false;
            JsonArray closed=array(item,"closedMonths");
            for(JsonElement e:closed)
                if(e.isJsonPrimitive() && month.toString().equals(e.getAsString()))
                    return false;
            int cycle=(int)number(item,"cycleMonths",0L);
            long months=(month.getYear()-start.getYear())*12L
                + month.getMonthValue()-start.getMonthValue();
            if(cycle==0) return months==0L;
            if(cycle<0 || months%cycle!=0L) return false;
            if(bool(item,"installment",false)) {
                int installments=(int)number(item,"installmentCount",0L);
                if(installments>0 && months/cycle>=installments) return false;
            }
            return true;
        } catch(Exception invalid) {
            throw new IllegalArgumentException(
                "Nieprawidłowy zakres zobowiązania w synchronizacji.",invalid);
        }
    }

    static long amountFor(JsonObject item,YearMonth month) {
        JsonObject overrides=object(item,"monthAmountOverrides");
        if(overrides.has(month.toString()))
            return overrides.get(month.toString()).getAsLong();
        long result=number(item,"amountGrosz",0L);
        JsonObject changes=object(item,"amountChanges");
        YearMonth last=null;
        for(Map.Entry<String,JsonElement> e:changes.entrySet()) {
            try {
                YearMonth effective=YearMonth.parse(e.getKey());
                if(!effective.isAfter(month)
                        && (last==null || effective.isAfter(last))) {
                    result=e.getValue().getAsLong();
                    last=effective;
                }
            } catch(Exception invalid) {
                throw new IllegalArgumentException("Błędna korekta kwoty budżetu.",invalid);
            }
        }
        return result;
    }

    private static String plannedDate(JsonObject item,YearMonth month) {
        JsonObject specific=object(item,"plannedPaymentDates");
        if(specific.has(month.toString()))
            return specific.get(month.toString()).getAsString();
        int day=(int)number(item,"templateDueDay",
            number(item,"dueDay",10L));
        if(day<=0) day=10;
        LocalDate plan=month.atDay(Math.min(day,month.lengthOfMonth()));
        JsonObject invoices=object(item,"invoiceDueDates");
        if(invoices.has(month.toString())) {
            LocalDate due=LocalDate.parse(
                invoices.get(month.toString()).getAsString());
            if(due.isBefore(plan)) plan=due;
        }
        return plan.toString();
    }

    private static JsonObject object(JsonObject o,String key) {
        return o.has(key) && o.get(key).isJsonObject()
            ?o.getAsJsonObject(key):new JsonObject();
    }
    private static JsonArray array(JsonObject o,String key) {
        return o.has(key) && o.get(key).isJsonArray()
            ?o.getAsJsonArray(key):new JsonArray();
    }
    private static String str(JsonObject o,String key,String fallback) {
        try { return o.has(key) && !o.get(key).isJsonNull()
            ?o.get(key).getAsString():fallback; }
        catch(Exception ignored) { return fallback; }
    }
    private static long number(JsonObject o,String key,long fallback) {
        try { return o.has(key) && !o.get(key).isJsonNull()
            ?o.get(key).getAsLong():fallback; }
        catch(Exception ignored) { return fallback; }
    }
    private static boolean bool(JsonObject o,String key,boolean fallback) {
        try { return o.has(key) && !o.get(key).isJsonNull()
            ?o.get(key).getAsBoolean():fallback; }
        catch(Exception ignored) { return fallback; }
    }
    private static String money(long grosz) {
        return String.format(Locale.forLanguageTag("pl-PL"),
            "%,.2f zł",grosz/100.0);
    }

    static void show(Component owner,JsonObject settings) {
        showBudget(owner,settings,null,null);
    }

    static void showEditable(Component owner,JsonObject sharedSnapshot,
            java.util.function.Consumer<JsonObject> onSave) {
        if(sharedSnapshot==null||!sharedSnapshot.has("settings")
                ||!sharedSnapshot.get("settings").isJsonObject())
            throw new IllegalArgumentException("Brak wspólnego Budżetu.");
        showBudget(owner,sharedSnapshot.getAsJsonObject("settings"),
            sharedSnapshot,onSave);
    }

    private static void showBudget(Component owner,JsonObject settings,
            JsonObject sharedSnapshot,java.util.function.Consumer<JsonObject> onSave) {
        JsonArray items,recipients;
        try {
            items=loadShared(settings);
            JsonElement r=JsonParser.parseString(
                settings.has("paycheckRecipients")
                    ?settings.get("paycheckRecipients").getAsString():"[]");
            recipients=r.isJsonArray()?r.getAsJsonArray():new JsonArray();
        } catch(Exception error) {
            JOptionPane.showMessageDialog(owner,
                "Nie można odczytać zsynchronizowanego Budżetu:\n"
                    +error.getMessage(),
                "PayCheck • Budżet miesiąca",JOptionPane.ERROR_MESSAGE);
            return;
        }
        YearMonth current=YearMonth.now();
        while(true) {
            Summary summary;
            try { summary=month(items,recipients,current); }
            catch(Exception error) {
                JOptionPane.showMessageDialog(owner,
                    "Nieprawidłowe dane Budżetu:\n"+error.getMessage(),
                    "Budżet miesiąca",JOptionPane.ERROR_MESSAGE);
                return;
            }
            JTable table=new JTable(summary.rows);
            table.setAutoCreateRowSorter(true);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getColumnModel().getColumn(4).setMinWidth(0);
            table.getColumnModel().getColumn(4).setMaxWidth(0);
            JScrollPane scroll=new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(850,400));
            JPanel body=new JPanel(new BorderLayout(0,8));
            String monthLabel=current.format(
                java.time.format.DateTimeFormatter.ofPattern(
                    "LLLL yyyy",Locale.forLanguageTag("pl-PL")));
            String title=monthLabel.substring(0,1).toUpperCase(
                Locale.forLanguageTag("pl-PL"))+monthLabel.substring(1);
            body.add(new JLabel("<html><b>"+title+"</b>  |  Wpływy: "
                +money(summary.income)+"  |  Wydatki: "
                +money(summary.expenses)+"  |  Różnica: "
                +money(Math.subtractExact(summary.income,summary.expenses))
                +"</html>"),BorderLayout.NORTH);
            body.add(scroll,BorderLayout.CENTER);
            boolean editable=sharedSnapshot!=null&&onSave!=null;
            body.add(new JLabel(editable
                ? "<html>Wspólny Budżet Android ↔ PC. Edycja nazwy i kwoty "
                    +"wybranego miesiąca. Zmiany zapiszą się lokalnie i przejdą "
                    +"synchronizację z kontrolą konfliktu. "
                    +"Stary plik planu PC pozostaje bez zmian.</html>"
                : "<html>Ten sam Budżet co na Androidzie "
                    +"(ostatni stan synchronizacji). Podgląd tylko do odczytu. "
                    +"Starszy lokalny plik planu nie został skasowany.</html>"),
                BorderLayout.SOUTH);
            Object[] options=editable
                ?new Object[]{"‹ Poprzedni","Następny ›",
                    "Bieżący miesiąc","Odbiorcy",
                    "Edytuj pozycję","Zamknij"}
                :new Object[]{"‹ Poprzedni","Następny ›",
                    "Bieżący miesiąc","Odbiorcy","Zamknij"};
            int action=JOptionPane.showOptionDialog(owner,body,
                "PayCheck • wspólny Budżet miesiąca",
                JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,
                null,options,options[options.length-1]);
            if(action==0) current=current.minusMonths(1);
            else if(action==1) current=current.plusMonths(1);
            else if(action==2) current=YearMonth.now();
            else if(action==3) {
                try {
                    JTable recipientTable=new JTable(
                        recipientOverview(items,recipients,current));
                    recipientTable.setAutoCreateRowSorter(true);
                    recipientTable.getColumnModel().getColumn(3).setMinWidth(0);
                    recipientTable.getColumnModel().getColumn(3).setMaxWidth(0);
                    JScrollPane directoryScroll=new JScrollPane(recipientTable);
                    directoryScroll.setPreferredSize(new Dimension(650,280));
                    JOptionPane.showMessageDialog(owner,directoryScroll,
                        "Odbiorcy i zobowiązania • "+current,
                        JOptionPane.PLAIN_MESSAGE);
                } catch(Exception invalid) {
                    JOptionPane.showMessageDialog(owner,
                        "Nie można wyświetlić odbiorców: "
                            +invalid.getMessage(),
                        "Budżet miesiąca",JOptionPane.ERROR_MESSAGE);
                }
            }
            else if(editable&&action==4) {
                int selected=table.getSelectedRow();
                if(selected<0) {
                    JOptionPane.showMessageDialog(owner,
                        "Najpierw zaznacz pozycję budżetu do edycji.");
                    continue;
                }
                String itemId=String.valueOf(summary.rows.getValueAt(
                    table.convertRowIndexToModel(selected),4));
                JsonObject item=null;
                for(JsonElement e:items)if(e.isJsonObject()&&itemId.equals(
                        str(e.getAsJsonObject(),"id","")))item=e.getAsJsonObject();
                if(item==null)continue;
                JTextField name=new JTextField(str(item,"name",""),28);
                javax.swing.JFormattedTextField amount=new javax.swing.JFormattedTextField();
                amount.setText(java.math.BigDecimal.valueOf(
                    amountFor(item,current),2).toPlainString().replace('.',','));
                JPanel fields=new JPanel(new GridLayout(0,1,4,4));
                fields.add(new JLabel("Nazwa pozycji:"));
                fields.add(name);
                fields.add(new JLabel("Planowana kwota tylko za "+current+" (zł):"));
                fields.add(amount);
                if(JOptionPane.showConfirmDialog(owner,fields,
                        "Edytuj pozycję Budżetu",JOptionPane.OK_CANCEL_OPTION)
                        !=JOptionPane.OK_OPTION)continue;
                try {
                    String raw=amount.getText().trim().replace(" ","")
                        .replace(',','.');
                    if(!raw.matches("[0-9]{1,9}([.][0-9]{1,2})?"))
                        throw new IllegalArgumentException(
                            "Wprowadź kwotę z maksymalnie dwoma miejscami po przecinku.");
                    long grosz=new java.math.BigDecimal(raw).movePointRight(2)
                        .longValueExact();
                    JsonObject edited=DesktopSharedBudgetEdits.changeMonth(
                        sharedSnapshot,itemId,name.getText(),current,grosz);
                    if(edited!=sharedSnapshot) {
                        onSave.accept(edited);
                        sharedSnapshot=edited;
                        settings=edited.getAsJsonObject("settings");
                        items=loadShared(settings);
                    }
                } catch(Exception invalid) {
                    JOptionPane.showMessageDialog(owner,
                        "Nie zapisano zmiany: "+invalid.getMessage(),
                        "Budżet miesiąca",JOptionPane.ERROR_MESSAGE);
                }
            } else return;
        }
    }

}
