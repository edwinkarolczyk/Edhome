# EDHOME 0.2.9-beta.1 — trzy propozycje terminu czynności

## Wykonany zakres

- Formularz Czynności → **Zaproponuj 3 terminy** po wybraniu wykonawcy i wpisaniu szacowanego czasu 1–480 minut.
- Propozycje w ciągu najbliższych 28 dni: maksymalnie **jedno okno na dzień**, do trzech osobnych dat, tylko przy jawnie ustawionym grafiku (`Wolne` jest dostępnością, `Nie ustawiono` oznacza brak danych), z uwzględnieniem wyjątków na datę.
- Początkowe proponowane godziny to 08:00, 10:00, 12:00, 14:00, 16:00, 18:00 lub 19:00; koniec nie może wypaść później niż 21:00. Algorytm unika zmian 06–14, 14–22 i 22–06 z godzinnym buforem oraz zostawia co najmniej 8 godzin odpoczynku po poprzedniej nocnej, czyli do 14:00.
- **Zaznaczenie propozycji tylko ustawia datę w formularzu. Zapis następuje dopiero po kliknięciu Zapisz.** Anuluj nie zmienia zadania. Przypisanie osoby jest opcjonalne, ale bez niego albo bez jawnego grafiku propozycje nie są zgadywane. Ręczny wybór daty nadal działa.
- Okna godzinowe są wyłącznie podpowiedzią w tej wersji: **zapisujemy datę, nie godzinę rozpoczęcia**. Nie jest jeszcze sprawdzany konflikt z innymi czynnościami, wydarzeniami i dojazdem; bez przechowywania godzin nie wolno twierdzić, że termin jest faktycznie wolny.
- Nie ma zmiany bazy ani formatu kopii (pozostaje SQLite v7 i backup v7). Beta nadal bez PIN-u, Stable z PIN-em, publiczny kanał APK bez zmian. Wersja `0.2.9-beta.1`, `versionCode=19`.
- Dodano testy czystej logiki terminu: nieznany grafik, dzień wolny, zmiana poranna/pop., nocna i odpoczynek, różne daty, granice czasu i dni już minione.

## Test na telefonie

1. Zrób kopię JSON. Zaktualizuj 0.2.8→0.2.9 bez odinstalowania, potwierdź brak PIN-u i zachowanie zadań/domowników/grafików.
2. Domownicy → ustaw poniedziałek 06–14, wtorek 14–22, środę Wolne. Ustaw wyjątek na poniedziałek Wolne.
3. W formularzu wybierz wykonawcę i 45 minut, naciśnij **Zaproponuj 3 terminy**. Wyjątek ma wygrać z grafikiem tygodnia; każdy zaproponowany termin to inna data.
4. Wybierz propozycję, potem Anuluj: nie może być zapisana. Ponownie wybierz propozycję i Zapisz: zachowana ma być wybrana data wraz z dotychczasowymi polami.
5. Wybierz osobę bez ustawionego grafiku albo pozostaw wykonawcę pustego: program ma wyświetlić brak wiarygodnych terminów, nie przydzielać daty.
6. Długą czynność i zmianę nocną przetestuj z uwzględnieniem odpoczynku do 14:00 dnia po nocce. Porównaj wyniki ze swoim realnym planem — na razie nie uwzględniają kolizji z innymi zdarzeniami.

**Ważne:** testy GitHub Actions nie zastępują próby na fizycznym Androidzie.

## Kolejny etap

Rozszerzyć model terminu o godzinę i wiarygodną zajętość wynikającą z innych zadań/wydarzeń; oddzielnie dopracować własne godziny zmian i rotację grafiku. Utrzymać obowiązek zatwierdzenia zapisu przez użytkownika.
