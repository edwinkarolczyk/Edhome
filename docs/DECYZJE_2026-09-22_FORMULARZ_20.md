# EDHOME — audyt 20 odpowiedzi Edwina, 22.09.2026

> Obowiązujący rejestr decyzji z obecnego audytu. Przewyższa sprzeczne starsze propozycje w `ROADMAP.md`, `SPECYFIKACJA_CALOSC.md` i formularzu 30 pytań, ale **wyłącznie w odpowiedzianych kwestiach**. „Inaczej / wyjaśnij / zapytaj jeszcze raz” pozostaje **OTWARTE**, a nie jest akceptacją A/B/C ani zgody na kod. Ten plik jest dokumentacją, nie twierdzeniem o funkcji w APK.

## Zatwierdzone, bez nadinterpretacji

| Nr | Decyzja użytkownika | Konsekwencja dla implementacji |
|---|---|---|
| 1 | A: automatyczna liczba kolumn wg szerokości ekranu, pionowe przewijanie. | Bez stałego limitu 9 kafelków; test telefonu i tabletu. |
| 2 | B: tablet początkowo kopiuje układ telefonu, następnie niezależne układy. | Kopiowanie jednorazowe w chwili utworzenia; później nie nadpisywać zmian. |
| 3 | C z doprecyzowaniem: krótsze przytrzymanie → menu; dłuższe → przeciąganie. Czas progowy ustalany w Ustawieniach. | Nie narzucać na sztywno progu, przewidzieć dostępną alternatywę dla gestu. |
| 7 | B: trzy propozycje o odmiennych priorytetach: najwcześniej, najmniejsze obciążenie, najwygodniej. | Zatwierdzenie terminu pozostaje po stronie użytkownika. |
| 9 | B: domyślne odliczanie przy wyciąganiu 5 s. | Jest ustawieniem edytowalnym przez użytkownika. |
| 10 | C: inny kod w trakcie odliczania anuluje bieżącą niezapisaną operację i przechodzi do nowego. | Nie zaksięgować anulowanej pozycji; ten sam kod w trakcie licznika ignorować. |
| 11 | B: lokalny katalog produktów rozpoznanych u użytkownika, dostępny na innych jego urządzeniach po synchronizacji. | Offline-first, legalne źródła, bez zakładania wdrożonej synchronizacji. |
| 18 | C: prywatne finanse na osobnych telefonach i opcjonalnie wiele profili na urządzeniu współdzielonym, z odrębnymi kluczami. | Nie udostępniać prywatnej bazy tabletowi tylko przez ukrycie UI; wieloprofilowość jest do wykonania. |
| 19 | C: powiadomienia bankowe i import wyciągów równolegle, jedna kolejka propozycji i potwierdzenie przed księgowaniem. | Idempotencja przy wpisie ręcznym, powiadomieniu i wyciągu, bez nieuprawnionego kopiowania powiadomień. |

## Zatwierdzony kierunek, ale szczegóły otwarte

- **4 — profil „Wspólny”:** użytkownik zaproponował stałego domownika/profil gospodarstwa „Wspólny”, do którego przypisywany jest tablet; **nie zatwierdził** jeszcze sposobu wyjścia z tego profilu, modelu administratora i zabezpieczenia przełączania.
- **12 — częściowe opakowania:** brak rozstrzygnięcia pełne/jedno/wiele otwartych. Nowy wymóg do rozważenia: daty ważności per opakowanie/partia i przypomnienia przed terminem; bez domniemania, że jest to już zaimplementowane.

## Nierozstrzygnięte — wymagają pytania ponownie NA PRZYKŁADZIE

- **5:** widoczność czynności miejsca nadrzędnego w dzieciach.
- **6:** zachowanie zaległych instancji czynności powtarzalnych.
- **8:** zakres wspólnego kalendarza i prywatna zajętość.
- **13:** moment wyboru docelowego miejsca konkretnej pozycji zakupów.
- **14:** znaczenie ceny „Kupione” (za sztukę/opakowanie czy za całą ilość).
- **15:** zatwierdzanie bezkonfliktowych wyników remanentu w obecności konfliktu.
- **16:** następny priorytet magazynu i QR.
- **17:** model wkładów osób do wspólnego budżetu.
- **20:** model priorytetów PV (stały/sezonowy/dynamiczny/hybrydowy).

## Porównanie z aktualnym kodem i wydaniem — 22.09.2026

- Manifest potwierdza wydaną **0.5.0-beta.5 / versionCode 48**; w kodzie i kopii danych **SQLite v22**, cena opcjonalna przy „Kupione” i historia cen produktu. Nie opisywać tego jako wyłącznie planu.
- Beta ma edytowalny katalog kafelków; brak potwierdzenia zgodności obu czasów przytrzymania i progu w ustawieniach, automatycznej adaptacji liczby kolumn oraz oddzielnego układu profilu „Wspólny”.
- W kodzie jest jeden lokalny szyfrowany prywatny sejf PayCheck oraz osobny zaszyfrowany eksport; **wiele odrębnych prywatnych profili i docelowa polityka współdzielonego tabletu nie są potwierdzone**.
- Nie uznawać 5-sekundowego automatycznego odjęcia ani przełączania po innym kodzie za zrobione tylko dlatego, że istnieje seryjny skaner z potwierdzeniami.
- Open Facts i lokalna kartoteka istnieją, natomiast dostępność katalogu na innych urządzeniach wymaga przyszłej synchronizacji Wi-Fi.
- Cena historyczna przy „Kupione” nie oznacza rozstrzygnięcia semantyki kwoty z pytania 14. Nie zmienia też automatycznie stanu spiżarni ani PayCheck.
- Starsze zapisy wymagające jednego sztywnego menu kafelka, dziewięciu kafelków na stałe lub obowiązkowego ręcznego zatwierdzania każdego wyjęcia są historyczne i nie zastępują nowszych decyzji.

## Zasady następnych wydań

1. Najpierw potwierdzić otwarte pytania w rozmowie na konkretnych przykładach; nie blokować niezależnych, już zatwierdzonych poprawek.
2. Wprowadzać funkcje wspólnym ID i historią między modułami; odróżniać zdarzenie zakupu, przyjęcia do zapasu i płatności.
3. Każde wydanie: kod na `beta`, migracja i kopia, test CI, manifest/APK i odrębny odbiór na telefonie. Historyczne wiersze roadmapy nie są dowodem wydania.
4. `main` Stable: absolutnie bez zmian, aktualizacji, merge i podmiany APK bez **każdorazowej osobnej, wyraźnej akceptacji Edwina**.
