# EDHOME 0.3.1-beta.1 — odpady i terminy wystawiania

## Zakres wydania

- Ekran **Czynności → Odpady i wystawianie** oraz skrót z głównego panelu (bez zmiany kolejności 9 kafelków).
- Ręczne dodanie frakcji: zmieszane, metale i tworzywa, papier, szkło, bio, inne; wybór daty **wystawienia**, nie daty odbioru; jednorazowo, tygodniowo, co 2 tygodnie lub miesięcznie. Pełna edycja reguł przez zwykły formularz Czynności.
- **Ten sam rekord zadania** (`tasks`) i ta sama definicja powtarzania, kalendarz, wykonanie/historia. Nie tworzymy osobnej „listy przypomnień” konkurującej z Czynnościami. Po potwierdzeniu cykliczne zadanie przechodzi na kolejny termin według wspólnych reguł. Potwierdzenie jednorazowego zamyka je.
- Nie pozwalamy utworzyć drugiego niezakończonego wystawienia tej samej frakcji na tę samą datę. Inne dni i frakcje są niezależne. Nie importujemy harmonogramów odbioru z gminy.
- Powiadomienia są **opcjonalne**, wyłącznie po włączeniu w Ustawieniach i zgodzie Androida. Istniejące lokalne przypomnienie sprawdza o około 09:00 czynności na dziś i zaległe, podając liczbę terminów wystawienia oraz innych zadań. Inexact alarm może się spóźnić. Nie wdrażamy obiecanego „dzień przed” ani indywidualnych godzin — dopiero w przyszłych wydaniach.
- SQLite `v8→v9`: typ czynności `general/waste` i opcjonalna frakcja; istniejące zadania dostają `general` bez utraty ID, powtórzeń ani historii. Eksport JSON v9 przechowuje oba pola, a import kopii v2–v8 ustawia domyślne wartości.
- Beta nadal **bez PIN-u**; Stable nadal z PIN-em. Podpis APK i adres aktualizacji pozostają niezmienione.

## Test odbioru na telefonie

1. Wyeksportuj kopię JSON poza aplikację. Zaktualizuj przez EDHOME albo zainstaluj podpisany APK na istniejącej Becie **bez odinstalowywania**.
2. Wejdź w Czynności → Odpady. Ustaw Papier na datę zgodną z lokalnym harmonogramem, „Co tydzień”. Potwierdź, że widzisz **ten sam wpis** na liście Czynności, w kalendarzu i na ekranie Odpady.
3. Spróbuj ponownie zapisać Papier na tę samą datę — powinien pojawić się komunikat o duplikacie. Dodaj Szkło na inną datę.
4. Potwierdź wystawienie Papieru — historia ma odnotować wykonanie, a następny termin przesunąć się zgodnie z regułą, bez tworzenia nowego obiektu. Zmiana terminu przez Edytuj na głównej liście ma być widoczna również w Odpady.
5. Włącz powiadomienia w Ustawieniach, przyznaj Androidowi uprawnienie, sprawdź przypomnienie na dziś lub zaległy termin po kolejnym harmonogramowym przebiegu. Bez zgody/ustawienia powiadomienie nie powinno się pojawić.
6. Zrób testowy import starej kopii v8 i nowej v9 na **dodatkowej, nie na jedynej** kopii danych; nie powinno być utraty historycznych czynności, zakupów, remanentów ani wykonawców.

**CI potwierdza kompilację, kontrakt migracji i podpis.** Instalacja/zgoda na powiadomienia i zachowanie systemu Android wymagają oddzielnego testu na urządzeniu.

## Następne kroki

0.3.2: godzina i wyprzedzenie przypomnień dla czynności/odpadów, konfigurowane przez użytkownika, bez nieuprawnionego omijania godzin ciszy; potem niezależne minutniki urządzeń. Harmonogram nie powinien wpisywać się automatycznie bez decyzji użytkownika.
