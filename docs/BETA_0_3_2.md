# EDHOME 0.3.2-beta.1 — indywidualna godzina i wyprzedzenie przypomnień

## Zmiany na `beta`

- Edycja **każdej czynności**, w tym „Wystaw: …” dla odpadów: **Standardowo ok. 09:00** albo **Moja godzina i wyprzedzenie**. Użytkownik wybiera godzinę w systemowym zegarze 24 h i wyprzedzenie: 0/1/2/3/7 dni przed datą wykonania. Datą odniesienia przy odpadach pozostaje *dzień wystawienia*, nie odbioru.
- Własna godzina wymaga daty; zwykła czynność może nadal istnieć bez terminu i miejsca, ale wtedy nie może mieć zaplanowanego indywidualnego alarmu.
- **Cisza 22:00–07:00:** żądane 22:00–23:59 zostaje przyspieszone do 21:00 tego samego dnia, a 00:00–06:59 przesunięte na 07:00. Po opóźnieniu alarmu przez Androida do godzin ciszy system ponownie ustawia go na najbliższą 07:00. Nie jest to obietnica doręczenia co do minuty. Android/Doze może opóźniać alarmy.
- Pozostaje **globalne opt-in** w Ustawieniach oraz systemowa zgoda na powiadomienia. Domyślne zadania bez indywidualnej godziny używają istniejącego przypomnienia około 09:00 dla dzisiejszych i zaległych. Zaległe zadania z indywidualnym czasem są następnie uwzględniane w dziennym zestawieniu aż do wykonania.
- Każdy indywidualny alarm jest powiązany z **ID + datą + godziną + wyprzedzeniem**; stary, edytowany, usunięty albo zakończony wpis nie powinien zgłosić historycznego terminu. Nowa data po wykonaniu cyklicznej czynności tworzy nowy termin; doręczona konkretna wersja terminu jest oznaczana, by nie wysyłać jej wielokrotnie.
- Po restarcie telefonu, aktualizacji APK i zmianie strefy/czasu przypomnienia są odtwarzane. Po imporcie kopii aplikacja resetuje wyłącznie identyfikatory doręczonych przypomnień i układa alarmy dla zaimportowanych czynności. Tytuły zadań nie są wysyłane do internetu ani pokazywane w treści zablokowanego ekranu.
- SQLite **v10**: `tasks.remind_time` (nullable HH:mm) i `tasks.reminder_lead_days` (domyślnie 0), migracja v9→v10 bez naruszania danych, import kopii v2–v9 z pustą indywidualną godziną. Kopia v10 przenosi ustawienia per czynność. Beta **bez PIN-u**, Stable nadal wymaga PIN.

**Ograniczenia:** brak gwarancji dokładnej minuty, brak własnych godzin ciszy i osobnych powtórek/snooze, brak synchronizacji na dwóch telefonach. Przy niedostępnych uprawnieniach Androida nie twierdzimy, że powiadomienie zostało doręczone.

## Test odbiorczy na Androidzie

1. Przed aktualizacją wyeksportuj JSON poza aplikację; zainstaluj 0.3.2 na istniejącej Becie bez odinstalowywania.
2. Sprawdź, że starsze zadania wciąż mają standardowe przypomnienie; zadanie bez daty nadal się zapisuje, lecz przy wyborze „Moja godzina” powinno wymagać terminu.
3. Utwórz „Wystaw: Papier” na np. środę; w edycji ustaw **wtorek 19:30** (1 dzień wcześniej). Sprawdź, że opis zadania pokazuje wtorkową datę i orientacyjną godzinę.
4. Zmień godzinę i datę, zamknij aplikację, uruchom ponownie, wyeksportuj/importuj na dodatkowej kopii. Weryfikuj, że nie dochodzi do alarmu dla poprzedniego terminu ani duplikatu doręczenia.
5. Wybierz 23:30 i 05:30, sprawdź w opisie przesunięcie odpowiednio na 21:00 i 07:00; wyłącz globalne przypomnienia i sprawdź brak powiadomień. Uprawnienie Androida pozostaje odrębnym warunkiem.
6. Potwierdź wykonanie powtarzalnej czynności i sprawdź nowy termin, historię i kolejne przypomnienie. Nie testuj importu destrukcyjnego na jedynej kopii.

CI weryfikuje zasady czasu, migrację, kontrakty opt-in i kompilację/podpis; rzeczywiste zachowanie AlarmManager/Doze wymaga testu na telefonie.
