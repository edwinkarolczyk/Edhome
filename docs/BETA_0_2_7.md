# EDHOME 0.2.7-beta.1 — Beta bez PIN-u i wykonawcy czynności

## Zakres

- **Tylko Beta DEV bez PIN-u**: pierwsze uruchomienie, ponowny start, wyjście i powrót do aplikacji oraz przywrócenie JSON przechodzą prosto do panelu. Poprzedni PIN zachowano w lokalnych preferencjach, ale w Beta nie jest wymagany; nie jest częścią eksportu. Stable nadal używa dotychczasowej blokady PIN. To świadomie niezabezpieczona instalacja testowa — nie przechowuj w niej danych prywatnych.
- Moduł `Czynności → Domownicy / wykonawcy`: dodawanie lokalnych osób, kontrola powielonych nazw, usunięcie z potwierdzeniem; po usunięciu zadania zostają bez wykonawcy, wraz z historią.
- Formularz czynności: opcjonalny **jeden wykonawca** z listy, `Bez wyznaczonej osoby` jako domyślna opcja; karta czynności pokazuje przypisaną osobę. Można zmienić przypisanie przy edycji.
- SQLite `v6`: `household_members` oraz `tasks.assignee_id`; migracje `v1–v5 → v6` nie zmieniają danych historycznych i domyślnie nie przypisują osoby.
- Eksport/import JSON `databaseVersion: 6`: członkowie i powiązania; wcześniejsze kopie v2–v5 odtwarzają zadania bez wykonawców. Walidacja identyfikatorów i powielonych nazw przed importem.

**Jeszcze nie zrealizowano:** grafiku zmianowego, ręcznych wyjątków, automatycznego doboru domownika i trzech propozycji terminów. To osobny etap, nie ukryta funkcja tego wydania.

## Test telefonu

1. W 0.2.6 wykonaj eksport JSON poza aplikacją. Uruchom aktualizację 0.2.7 bez odinstalowywania.
2. Wersja Beta od razu otwiera panel: **bez ustawiania PIN-u, bez ekranu odblokowania, także po ponownym uruchomieniu i powrocie z tła**.
3. Czynności → Domownicy: dodaj dwie osoby; sprawdź, że duplikat o innej wielkości liter jest odrzucany.
4. Nowa czynność: wybierz wykonawcę, 45 min i priorytet Pilny. Zamknij i uruchom aplikację ponownie, edytuj przypisanie, sprawdź kartę czynności.
5. Usuń przypisaną osobę: zadanie i jego historia pozostają, a wykonawca znika.
6. Eksportuj kopię v6 i sprawdź przywrócenie na instalacji testowej. Dla starej kopii v5 wykonawca powinien pozostać pusty, inne dane bez zmian.
7. Zweryfikuj aktualizator i podpis APK. Nie sprawdzaj migracji na jedynej kopii danych.

Test kontraktowy Beta-PIN w CI sprawdza warunki gałęzi kodu, ale fizyczny test ekranu pozostaje osobnym kryterium odbioru.

## Kolejna wersja

0.2.8-beta.1 — lokalny grafik domownika i wyjątki kalendarzowe; dopiero na ich podstawie trzy propozycje terminu.
