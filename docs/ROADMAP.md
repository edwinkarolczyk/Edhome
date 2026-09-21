# EDHOME — roadmapa (propozycja)

> **Stan 0.3.2-beta.2 (`beta`):** indywidualna godzina i wyprzedzenie przypomnienia (0/1/2/3/7 dni), cisza 22:00–07:00 i opt-in. Starsze zadania nadal mają przypomnienie około 09:00. SQLite v10, kopie v2–v10. Powiadomień nie gwarantuje się co do minuty (Android/Doze). Beta bez PIN-u, Stable z PIN-em. Test na telefonie odrębnym kryterium odbioru. Źródło wymagań: [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md).

Wersje są **planem**, nie wydaniami. Każdy etap przechodzi przez `beta` i testy, a dopiero potem może trafić do `main`.

| Etap | Zakres | Warunek odbioru |
|---|---|---|
| 0.0 — projekt | Zarys, model obiektów i relacji, model prywatności, źródła danych, stos Android i kanały wydań | Udokumentowane decyzje i testowalne kontrakty. |
| 0.1.0 | Uruchamialny Android, 3 motywy, nawigacja, profil/PIN, lokalna baza, edytowalny panel telefonu/tabletu, eksport | Start offline; brak utraty konfiguracji po restarcie. |
| 0.2.0 | Kalendarz pełnoekranowy, samodzielne czynności, grafik pracy, propozycje 3 terminów, powtarzanie i sezony | Jeden obiekt zadania w wielu widokach; przewijanie i klawiatura bez blokad. |
| 0.3.0 | Obowiązki, śmieci, zakupy, minutniki, lokalne powiadomienia | Wznowienie/restart; godziny ciszy i potwierdzanie zadań. |
| 0.4.0 | Magazyn, pudełka, miejsca, QR dla narzędzi, wypożyczenia, historia | Przeniesienie pudełka nie psuje QR ani lokalizacji zawartości. |
| 0.5.0 | PayCheck: prywatne/wspólne, bankowe powiadomienia i import wyciągów, cele finansowe | Brak podwójnego księgowania; brak prywatnych danych na tablecie. |
| 0.6.0 | Pojazdy, OC, serwis, opony, remonty i dokumenty | Relacje OC ↔ cel PayCheck ↔ kalendarz bez duplikacji. |
| 0.7.0 | Ogród i baza offline, PV/energia, adapter SUPLA | Legalnie pozyskane dane; brak wymogu internetu w podstawowych funkcjach. |
| 0.8.0 | Synchronizacja domowników Wi-Fi, konflikty, zgodność wersji | Dwa urządzenia nie zawieszają się, duplikaty zmian odrzucane. |
| 0.9.0 | Widgety, optymalizacja, testy regresji, proces wydań | Testy przechodzą i migracje sprawdzone na kopiach. |
| 1.0.0 | Stabilizacja, polityka prywatności, zgody, zasoby sklepu, wydanie | Gotowość do publicznej dystrybucji, nie tylko kompilacji. |

## Krytyczne reguły wydawania

- Nie uruchamiać testowania migracji na jedynej kopii prawdziwych finansów.
- Android package ID, podpisy, `versionCode`, schemat bazy i protokół synchronizacji planować przed pierwszą publikacją.
- `stable`: informacja o aktualizacji po uruchomieniu, szczegóły zmian i możliwość odroczenia; używać mechanizmu Google Play dla instalacji sklepowej.
- `beta DEV`: okresowe sprawdzenie **metadanych**, nie pobieranie APK co kilkanaście sekund; pobranie tylko zatwierdzonego artefaktu, walidacja integralności/podpisu; instalacja może wymagać działania użytkownika.
- CI powinno obejmować testy jednostkowe relacji, czasu, księgowania, migracji i synchronizacji, a później testy UI i fizycznego urządzenia.

## Otwarte decyzje

- Rzeczywiste repozytorium to **edwinkarolczyk/Edhome**, publiczne; marka aplikacji **EDHOME**. Wcześniejsze `Edhime` jest nieaktualnym zapisem historycznym.
- Stos Android i silnik lokalnego rozpoznawania mowy.
- Format i źródła legalnie dostępnej bazy ogrodniczej.
- Konkretny bank, treści powiadomień, zakres dostępu Android Notification Listener i formaty wyciągów.
- Lista urządzeń SUPLA i możliwości odczytu po LAN.
- Docelowy model rozwiązywania konfliktów synchronizacji i kluczy prywatnych.

## Korekta: skaner tabletu i spiżarnia

- **0.1.0:** konfigurowalny panel tabletu z widocznym miejscem na kafelek skanera (bez udawania działającego skanowania).
- **0.3.0:** podstawowe wspólne zakupy i magazyn spiżarni z opcjonalną ilością; dane działają offline.
- **0.4.0:** działający skaner kamery, kod kreskowy produktu i QR rzeczy/pudełka/miejsca; operacje „Dodaj / Wyciągnij / Przenieś” z potwierdzeniem; testy nieznanych kodów, duplikatów i historii.
- **0.8.0:** synchronizacja operacji tabletu i telefonów przez Wi-Fi, bez podwójnych pobrań.

[Pełna specyfikacja ustaleń](SPECYFIKACJA_CALOSC.md) pozostaje źródłem zakresu, a niniejsza roadmapa — kolejności dostarczania.

## Kreator remanentu — nowe ustalenie 19.09.2026

- **0.2.0:** uniwersalny silnik czynności cyklicznych do przypominania o remanencie (co tydzień/miesiąc/N lub termin ręczny); nie rozpoczynać sesji samowolnie.
- **0.3.0:** kartoteka produktów spiżarni z opcjonalnym stanem, jednostką i przypisaniem do miejsca.
- **0.4.0:** tabletowy kreator przeglądający produkty po kolei: „Zgadza się / Dalej”, „Podaj faktyczną liczbę”, „Brak”, „Pomiń”, przerwij/wznów, skanuj poza kolejnością, raport różnic i zatwierdzenie korekt jako osobny krok, historia remanentów.
- **0.8.0:** odporność na równoległe zmiany stanu przy remanencie i synchronizacji po Wi-Fi: porównywanie ze stanem sesji, bez dublowania korekt.

**Zakres i szczegółowy scenariusz:** [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md#11-nowe-ustalenie--kreator-okresowego-remanentu-spiżarni-na-tablecie).

## Stan faktyczny — 19.09.2026

- **0.1.0-beta.1:** skompilowana pierwsza lokalna aplikacja testowa Android z PIN, motywami, prostą listą czynności, testową spiżarnią i kopiowalnymi logami (potwierdzony test ręczny użytkownika w logu, bez błędów w podstawowych operacjach).
- **0.1.1-beta.1:** na `beta` podpięte dwa różne zasoby ikon Stable/Beta; dodano pierwszy **prototyp** ręcznego kreatora remanentu ze snapshotem, zachowaniem postępu, raportem, korektą po zatwierdzeniu i migracją SQLite v1→v2. Cykliczne przypomnienia, skaner aparatu, jednostki kg/l i automatyczna synchronizacja nadal są **niezrealizowane**.
- [Opis i test ręczny wersji 0.1.1](BETA_0_1_1.md). Status builda sprawdza się w GitHub Actions, nie należy przyjmować ukończonej aplikacji na podstawie samego zapisu w roadmapie.

## Aktualizacje — stan historyczny 0.1.2-beta.1 (kanał zaktualizowano w 0.2.5)

- Kod klienta **Beta DEV**: aktywne sprawdzanie małego manifestu HTTPS co 30 s po konfiguracji adresu, automatyczne pobranie nowej wersji, SHA-256, identyfikator pakietu/podpis i zgoda systemowa na instalację; możliwość ręcznego wskazania lokalnego APK.
- Kod klienta **Stable**: integracja Google Play In-App Updates, sprawdzenie raz po odblokowaniu aplikacji, „Aktualizuj / Później”, możliwość otwarcia szczegółów Play. Stable nieopublikowana — brak testu prawdziwej aktualizacji sklepowej.
- **Historycznie niezakończona infrastruktura:** przed 0.2.5 nie było publicznego manifestu i APK. Od 0.2.5 podpisane APK są publikowane w GitHub Releases publicznego repo, a manifest na gałęzi `beta`; nie należy wracać do ręcznej konfiguracji URL/tokenów.
- **Przed rzeczywistą migracją** zadbać o trwały podpis, backup/eksport i regresję aktualizacji na telefonie. Warianty debug mogą mieć konflikt certyfikatu z wcześniejszymi wydaniami.

[Zasady i wymagania kanału](BETA_0_1_2.md).


## Etap wykonany w kodzie — 0.1.5-beta.1 (20.09.2026)

- Samodzielne czynności otrzymały termin, regułę powtarzania oraz osobną historię wykonania.
- Powtarzanie dzienne/tygodniowe/miesięczne/roczne, co N jednostek i sezonowe na wybrany przez użytkownika dzień przygotowania.
- Pierwszy miesięczny kalendarz z datą, liczbą czynności i listą wybranego dnia; nie jest to jeszcze pełny planer z grafikami.
- SQLite v3, migracja 1→2→3 i backup JSON v3 z importem wcześniejszego formatu v2.
- Osobna weryfikacja wydania APK w GitHub Actions; zapis kodu nie oznacza testu instalacji na fizycznym telefonie.

[Zakres i scenariusze odbioru](BETA_0_1_5.md).


## Następny inkrement — 0.3.3-beta.1

- Lokalne minutniki sprzętów (pralka, suszarka, zmywarka) z zakończeniem i prostym potwierdzeniem, oddzielone od niepewnych dokładnych powiadomień Androida.
- Rotacyjne obowiązki i dalsze ustawienia przypomnień w następnych małych wersjach; godziny ciszy 22:00–07:00 w 0.3.2 są stałe, jeszcze nie konfigurowalne.
- Nie włączać synchronizacji kilku urządzeń przed etapem 0.8. Weryfikować CI, podpis, manifest i zachowanie na fizycznym Androidzie.
