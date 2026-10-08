# EDHOME — etap 6: audyt i odbiór Budżetu miesiąca 25/25

**Gałąź:** `beta`. **Stable `main`:** bez zmian bez jawnej akceptacji.
**Kontrakt:** [ROADMAP.md](ROADMAP.md#paycheck--budżet-miesiąca-10-kontrakt-odbioru-2525).
**Rozdział obowiązkowy:** pokrycie kodowe ≠ przetestowanie zachowania ≠ odbiór na telefonie i PC.
**Stan 08.10.2026:** Android Beta `0.8.0.64/256` (#2010 PASS); Desktop `0.7.0.114` ([CI #284 PASS](https://github.com/edwinkarolczyk/Edhome/actions/runs/37848629781), instalator opublikowany). Synchronizacja 5C wymaga nadal fizycznych testów A1–A20; testy etap 6 oznaczono poniżej B1–B12. Na jedynej kopii danych NIE przeprowadzać prób celowych konfliktów.

## Macierz 25 ustaleń

Legenda: **K** = wymaganie ma implementację w Beta / kontrakt źródłowy (bez gwarancji poprawności każdego scenariusza); **T** = test automatyczny istnieje dla części zachowania; **O** = odbiór ręczny wymagany. Brak oznaczenia O oznacza dopiero potwierdzenie fizyczne, nigdy zielone CI.

| # | Ustalenie | Stan obecny i ograniczenie | Próba odbiorowa |
|---|---|---|---|
| 1 | Miesiące i trwała historia | K/T; stan historyczny i zmiana planu po usunięciu do weryfikacji | B1, B12 |
| 2 | Jeden bieżący PayCheck/Budżet | K; prywatne dane pozostają zachowane, ale główny przepływ wspólny | B2 |
| 3 | Jedna zwarta linia bez kafelka | K; kontrola wyglądu i przewijania na Androidzie | B3 |
| 4 | Jedna rozwinięta pozycja | K; weryfikacja po zmianie miesiąca | B3 |
| 5 | Zielony/czerwony/żółty i plakietki typu | K; sprawdzić częściową wpłatę i ostatnią ratę | B4 |
| 6 | Opcjonalne i przeniesienie miesiąca | K/T; brak zaległości po pominięciu | B5 |
| 7 | Zaległości u góry, dalej po terminie | K; zachowanie sortowania wymaga potwierdzenia | B5 |
| 8 | Wpływy osobno u góry | K; Desktop 0.7.0.114 osobno porządkuje wpływy na początku tabeli | B2 |
| 9 | Plan nie księguje pieniędzy | K/T; regresja matematyki i potwierdzonych operacji | B6 |
| 10 | Jeden przelew → kilka pozycji | K/T; kontrola sum alokacji i zatwierdzenia | B6 |
| 11 | Jawna różnica i nadpłata/niedopłata | K/T; stan bez automatycznej korekty | B6 |
| 12 | Niedopłata jako osobna linia za poprzedni miesiąc | K; sprawdzić niezależność od bieżącego planu | B5 |
| 13 | Historia pod przyszłe statystyki | K/T; dziennik append-only, bez ekranu statystyk | B12 |
| 14 | Nadpłata odliczana tylko za zgodą | K/T; ograniczenia źródło/cel i wielokrotne miesiące; Android 0.8.0.64 | B6 |
| 15 | Niezapłacone przenosi się jako zaległość | K; utrzymanie aż do rozliczenia lub zamknięcia | B5 |
| 16 | Ręczne zamknięcie z powodem i datą | K; odbiór zapisu zdarzenia i korekty | B7 |
| 17 | Zakres zamknięcia cyklu | K; tylko miesiąc / cały cykl / nadpłata | B7 |
| 18 | Zmiana kwoty tylko miesiąc / od teraz | K; Desktop edytuje kwotę konkretnego miesiąca | B7 |
| 19 | Raty liczba/koniec, automatyczne wyliczenie | K; wymagane sprawdzenie obu kierunków | B8 |
| 20 | Postęp rat, sumy, ostrzeżenia | K; pozostało i ostatnie dwie raty | B8 |
| 21 | Odbiorca różny od zobowiązania | K; Desktop 0.7.0.114 wyświetla wiele pozycji pod jednym odbiorcą | B9 |
| 22 | Trwała kartoteka odbiorców/szablony | K; Desktop wyświetla kartotekę, jej edycja pozostaje funkcją Androida | B9 |
| 23 | Faktura i osobna planowana data | K/T; wcześniejsza data: 10. lub termin faktury | B10 |
| 24 | Przypomnienia 3 dni przed i w dzień | K; sprawdzić anulowanie po zapłacie i godziny telefonu | B11 |
| 25 | Zdjęcie/PDF faktury schowane w szczegółach | K; ZIP/restore i FileProvider do potwierdzenia | B12 |

**Ocena:** w kodzie Beta istnieje pokrycie 25 punktów według kontraktu źródłowego i wcześniejszych regresji. Nie wolno zapisywać „odebrane 25/25” do czasu ukończenia odpowiednich prób B i A1–A20. Szczególnie wymagają uwagi Android UX, kopie załączników i konflikty zmian offline.

## Scenariusze etapu 6

| Test | Co sprawdzić na kopii danych | Stan |
|---|---|---|
| B1 | Zmień miesiąc w obie strony, wróć do bieżącego; stary okres i korekty nie znikają | ☐ |
| B2 | Jedna księga; wpływy osobno na górze, jeden zwarty nagłówek | ☐ |
| B3 | Rozwiń pozycję A, potem B; poprzednia zwinięta, przewinięcie się nie resetuje | ☐ |
| B4 | Opłacone/zaległe/częściowo/opcjonalne i plakietki rat mają właściwe kolory | ☐ |
| B5 | Zaległość poprzedniego miesiąca na górze, opcjonalnego brak w zaległościach | ☐ |
| B6 | Przelew grupowy, nadpłata i zgoda na odliczenie; brak podwójnego księgowania | ☐ |
| B7 | Ręczne zamknięcie z powodem, zakres cyklu i zmiana kwoty miesiąc/od teraz | ☐ |
| B8 | Raty: liczba → koniec i koniec → liczba, „zostało”, suma i ostatnie raty | ☐ |
| B9 | Jeden odbiorca, dwa zobowiązania, podpowiedź szablonu; PC pokazuje tę kartotekę | ☐ |
| B10 | Faktura przed 10. i po 10.; planowana data nigdy po terminie | ☐ |
| B11 | Testowe przypomnienie przed i w dzień płatności; po zapłacie nie wraca | ☐ |
| B12 | Dodaj PDF/zdjęcie, zrób ZIP, przywróć na kopii, sprawdź historię i załączniki | ☐ |

## Kontrola wersji / zależności

- Desktop 0.7.0.114: zwarty nagłówek z bilansem, nawigacja do bieżącego miesiąca, wpływy przed wydatkami, sortowanie wydatków datą, podgląd odbiorców i liczby zobowiązań. Testy `DesktopBudgetMirrorTest` i `check_paycheck_budget_stage6_desktop.py`; build #284 **PASS**, Windows Beta opublikowany.
- Android 0.8.0.64: zabezpieczenie nadpłat między miesiącami, CI #2010 PASS; dalsze zmiany wyłącznie na `beta`.
- 5C: [ODBIOR_PAYCHECK_5C_2026-10-08.md](ODBIOR_PAYCHECK_5C_2026-10-08.md), A1–A20 nadal niewykonane fizycznie.
- Nie przenosić na Stable, nie usuwać poprzednich wpisów historii i nie zastępować kopii prawdziwych danych plikami testowymi.
