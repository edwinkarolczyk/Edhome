package com.edwinkarolczyk.edhome;

import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Budżet Desktop -> Android: paczka wyłącznie zmienionych UUID.
 * Nie modyfikuje tabel ani preferencji przed sprawdzeniem konfliktów.
 * Zmienia wyłącznie trzy preferencje i pięć tabel budżetu
 * w transakcji. Nie odtwarza pozostałych danych EDHOME.
 */
final class PaycheckBudgetSyncPatch {
    private static final String[] FIELDS = {
        "paycheckMonthlyBudget","paycheckRecipients","paycheckBudgetHistory"
    };
    private static final String[] TABLES = {
        "budget_items","budget_occurrences","budget_recipients",
        "budget_credits","budget_history"
    };
    private PaycheckBudgetSyncPatch() { }

 
