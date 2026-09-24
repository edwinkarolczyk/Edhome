> **Aktualizacja stanu, 24.09.2026:** linia 0.6.0 doszła do 0.6.0.9 / SQLite v32 z kolejką PayCheck „Do potwierdzenia” i ręcznym uzgadnianiem importowanego CSV. 0.6.0.10 / SQLite v33 dodaje lokalny rejestr dokumentów pojazdu. Nadal otwarte w 0.6: powiadomienia bankowe po wyborze banku i nadaniu uprawnień, załączniki dokumentów oraz stabilizacja/odbiór na telefonie. Dopiero później 0.7 Ogród/Energia, 0.8 synchronizacja, 0.9 regresje i 1.0 Stable po akceptacji. Starsze datowane nagłówki roadmapy są historią, nie stanem obecnego APK.

> **Druga tura odpowiedzi Edwina, 22.09.2026:** [obowiązujący audyt 20 pytań](DECYZJE_2026-09-22_FORMULARZ_20.md) zawiera zatwierdzone decyzje 4/5/12/13/14/15/17/20. **Tylko 6/8/16 wciąż pozostają otwarte** w tym formularzu. Tablet jest przypisany na stałe do domownika „Wspólny” (prywatny profil na telefonie), widoczność czynności z nadrzędnego miejsca ustawiana **per czynność**, wiele otwartych opakowań/partii z datami ważności; miejsce zakupów opcjonalne do przyjęcia; cena to **za jedną sztukę/opakowanie**; konflikt remanentu blokuje **wszystkie korekty**; wkłady do wspólnego PayCheck ręczne i jawne per osoba, bez prywatnych ksiąg; PV dynamiczne z ręcznym nadpisaniem, fizyczne sterowanie dopiero po audycie. To decyzje projektowe, nie funkcje wdrożone w APK.

> **Ostatnia potwierdzona publikacja przed pracami nad adaptacją panelu: `0.5.0-beta.5` / versionCode 48 / SQLite v22.** Wydania `.4` i `.5` oraz CI są potwierdzone; pozostałe wiersze planu poniżej to propozycje, nie funkcje już w APK. Zobacz [odbiór serii 0.5.0](BETA_0_5_0.md).

> **Stan nowszy od historycznego nagłówka niżej:** wydania 0.4.0-beta.11/12/13 obejmują przyjęcia zakupów, pudełka, QR i ochronę miejsc. Pierwszy PayCheck tylko dla wspólnego budżetu zaczyna się w 0.5.0-beta.1 (SQLite v20); prywatne dane nie są dostępne w Becie bez ochrony. Datowane fragmenty niżej są historią planu, nie bieżącą wersją APK.

# EDHOME — roadmapa (propozycja)

> **Stan 0.3.7-beta.1 (`beta`):** rotacyjne obowiązki, konfigurowalne godziny ciszy dla czynności i minutników oraz bardziej widoczny tryb układania kafelków. SQLite v13, kopie v2–v13. Beta bez PIN-u, Stable z PIN-em. [Odbiór 0.3.7](BETA_0_3_7.md).

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


## Zrealizowany inkrement — 0.3.7-beta.1

- Konfigurowalne godziny ciszy dla czynności i minutników, eksport/import JSON.
- Wyraźne wejście w tryb układania; przytrzymanie w zwykłym trybie pozostaje menu Edytuj / Przesuń. Odbiór gestów na telefonie pozostaje do wykonania.
- Nie włączać synchronizacji kilku urządzeń przed etapem 0.8. Weryfikować CI, podpis, manifest i zachowanie na fizycznym Androidzie.


## Energia — panel nadwyżek PV i priorytety odbiorników (zakres docelowy)

- **Etap Energia / pierwszy inkrement:** ekran bilansu: PV, zużycie, import, eksport, dostępna nadwyżka, CWU/bufor i pomiary z podaniem źródła oraz czasu ostatniej aktualizacji. Nie wypełniać braków fikcyjnymi wartościami.
- **Następny inkrement:** edytowalna kolejka priorytetów uprawnionych odbiorników, wyjaśnienie propozycji, scenariusze/symulacja bez wysyłania poleceń, historia zmian, ręczne zatwierdzanie.
- **Po audycie sprzętu i bezpieczeństwa:** lokalny adapter SUPLA/falownik/licznik oraz sterownik, który może wykonywać jawnie dopuszczone automatyzacje w granicach mocy i temperatury; tryb awaryjny oraz ręczny override. Automatyka nie może zależeć od otwartej aplikacji w telefonie.
- **Testy odbiorowe:** bilans kW vs kWh, świeżość telemetrii, brak pomiaru, spadek PV, import sieciowy, utrata LAN, histereza, priorytety, maksymalna moc/temperatura, brak podwójnego księgowania w PayCheck. Limit eksportu nie zastępuje zgłoszeń do operatora.

Szczegóły wymagań: [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md#21-edhome--panel-zarządzania-energią-i-priorytetami-nadwyżek-pv-uzgodnienie). **To jest zakres planowany, nie funkcja już dostępna w APK.**

## Integracja SUPLA — kamienie milowe

- **Odkrywanie:** po wyborze zgodnej instancji Cloud API i bezpiecznym OAuth2 pobrać listę uprawnionych kanałów; nie testować endpointów zapisu na realnym koncie.
- **Odczyt:** stany i pomiary ze źródłem, jednostką, znacznikiem czasu i mapowaniem do obiektów EDHOME.
- **MQTT (opcjonalnie):** odbiór zmian chmurowego brokera SUPLA przez TLS; to nie jest automatycznie lokalny/offline broker. Konfiguracja sekretów poza repo i logami.
- **Offline:** osobny adapter LAN wyłącznie po potwierdzeniu protokołu konkretnego urządzenia; przy utracie połączenia oznaczać dane jako nieaktualne.
- **Sterowanie:** dopiero po sprawdzeniu uprawnień, parametrów urządzeń i zabezpieczeń; w pierwszym wydaniu tylko odczyt.
- Kryteria: brak tokenów w APK/logach/commicie, poprawna obsługa odwołania dostępu, różnych instancji SUPLA i niekompletnej telemetrii.

Zob. [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md#22-integracja-supla--cloud-api-oauth2-i-broker-mqtt-doprecyzowanie-na-podstawie-zrzutów-21092026). Ta sekcja to roadmapa, nie potwierdzenie wykonania.


## Aktualizacja roadmapy po 30 odpowiedziach Edwina — 21.09.2026

**Źródło decyzji:** [DECYZJE_2026-09-21_FORMULARZ_30.md](DECYZJE_2026-09-21_FORMULARZ_30.md). Ten plan ustala **kolejność i kryteria**, nie deklaruje ukończenia. Starsze etapy roadmapy mogą przedstawiać historyczny zakres i nie zastępują późniejszych decyzji użytkownika.

| Kolejność | Obszar / rezultat | Kryterium odbioru |
|---|---|---|
| 1 | Domknąć bieżącą betę 0.3.4 (SQLite v11, aktualizacje i testy użytkowe) | CI zielone; migracje danych i test telefonu/tabletu; odróżnić wydaną wersję od nieopublikowanego kodu. |
| 2 | UI, panel i Miejsca | Sześć motywów; **wszystkie wejścia w katalogu kafelków, bez limitu 9**; dodawanie/ukrywanie/usuwanie samych skrótów, pełna edycja każdego (cel, nazwa, ikona, kolor, rozmiar, pozycja), przeciąganie całego kafelka; także Minutniki, Zakupy, PayCheck, Odpady i wszystkie Czynności. Tryb tabletu w **tym samym APK** z kontrolą uprawnień; własne Miejsca i pudełka bez cykli. |
| 3 | Czynności ↔ Kalendarz ↔ Miejsca | Dwa formularze czynności, trzy rodzaje daty sezonowej, reguła terminu per czynność, wspólne ID i jeden stan widoczny w wielu ekranach; planer łączy grafik, dostępność, czas i obciążenie. |
| 4 | Spiżarnia ↔ Zakupy ↔ Skaner | Dwa tryby skanowania. Dodawanie seryjne + zgodna licencyjnie baza produktów; wyciąganie domyślnie -1 po ustawianym odliczaniu, powtórny odczyt ignorowany, zmiana liczby restartuje czas; „Do dodania do [miejsce]” z lokalizacją per pozycja zakupów; historia i testy podwójnego skanu. |
| 5 | Remanent i zabezpieczenie danych | Konflikt ponownej weryfikacji zmienionych pozycji bez utraty reszty; kopie JSON, lokalne i NAS; testy migracji i odtwarzania. |
| 6 | **PayCheck — pierwszy duży nowy moduł** | Prywatne profile + wspólny budżet pokazujący wkłady, transakcja zatwierdzana przez użytkownika, powiązania z rzeczywistymi obiektami/kalendarzem bez podwójnego księgowania. |
| 7 | Współpraca telefon–tablet i synchronizacja | Zgodność danych i uprawnień; ustalić dokładne operacje wymagające połączenia (wskazane: transfer rzeczy między urządzeniami, sterowanie SUPLA/energią, rozliczenia wspólnego budżetu). |
| 8 | SUPLA → Energia | Najpierw rzeczywiste urządzenia i stany; potem pomiary i priorytety; sterowanie/automatyka dopiero po audycie; model profili PV **otwarty (pytanie 28)**. |
| 9 | Pojazdy, ogród i dalsze moduły | Korzystają z istniejących obiektów/czynności/kalendarza/magazynu/finansów, bez osobnych kopii faktów. |

**Prace przekrojowe przy KAŻDYM etapie:** wspólne ID i relacje, jeden właściciel danych, migracja/backup, prywatność, log Beta, CI i regresja. Bez zamrażania rozwoju na samą architekturę.

**Nierozstrzygnięte:** pyt. 13 (dziedziczenie czynności miejsca), 28 (model PV); szczegóły trybu tabletu, czas domyślny odliczania, zachowanie innego kodu w trakcie licznika i semantyka wymaganych połączeń. Nie wybierać ich za użytkownika.


## Spiżarnia — historia kosztów zakupów i cen (koncepcja, 21.09.2026)

**Status:** trzy decyzje Edwina zatwierdzone 21.09.2026; koncepcja niezaimplementowana. Szczegółowy model i pytania: [specyfikacja §24](SPECYFIKACJA_CALOSC.md#24-spiżarnia--koszt-produktu-i-historia-cen-propozycja-koncepcyjna-21092026). Zachować priorytet **PayCheck jako pierwszy duży nowy moduł** z decyzji 30 odpowiedzi; kosztów spiżarni nie robić drugą, konkurencyjną księgowością.

| Kolejność względem obecnego 0.3.6 | Zakres koncepcyjny | Kryterium |
|---|---|---|
| Fundament kartoteki / Miejsca / zakupy / skaner (obecne etapy 0.3→0.4) | Przygotować ID produktu i wariantu opakowania, jednostki oraz rozdzielenie zakupu, przyjęcia i ruchu magazynowego; nie wymuszać ceny przy skanie. | „Kupione” nie dodaje zapasu, „Wyciągnij” nie tworzy wydatku, brak ceny != 0 zł; historia nie ginie przy zmianie nazwy produktu/miejsca. |
| Pierwszy inkrement cen przy spiżarni (po stabilizacji podstawowych operacji) | **Opcjonalna cena przy oznaczaniu pozycji jako „kupione”**, ilość, data i sklep; historia cen i ostatnia cena na karcie; link do listy zakupów; **wszystkie ceny produktów widoczne na wspólnym tablecie bez danych kont**. | Ten sam produkt w kilku zakupach ma pełną historię; szybki skaner działa bez formularza ceny; dane PayCheck i metody płatności nie trafiają do wspólnego cache. |
| **PayCheck — nadal pierwszy duży kolejny moduł**, zgodnie z ustaleniami | Jeden paragon → wiele pozycji, jedna powiązana transakcja finansowa; uzgodnienie danych ręcznych, powiadomienia i wyciągu; osobiste i wspólne uprawnienia. | Brak drugiego zaksięgowania zakupów lub wyjęcia; na tablecie nie ma prywatnych danych finansowych. |
| Rozszerzenie raportów spiżarni (**późniejszy etap — zatwierdzone**) | Ceny za kg/l, rabaty, porównania w czasie, zwroty, częściowe opakowania, **dopiero tu oznaczona jako szacunkowa wartość całego zapasu**, na podstawie jawnej metody i jakości danych. | Partie bez ceny i nieporównywalne jednostki nie stają się pozorną precyzyjną kwotą; raport nie jest saldem PayCheck. |
| Etap Wi-Fi / 0.8 | Przesyłanie zakupów i ruchów po uprawnieniach, idempotencja przyjęć, spójne korekty ceny i remanentu. | Bez dubli i nadpisania historii po pracy offline na kilku urządzeniach. |

**Decyzje Edwina 21.09.2026:** cena opcjonalna podczas oznaczania „kupione”; wszystkie ceny produktów widoczne na wspólnym tablecie bez danych kont/księgowości prywatnej; wartość całego zapasu dopiero w późniejszym etapie. **Otwarte:** szczegółowa metoda późniejszej wyceny stanu i zachowanie przy niepełnych danych. Nie zmieniać kodu, APK ani `main` w tym wątku bez wyraźnego osobnego polecenia.


## EDHOME na komputerze — plan architektury (bez deklaracji działającej wersji PC)

- **Interfejs:** responsywny panel webowy otwierany w przeglądarce Windows / Linux / macOS; docelowo opcjonalny instalator Windows/EXE jako opakowanie panelu. Na dużym ekranie: lewy panel modułów, centrum z kalendarzem i listami, prawa karta szczegółów, przeciąganie myszą/touch.
- **Wspólna semantyka:** te same trwałe ID gospodarstwa, osoby, miejsca i czynności, wspólne zasady historii, terminów i uprawnień. Android pozostaje natywną aplikacją offline; nie przenosić 1:1 widoku 3×3 na monitor.
- **Etap przed synchronizacją:** można prototypować panel PC na testowych danych albo imporcie osobnej kopii JSON. Nie prezentować tego jako danych na żywo z telefonu.
- **Etap 0.8:** lokalny EDHOME Hub na zaufanym komputerze/NAS jako punkt wymiany po domowym Wi-Fi; Android zachowuje SQLite offline, PC używa API Huba. Identyfikatory zmian, wersje schematu, deduplikacja i rozwiązywanie konfliktów, a nie wspólny plik SQLite otwarty przez kilka urządzeń.
- **Prywatność:** logowanie/sesja i role domowników, finansów prywatnych nie udostępniać z automatu na wspólnym PC/tablecie. Domyślnie bez publicznego wystawiania Huba do internetu.

Wdrożenie PC nie należy do podpisanego APK 0.3.7 i nie wymusza modyfikacji `main`.


## Domowy dysk / NAS — EDHOME Hub i API (warunkowy kierunek)

**Nie jest to działająca integracja. Edwin potwierdził model D-Link DNS-320L; zalecany wariant B — NAS na kopie, EDHOME Hub na osobnym hoście. Nie zweryfikowano firmware, stanu dysków ani bezpiecznych protokołów.** [Specyfikacja §25](SPECYFIKACJA_CALOSC.md#25-własny-dysk-sieciowy--nas-jako-edhome-hub-i-lokalne-api--analiza-warunkowa-21092026).

| Kiedy | Rezultat | Warunek odbioru |
|---|---|---|
| Teraz — projekt / bez zmian kodu | **Model: D-Link DNS-320L.** Zweryfikować firmware, stan dysków, protokoły SMB i aktualne możliwości urządzenia; nie zakładać Dockera ani nowoczesnych pakietów API. | Potwierdzenie z panelu/testu sieci; żadnych haseł, adresów i numerów seryjnych w publicznym repo. |
| Fundament danych równolegle z modułami | Wspólne ID, wersje, kolejka zdarzeń, uprawnienia i eksport; API może działać na NAS **albo** na oddzielnym mini-PC, z NAS jako magazynem backupów. | Offline działa bez Huba, brak współdzielonego pliku SQLite po SMB, brak prywatnego PayCheck w cache tabletu. |
| Etap 0.8 — synchronizacja LAN / Hub | Lokalny proces API, autoryzacja osób i urządzeń, przyjęcia/wyjęcia, zakupy, ceny, remanent, konflikty i idempotencja; później panel PC. | Dwa urządzenia po pracy offline nie nadpisują zmian, nie dublują kosztów i mogą wznowić synchronizację. |
| Utwardzenie i eksploatacja | Backup wersjonowany na osobny nośnik, test przywrócenia, migracje, aktualizacja usługi, zasilanie oraz opcjonalny VPN do dostępu zdalnego. | Awaria NAS/Huba nie kasuje jedynych danych; API/SMB nie są otwarte wprost do internetu. |

**Dla wskazanego D-Link DNS-320L rekomendowany wariant B:** NAS wyłącznie na backupy/eksporty/załączniki po weryfikacji bezpieczeństwa, API na mini-PC lub innym zgodnym i aktualizowanym urządzeniu. Alternatywne uruchamianie niestandardowego oprogramowania na NAS pozostaje eksperymentem, nie bazową architekturą. Nie polegać na starym SMB1 ani jednym nośniku backupu. W tym czacie jedynie teoria i dokumentacja na `beta`; nie zmieniać APK, kodu ani `main`.


## Korekta panelu po zrzutach 0.5.0-beta.2 — **kafelki bez limitu i pełny edytor** (22.09.2026)

**To zatwierdzone wymaganie, nie deklaracja wykonania.** Na pokazanym ekranie widać dziewięć głównych kafelków, natomiast Minutniki urządzeń, Lista zakupów, PayCheck — wspólny budżet, Odpady, wszystkie Czynności i Diagnostyka Beta są odrębnymi przyciskami poza siatką. **Tak ma nie pozostać.** Zmieniono nadrzędny rejestr decyzji i §26 specyfikacji; historyczny opis 0.3.3-beta.2 z zakazem zmiany celu skrótu już nie obowiązuje.

| Etap prac (nie przypisywać numeru wydania bez potwierdzenia planu gałęzi kodu) | Rezultat | Test odbiorowy |
|---|---|---|
| **Najbliższy inkrement panelu — obok dalszego PayCheck, a nie zamiast niego** | Katalog celów wszystkich modułów oraz skrótów do podwidoków (Minutniki, Zakupy, PayCheck wspólny/osobisty po uprawnieniach, Odpady, Czynności, Na dziś, Diagnostyka Beta itd.). Usunięcie stałego dolnego menu skrótów po przeniesieniu wejść do katalogu. | Każdą z funkcji obecnie występujących pod kartą „Najbliższe czynności” można dodać jako kafelek i otworzyć z niego; brak utraty istniejących funkcji. |
| **Pełny edytor każdego kafelka** | Konfigurowalne: cel moduł/widok/dozwolony obiekt, własna nazwa, ikona z biblioteki, kolor, rozmiar (mały/podwójny), pozycja i widoczność; kilka skrótów do tego samego celu; menu przytrzymania Edytuj / Przesuń / Ukryj/Usuń skrót, pełne przeciąganie. | 15+ kafelków w przewijanej/responsywnej siatce; zmiana celu i pozostałych pól dla **domyślnego i nowego** kafelka, swobodna kolejność; żaden skrót nie kasuje danych modułu. |
| **Trwałość, tryby, bezpieczeństwo** | Konfiguracja w bazie i JSON, migracja bez resetu, odrębny układ telefonu i współdzielonego tabletu, ponowna kontrola uprawnień docelowego ekranu. | Restart/import zachowuje cały układ; brak możliwości otwarcia prywatnego PayCheck z tabletu przez zmianę celu; Diagnostyka tylko Beta. |

**3×3 to przykład widocznego fragmentu siatki na telefonie, nie ograniczenie danych ani stała lista systemowych modułów.** Szczegóły: [specyfikacja §26](SPECYFIKACJA_CALOSC.md#26-panel-główny--w-pełni-edytowalne-kafelki-bez-limitu-dziewięciu-doprecyzowanie-edwina-22092026). W tym czacie **tylko dokumentacja na `beta`**, bez zmian kodu, APK i `main`.


## Historyczna propozycja od 0.5.0-beta.3 do 1.0.0 — datowany plan przed wydaniem .4 i .5

**UWAGA: tabela powstała przy `.3`.** Wydania `.4` (pełny panel kafelków i szyfrowana kopia PayCheck) oraz `.5` (historia cen) są już opublikowane, **nie zgadzają się z pierwotną kolejnością numerów tej tabeli**. Za obecny punkt odniesienia przyjmij sekcję „Stan wykonania serii 0.5” poniżej. Pozostałe numery to pomysły na inkrementy, a nie daty ani potwierdzone wydania. Kolejne numery można skorygować po teście i zmianie rozmiaru pracy. Żaden numer nie oznacza automatycznej promocji na `main`. W tym czacie zmieniana jest wyłącznie dokumentacja `beta`.

### Najbliższa seria 0.5.0 — równoległe domknięcie panelu i bezpiecznego PayCheck

| Proponowane wydanie | Zmiana produktu | Minimalny test przed następną wersją |
|---|---|---|
| **0.5.0-beta.4** | **Katalog wszystkich celów kafelków**, również Minutniki, Zakupy, PayCheck (wspólny i prywatny tylko po uprawnieniach), Odpady, Wszystkie Czynności i Diagnostyka Beta; dodawanie bez limitu dziewięciu, brak stałego drugiego menu jako jedynej drogi do funkcji. | Dodaj minimum 15 skrótów, przewiń, otwórz każdy cel; nie ukrywaj funkcji podczas migracji panelu. |
| **0.5.0-beta.5** | Pełna edycja **każdego** skrótu: cel, nazwa, ikona, kolor, rozmiar, pozycja, widoczność; przytrzymanie i przeciąganie całego kafelka; duplikaty skrótu z osobną konfiguracją. | Zmiana celu działa także dla pierwotnych dziewięciu; usunięcie skrótu nie usuwa danych; przewijanie i klawiatura bez zacięć. |
| **0.5.0-beta.6** | Utrwalenie konfiguracji panelu w bazie, backupie i migracjach; osobne układy telefonu i trybu tabletu; uprawnienia sprawdzane **również po kliknięciu skrótu**. | Restart/import zachowuje 15+ kafelków; z tabletu nie można otworzyć prywatnego sejfu przez podmianę celu. |
| **0.5.0-beta.7** | Bezpieczny, zaszyfrowany **eksport/odtwarzanie prywatnego sejfu** i test ścieżki odzyskania; nie łączyć zwykłego JSON ze szczegółami prywatnych transakcji. | Odtworzenie na osobnej testowej instalacji, błędne hasło i utracony backup nie ujawniają danych; nie kasować jedynej kopii danych. |
| **0.5.0-beta.8 i następne drobne bety** | Profil domownika, rozdział prywatnych/wspólnych uprawnień i dalsza historia wspólnego budżetu; jednocześnie stabilizacja katalogu kafelków, backupów, aktualizacji i regresja na telefonie/tablecie. | Uprawnienia działają w ekranie, eksporcie i synchronizowanym zakresie; CI + test użytkownika, bez duplikatów wpłat/operacji. |

**Zasada:** UI nie czeka do 0.9, a PayCheck nie jest porzucany. Jeśli .4–.8 ujawnią błędy, poprawić je w następnej becie przed rozszerzeniem funkcji. Nie oznaczać kolejnych numerów jako wydanych na podstawie tej tabeli.

### Po serii panelu i PayCheck — proponowane inkrementy

| Proponowana wersja / rodzina | Zakres i zależności | Odbiór |
|---|---|---|
| **0.5.1** | Dalszy PayCheck: osobiste profile, wspólny budżet z wkładami osób, cele/raty, kategorie, terminy i związki z kalendarzem; pełna kontrola dostępu. | Wspólne kwoty nie ujawniają prywatnej historii; odkładanie na cel nie księguje wydatku. |
| **0.5.2** | Spiżarnia ↔ zakupy: opcjonalna cena przy „Kupione”, historia cen produktu, sklep/data/ilość; wszystkie **ceny produktów** dozwolone na tablecie, bez rachunków/metody płatności. Stabilizacja skanera, przyjęć, pudełek/QR i remanentu. | Brak ceny ≠ 0 zł; skaner nie wymusza ceny; „Kupione” i „przyjęte” nie są tą samą operacją. |
| **0.5.3** | Paragon z wieloma pozycjami ↔ jedna płatność PayCheck; uzgadnianie zakupów, rabatów, zwrotów, ruchów magazynowych i kosztów. | Brak podwójnego wydatku przy skanie, wyjęciu, przyjęciu i korekcie remanentu. |
| **0.5.4** | Powiadomienia bankowe po uprawnieniu i potwierdzeniu, import wyciągów, deduplikacja płatności ręcznej/powiadomienia/wyciągu, reguły kategorii. | Nie przesyłać prywatnych powiadomień na tablet; ta sama transakcja księgowana raz; testy na faktycznych, bezpiecznie zanonimizowanych formatach. |
| **0.5.5** | Raporty finansowe oraz spiżarni; ceny za porównywalne kg/l; **dopiero później szacunkowa wycena całego zapasu** po wyborze jawnej metody i obsługi brakujących cen. | Wycena zapasu nie jest saldem banku ani nowym wydatkiem; brak pozornej dokładności. |
| **0.6.0** | Pojazdy: dokumenty, OC, przeglądy, oleje, serwis, opony letnie/zimowe i ich magazyn; powiązanie kosztów/terminów z PayCheck i kalendarzem. | Jedna wymiana kół aktualizuje pojazd, magazyn i historię bez duplikatów. |
| **0.6.1** | Dom, instalacje, urządzenia, gwarancje, remonty, materiały, etapy i budżety. | Jedna czynność i wydatek powiązane z miejscem i projektem, bez drugiej kopii danych. |
| **0.6.2** | Przeglądy sezonowe, odpady, konserwacje i dopracowanie planera/kalendarza między modułami. | Realia grafiku, powtórzeń i historii wykonania; zaległości nadal widoczne do potwierdzenia. |
| **0.7.0** | Ogród: lokalna baza roślin po weryfikacji licencji, konkretne uprawy, siew/sadzenie/zbiór, zadania sezonowe. | Terminy proponowane ≠ faktyczne; działa offline. |
| **0.7.1** | SUPLA: bezpieczne połączenie, lista uprawnionych rzeczywistych urządzeń/kanałów i **odczyt** ze źródłem oraz świeżością danych. | Brak tokenów w logach; brak udawanych danych i sterowania. |
| **0.7.2** | Energia/PV/CWU/bufor: bilans i historia, kW kontra kWh, propozycje priorytetów i symulacje; integracja wydatków bez podwójnego księgowania faktur. | Dane oznaczone czasem i jakością; brak pomiaru nie jest zerem. |
| **0.7.3 — warunkowo** | Dopuszczone sterowanie/automatyka tylko po audycie konkretnego osprzętu, pomiarów i bezpieczeństwa; może zostać przesunięte poza 1.0, jeśli warunki nie są spełnione. | Awaria sieci, spadek PV, ograniczenia mocy/temperatury i ręczne przejęcie sterowania nie tworzą ryzyka. |
| **0.8.0** | EDHOME Hub — lokalne API i kontrakt zdarzeń, role, kolejka zmian; telefon nadal offline; **D-Link DNS-320L rozważany na kopie, API na osobnym aktualizowanym hoście**. | Dwa urządzenia działają bez Huba, API nie jest publicznie wystawione. |
| **0.8.1** | Wi-Fi telefon ↔ tablet ↔ Hub, identyfikacja urządzeń/osób, selektywna synchronizacja wspólnych danych. | Brak prywatnego PayCheck na tablecie; bez duplikatów zmian. |
| **0.8.2** | Konflikty: remanent, zakup/cena, skan, pudełka, kalendarz; ponowne próby i zgodność schematów/protokołu. | Konflikt pokazuje co sprawdzić ponownie, nie nadpisuje cichcem nowszych danych. |
| **0.8.3** | Panel PC (przeglądarka, opcjonalny EXE później), te same role, ID i API; nie współdzielić bezpośrednio bazy SQLite. | Telefon, tablet i PC widzą ten sam uprawniony stan po synchronizacji. |
| **0.8.4** | NAS/backup: ręczny JSON, automatyczne kopie lokalne i na DNS-320L po audycie firmware/protokołów, osobna dodatkowa kopia i test odtworzenia. | Utrata Huba/NAS lub migracja nie usuwa jedynej kopii danych. |
| **0.9.0** | Widgety, powiadomienia i trwałe minutniki, tryb tabletu, finalne motywy, dostępność, klawiatura/przewijanie i optymalizacja. | Regresja na rzeczywistym telefonie i tablecie. |
| **0.9.1** | Bezpieczeństwo i odzyskiwanie: migracje wszystkich schematów, prywatne sejfy, podpis APK, aktualizacje, logi bez sekretów, utrata sieci. | Test upgrade i restore ze starszych wspieranych bet; brak wycieków. |
| **0.9.2 / RC** | Domknięcie dokumentacji, pełne testy przepływów między modułami, polityka prywatności i przygotowanie Google Play. | Brak błędów blokujących, powtarzalny podpis, komplet testów. |
| **1.0.0 Stable** | Oficjalne EDHOME bez „prototyp”; zatwierdzony zakres, opis zmian przed aktualizacją, stabilny kanał i migracja danych. | **Osobna wyraźna zgoda Edwina na każdą zmianę/merge do chronionego `main`**; brak automatycznego scalenia z `beta`. |

**Zakres 1.0 należy formalnie zamknąć przed RC.** Numer 1.0 nie oznacza, że wszystkie opcjonalne integracje/sterowanie są obowiązkowo gotowe; niesprawdzone funkcje oznaczyć jako przyszłe 1.x, nie obiecywać ich. Dodatki i poprawki po wydaniu używają kolejnych wersji, a nie zmieniają historii wydania 1.0.0. Nie przypisywać sztywnych dat ani pozorowanego procentu zaawansowania.


## Stan wykonania serii 0.5 — aktualizacja po wydaniu beta.5 (22.09.2026)

| Wydanie | Status potwierdzony | Zakres |
|---|---|---|
| `0.5.0-beta.1`–`.3` | Opublikowane wcześniej | Wspólny PayCheck, wspólne cele finansowe; prywatny sejf z oddzielnym hasłem i szyfrowanymi lokalnymi wpisami. |
| [`0.5.0-beta.4`](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.5.0-beta.4) | **Opublikowane, CI zakończone sukcesem** | Kafelki bez limitu 9: dodawanie, cele, nazwa, ikona, kolor, mały/podwójny rozmiar, przeciąganie, ukryj/przywróć, backup układu; osobna zaszyfrowana kopia prywatnego PayCheck i import bez dublowania UUID. |
| [`0.5.0-beta.5`](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.5.0-beta.5) | **Opublikowane, CI zakończone sukcesem** | Cena opcjonalna przy „Kupione”, sklep, historia rzeczywistych cen dopięta do spiżarni po przyjęciu; bez księgowania PayCheck. SQLite v21→v22 i JSON ze zwalidowaną tabelą historii cen. Manifest poprawiony o rzeczywisty changelog beta.5. |

**Ważne: seria 0.5 NIE jest jeszcze zakończona funkcjonalnie**, mimo że kolejne APK mają numer 0.5.0. Dalsze podwersje trzeba nadać dopiero przed konkretnym wdrożeniem; nie nazywać automatycznie „beta.6 = backup”, bo backup zaszyfrowany był już w beta.4. Odbiór na fizycznym telefonie nie jest zastąpiony przez test CI.

**W realizacji na beta: adaptacyjne kolumny i regulowany dwustopniowy gest; brak zmian main bez każdorazowej zgody.**

**Do zamknięcia 0.5.x (kolejne wersje, zakres i numeracja do potwierdzenia po testach):** (1) rzeczywiste testy telefonu: 15+ kafelków, przeciąganie, backup i import, starsza kopia v21→v22, szyfrowany sejf z testowym eksportem/importem bez utraty danych; (2) pełne role i profile oddzielające prywatne dane na współdzielonym tablecie; (3) jeden paragon z pozycjami ↔ jedna potwierdzona płatność PayCheck, bez dublowania skanu/przyjęcia; (4) korekty historii cen, jednostki i rozmiary opakowań; (5) cele i raty osobiste/wspólne oraz wyciągi i propozycje bankowych transakcji po audycie prywatności i danych; (6) regresja CI i poprawny changelog każdego następnego wydania. Dopiero po odbiorze uznać serię 0.5 za domkniętą, bez wymuszania przejścia do 0.6.

**Aktualny porządek po doprecyzowaniu (decyzje vs wykonanie):** (1) odbiór na urządzeniu wydanej beta.5 / SQLite v22 i kopii, bez ponownego planowania już wydanej historii cen; (2) adaptacyjny panel, regulowane progi gestów i inicjalne skopiowanie układu telefonu dla **stałego profilu „Wspólny” na tablecie**, potem niezależne układy i ochrona administracji/prywatności; (3) wyciąganie −1 po domyślnych, edytowalnych 5 s, inny kod anuluje niezapisane odliczanie; (4) miejsce per zakup wybierane/potwierdzane dopiero przy przyjęciu, cena **jednostkowa** i poprawne wyliczenie wartości w zależności od ilości, bez automatycznego księgowania; (5) partia/opakowanie: wiele otwartych, różne rozmiary, daty ważności, przypomnienia; (6) remanent: jeśli choć jedna pozycja ma konflikt, wstrzymać wszystkie korekty do ponownego sprawdzenia zmienionych, zachowując resztę wyników; (7) wspólny PayCheck: ręcznie potwierdzone wkłady osób i suma, osobno od prywatnych ksiąg; wiele prywatnych profili z odrębnymi kluczami, bankowe propozycje z wyciągów/powiadomień bez dublowania; (8) PV: rzeczywista telemetria, dynamiczny priorytet z ręcznym nadpisaniem; sterowanie dopiero po osobnym audycie sprzętu. **Otwarte 6/8/16** (zaległe cykle, wspólny kalendarz, kolejność QR/magazynu) nie są zadaniami z rozstrzygniętym zachowaniem. Nie przypisywać numerów przyszłych bet przed implementacją, testami i publikacją.

**Ochrona Stable:** praca i dokumentacja na `beta`; każda zmiana lub merge do `main` wymaga osobnej wyraźnej akceptacji Edwina.
