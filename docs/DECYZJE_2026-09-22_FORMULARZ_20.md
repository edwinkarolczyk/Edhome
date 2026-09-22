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
| 4 | A: tablet przypisany na stałe do domownika/profilu „Wspólny”; prywatny profil Edwina otwierany na jego telefonie. | Jeden APK, ale urządzenie współdzielone nie przełącza się na prywatny profil w zwykłym użyciu; konkretna autoryzacja/administracja urządzeniem wymaga osobnego projektu. |
| 5 | C: dla każdej czynności użytkownik wybiera, czy ma być widoczna w podmiejscach miejsca, do którego jest przypięta. | Wyświetlać odziedziczony *ten sam rekord* i oznaczyć jego miejsce źródłowe; nie tworzyć duplikatu wykonania ani historii. |
| 12 | C: wiele otwartych opakowań, różnych rozmiarów i partii; termin ważności i przypomnienie mogą należeć do każdej partii. | Projekt partii/opakowań i liczników częściowych, dat ważności oraz powiadomień; konkretne progi przypominania są jeszcze do ustalenia. |
| 13 | C: miejsce docelowe pozycji zakupu jest opcjonalne wcześniej, a przy „Przyjmij” wybierane lub potwierdzane. | Powiązać miejsce z konkretną pozycją zakupu; nie ustawiać go na sztywno dla całego produktu. |
| 14 | A: wpisana cena oznacza cenę jednej sztuki/opakowania; EDHOME wylicza łączny koszt z ilości. | Obecne pole beta.5 nie jest jeszcze potwierdzoną realizacją tej semantyki dla wszystkich jednostek; uwzględnić ilości ułamkowe i nieznaną ilość bez przyjmowania zera. |
| 15 | B: gdy raport remanentu zawiera konflikt, wstrzymać **wszystkie korekty** do ponownego sprawdzenia zmienionych produktów. | Zachować bezkonfliktowe wyniki i historię; nie nadpisywać nowszych stanów ani nie wprowadzać częściowego zatwierdzenia mimo konfliktów. |
| 17 | A: wkłady osób do wspólnego PayCheck potwierdzane ręcznie; wspólny budżet pokazuje wkład każdej osoby i sumę, bez odczytu prywatnych kont. | Nie tworzyć automatycznego transferu z sejfu, nie dublować wpływów. |
| 18 | C: prywatne finanse na osobnych telefonach i opcjonalnie wiele profili na urządzeniu współdzielonym, z odrębnymi kluczami. | Nie udostępniać prywatnej bazy tabletowi tylko przez ukrycie UI; wieloprofilowość jest do wykonania. |
| 19 | C: powiadomienia bankowe i import wyciągów równolegle, jedna kolejka propozycji i potwierdzenie przed księgowaniem. | Idempotencja przy wpisie ręcznym, powiadomieniu i wyciągu, bez nieuprawnionego kopiowania powiadomień. |
| 20 | C: docelowo dynamiczny priorytet nadwyżek PV na podstawie dostępnych rzeczywistych pomiarów i potrzeb, z ręcznym nadpisaniem. | Najpierw odczyt i audyt sprzętu; nie oznacza akceptacji automatycznego sterowania już teraz ani wymogu profili sezonowych. |

## Otwarte decyzje — bez domyślnego wyboru

- **6:** zachowanie zaległych instancji czynności powtarzalnych.
- **8:** zakres wspólnego kalendarza i widoczność prywatnej zajętości.
- **16:** kolejność dalszych prac nad magazynem i QR (QR miejsc, operacje skanera, wypożyczenia) — użytkownik nie wybrał priorytetu.
- **4 — szczegóły wdrożenia:** domownik „Wspólny” na stałe przypisany do tabletu jest zatwierdzony, ale administracja tabletem, dodawanie osób i procedura odzyskania dostępu wymagają projektu; nie interpretować odpowiedzi A jako pozwolenia na ujawnienie prywatnych danych.
- **12 — szczegóły wdrożenia:** wiele otwartych opakowań i daty ważności partii są zatwierdzone; dokładne wyprzedzenie i kanał powiadomień wymagają konfiguracji.
- **20 — szczegóły bezpieczeństwa:** reguły dynamicznego PV z ręcznym nadpisaniem są celem produktu; fizyczne polecenia wymagają audytu urządzeń i odrębnej akceptacji uruchomienia automatyki.

## Porównanie z aktualnym kodem i wydaniem — 22.09.2026

> **Doprecyzowanie po drugiej turze 11 odpowiedzi:** osiem decyzji (4, 5, 12, 13, 14, 15, 17, 20) jest zatwierdzonych powyżej; trzy (6, 8, 16) są otwarte. Akceptacja nie oznacza jeszcze implementacji.

- Manifest potwierdza wydaną **0.5.0-beta.5 / versionCode 48**; w kodzie i kopii danych **SQLite v22**, cena opcjonalna przy „Kupione” i historia cen produktu. Nie opisywać tego jako wyłącznie planu.
- Beta ma edytowalny katalog kafelków; brak potwierdzenia zgodności obu czasów przytrzymania i progu w ustawieniach, automatycznej adaptacji liczby kolumn oraz oddzielnego układu profilu „Wspólny”.
- W kodzie jest jeden lokalny szyfrowany prywatny sejf PayCheck oraz osobny zaszyfrowany eksport; **wiele odrębnych prywatnych profili i docelowa polityka współdzielonego tabletu nie są potwierdzone**.
- Nie uznawać 5-sekundowego automatycznego odjęcia ani przełączania po innym kodzie za zrobione tylko dlatego, że istnieje seryjny skaner z potwierdzeniami.
- Open Facts i lokalna kartoteka istnieją, natomiast dostępność katalogu na innych urządzeniach wymaga przyszłej synchronizacji Wi-Fi.
- Cena historyczna przy „Kupione” jest już wydana, natomiast **nowa decyzja 14** wymaga czytelnej ceny za jedną sztukę/opakowanie i wyliczenia łącznego kosztu z ilości; sprawdzić obecne zachowanie bez twierdzenia, że jest gotowe. Nie zmienia automatycznie stanu spiżarni ani PayCheck.
- Starsze zapisy wymagające jednego sztywnego menu kafelka, dziewięciu kafelków na stałe lub obowiązkowego ręcznego zatwierdzania każdego wyjęcia są historyczne i nie zastępują nowszych decyzji.

## Zasady następnych wydań

1. Tylko pytania 6, 8 i 16 pozostają bez rozstrzygnięcia w tym formularzu; wrócić do nich na przykładach. Nie blokować niezależnych, już zatwierdzonych poprawek.
2. Wprowadzać funkcje wspólnym ID i historią między modułami; odróżniać zdarzenie zakupu, przyjęcia do zapasu i płatności.
3. Każde wydanie: kod na `beta`, migracja i kopia, test CI, manifest/APK i odrębny odbiór na telefonie. Historyczne wiersze roadmapy nie są dowodem wydania.
4. `main` Stable: absolutnie bez zmian, aktualizacji, merge i podmiany APK bez **każdorazowej osobnej, wyraźnej akceptacji Edwina**.
