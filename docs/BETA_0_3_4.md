# EDHOME 0.3.4-beta.1 — Miejsca definiowane przez użytkownika

## Co zmienia się w Miejscach

- **Nazwa** jest całkowicie własna: Dom, Kuchnia, Szafka, Półka 1, Garaż itp. **Rodzaj** to niezależny, opcjonalny opis wpisywany ręcznie (np. pomieszczenie, regał); nie ma sztywnego wymogu wyboru „Dom / Ogród / Garaż / Warsztat / Pomieszczenie / Inne”.
- Każde miejsce może należeć do innego miejsca lub pozostać na poziomie głównym. Ekran pokazuje ścieżki: `Dom → Kuchnia → Szafka → Półka 1`. Nie narzucamy limitu liczby poziomów w zwykłym użyciu. „Półka 1” może istnieć pod Garażem i pod Kuchnią; identyczna nazwa **pod tym samym rodzicem** jest blokowana bez rozróżniania wielkości liter.
- `+ Dodaj miejsce główne` oraz `+ Wewnątrz` na karcie. `Przytrzymaj miejsce` → Edytuj / Przenieś / Dodaj wewnątrz / Usuń. Formularz pozwala wybrać rodzica, ręcznie opisać rodzaj i wybrać ikonę z lokalnej biblioteki. Pokazuje nową ścieżkę jeszcze przed zapisem.
- Przeniesienie miejsca z podmiejscami nie zmienia ID: ścieżki potomków aktualizują się automatycznie. Zablokowane jest przenoszenie do samego siebie, potomka i nieistniejącego miejsca. Usuwanie miejsca **z dziećmi jest blokowane** (najpierw trzeba je przenieść/usunąć); usunięcie pustego miejsca nie usuwa czynności ani ich historii, tylko czyści opcjonalne przypisanie miejsca.
- W edycji i podglądzie czynności wybierasz **pełną ścieżkę**, nie mylący samodzielny podpis „Półka 1”. Czynności nadal są samodzielne — przypisanie miejsca pozostaje opcjonalne.
- SQLite **v10 → v11** przebudowuje starą tabelę `places`, bo stara nazwa była globalnie unikalna. Zachowuje istniejące identyfikatory i nazwy, poprzedni `kind`; nowe pola `parent_id=NULL` i `icon='places'`. Stare wpisy pozostają poziomem głównym. SQLite zapewnia unikalność nazwy wśród rodzeństwa. Kopia JSON v11 zawiera oba pola, import kopii v2–v10 stosuje bezpieczne wartości domyślne. Import v11 odrzuca brak rodzica, zapętlenie i zduplikowane nazwy w jednym miejscu.
- Beta nadal bez PIN-u, Stable nadal z PIN-em; nowe miejsca i ikony nie są jeszcze magazynem QR ani synchronizacją Wi-Fi. Motywy WMM/Trener 2 EDHOME pozostają dodatkową opcją w lokalnych Ustawieniach.

## Test na telefonie

1. Wyeksportuj JSON przed aktualizacją. Zainstaluj podpisaną Betę na poprzedniej, bez odinstalowania. Sprawdź stare miejsca, zadania i historię.
2. Dodaj **Dom → Kuchnia → Szafka → Półka 1**. Dodaj **Garaż → Półka 1** — obie półki muszą być niezależne. Druga `Półka 1` w tej samej Kuchni/Szafce powinna zostać odrzucona.
3. Sprawdź, że rodzaj można wpisać jako `półka` lub zostawić pusty; zmień ikonę na półkę/szafkę i uruchom EDHOME ponownie.
4. Podepnij czynność do `Dom → Kuchnia → Szafka → Półka 1`. Przenieś `Szafka` do Garażu: zachowana czynność ma wskazywać nową ścieżkę, a historia ma pozostać bez zmian.
5. Spróbuj przenieść `Garaż` do jego własnej półki — zapis ma być zablokowany. Spróbuj usunąć rodzica mającego dzieci — ma pojawić się komunikat bez utraty podmiejsc.
6. Wyeksportuj/importuj kopię na **testowej instalacji**: przetestuj JSON v10 i v11, a także sprawdź, czy starsze wpisy bez rodzica pozostają na poziomie głównym.

CI weryfikuje model, migracje i podpis APK. Fizyczne gesty, czytelność i scenariusz przywracania wymagają testu użytkownika na Androidzie.
