# EDHOME — 0.5.0-beta.1 — PayCheck: wspólny budżet

## Funkcje pierwszego inkrementu

- Nowy ekran PayCheck osiągalny z ekranu głównego. W tym wydaniu widoczny jest **wyłącznie wspólny budżet gospodarstwa**.
- Ręczne dodawanie przychodu lub wydatku w PLN, kategoria i opcjonalny opis. Dialog z wyraźnym zatwierdzeniem przed zapisem; anulowanie nic nie księguje.
- Kwoty przechowywane są jako całkowita liczba groszy, bez `float` ani `double`. Zestawienie wspólnego salda i 40 ostatnich transakcji.
- Odporność na ponowne zatwierdzenie tej samej operacji: niepowtarzalny `operation_id` w SQLite. Wpisy są tylko ze źródła ręcznego — lista zakupów, przyjęcia do spiżarni, remanenty i szacowane koszty energii **nie tworzą transakcji**.
- SQLite v19→v20 dodaje `paycheck_transactions`, backup JSON zawiera nowe wpisy i importuje kopie ze starszych wersji.
- Beta działa bez PIN-u. Dlatego w 0.5.0-beta.1 **nie ma pól, ekranów ani kopii osobistych kont i prywatnych transakcji**. Trzeba przed nimi dodać osobną ochronę i test braku wycieku na wspólny tablet.

## Testy odbiorowe

1. Wykonaj eksport kopii z beta.13 i zainstaluj beta.1 na obecną wersję — nie odinstalowuj.
2. Sprawdź swoje produkty, stan opakowań, pudełka, kody i historię przyjęć.
3. PayCheck: dodaj wpłatę 200,00 zł oraz wydatek 12,50 zł. Saldo ma wskazywać 187,50 zł, bez problemu z przecinkiem.
4. Anuluj kolejny wpis — saldo bez zmian. Nieprawidłowe kwoty, ułamki grosza, 0 i liczby ujemne mają być odrzucone.
5. Zaznacz zakup jako kupiony i przyjmij opakowanie do spiżarni — PayCheck ma pozostać bez zmian.
6. Wyeksportuj i przywróć kopię JSON: historia PayCheck, pudełka i zapasy mają pozostać.
7. Nie uznawać CI za fizyczny test na Huawei; osobno sprawdzić czytelność i klawiaturę.

## Po pierwszym wydaniu

Priorytet: prywatne profile z izolacją i ochroną przed dostępem na wspólnym urządzeniu, cele i raty, później uzgadnianie wyciągów oraz bankowych powiadomień. Żadnego automatycznego księgowania bez zatwierdzenia.

## 0.5.0-beta.2 — wspólne cele finansowe

- W PayCheck można utworzyć wspólny cel (nazwa, docelowa kwota PLN), zobaczyć „odłożone / docelowe / pozostało” i ręcznie odłożyć kwotę z potwierdzeniem. Wszystkie kwoty w groszach; cel nie może zostać przekroczony, a UUID zapisu nie pozwala na podwójne naliczenie.
- **Odkładanie jest wyłącznie planem/ewidencją**, nie wydatkiem, nie przelewem, nie zmianą salda wspólnego PayCheck. Nie zakładać, że oznacza rzeczywiste środki na rachunku; prawdziwa płatność jest oddzielną transakcją.
- Beta bez PIN-u nie przechowuje i nie eksportuje kont prywatnych. Cele są tylko wspólne. Przed prywatnymi finansami wymagana osobna kontrola dostępu i ograniczenie widoczności na wspólnym tablecie.
- SQLite v20→v21 dodaje `paycheck_goals` oraz `paycheck_goal_allocations`; backup zawiera obie tabele, odtwarza kopie v20 i starsze. Weryfikacja odrzuca osierocone wpłaty, duplikaty operacji i sumę ponad cel.

Odbiór: cel OC 700 zł, odłożenie 500 zł, potem 200 zł; pozostało 0 zł, saldo wspólne bez zmian. Anulowanie albo ponowny zapis tego samego UUID nie zmienia odłożonej kwoty. Testy CI nie zastępują testu na Huawei.

## 0.5.0-beta.3 — szyfrowany prywatny sejf PayCheck

- Beta nadal uruchamia się bez PIN-u. W PayCheck osobny przycisk „Prywatny sejf PayCheck”; przy pierwszym wejściu użytkownik tworzy **oddzielne hasło 12–64 znaków** i potwierdza je. Prywatny ekran i formularz hasła mają FLAG_SECURE, aby nie trafiać na zrzuty ekranu i podgląd ostatnich aplikacji.
- Dane jednej lokalnej osoby (prywatny przychód, wydatek, kategoria, kwota, opis i data) są szyfrowane AES-256-GCM z losowym IV dla każdego wpisu. Klucz jest wyprowadzony z hasła i losowej soli PBKDF2-HMAC-SHA256 (310 000 iteracji). Hasło i klucz **nie są zapisywane**. W lokalnej oddzielnej bazie SQLite pozostają tylko UUID operacji i ciphertext; powtórna operacja nie nalicza drugi raz.
- Po opuszczeniu aplikacji lub prywatnego ekranu sejf jest blokowany, bufor klucza zerowany, a ekran prywatny usuwany przed zdjęciem FLAG_SECURE. Po pięciu błędnych hasłach blokada na pięć minut. Wspólny budżet pozostaje dostępny bez hasła i nie widzi prywatnych kwot.
- **WAŻNE: prywatne wpisy nie wchodzą do zwykłej kopii EDHOME JSON i nie są przenoszone między telefonami**; Android Auto Backup pozostaje wyłączony. Nie odinstalowywać aplikacji. Utrata hasła lub telefonu = brak możliwości odzyskania prywatnych danych. Bezpieczny eksport i wiele oddzielnych profili pozostają odrębnym etapem; nie podawać fikcyjnych zapewnień o odzyskiwaniu.
- Nie zmienia się główna baza SQLite v21, nie ma migracji wspólnych danych. Lista zakupów, spiżarnia i energia nigdy same nie księgują prywatnych wpisów.

Odbiór: utwórz sejf z hasłem 12+ znaków; zapisz prywatny przychód 200 zł i wydatek 12,50 zł; prywatne saldo 187,50 zł, **wspólne bez zmian**. Zablokuj sejf, spróbuj otworzyć złym hasłem; po wyjściu z aplikacji hasło wymagane ponownie. Zwykły eksport JSON nie może zawierać prywatnej kwoty ani opisu.


## 0.5.0-beta.4 — kafelki i zaszyfrowany eksport prywatny

- Oficjalnie opublikowana beta `0.5.0-beta.4` (versionCode 47), [wydanie na GitHub](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.5.0-beta.4). Weryfikacja CI obejmuje kompilację Android Beta/Stable, podpis beta oraz kontrakty danych; **test na konkretnym telefonie jest nadal osobnym etapem**.
- Panel ma dowolną liczbę skrótów i cele: Minutniki, Lista zakupów, PayCheck, Odpady, Czynności, itd.; można zmienić cel, podpis, ikonę, kolor, wielkość mała/podwójna, pozycję, ukryć i przywrócić, usunąć tylko skrót. Poprzednie dziewięć ma być migrowane bez straty wyglądu/kolejności; nowe skróty zawiera backup JSON. Wspólne PayCheck i prywatny sejf nie stają się tym samym danymi przez dodanie skrótu.
- **W tym zbudowanym kodzie dodano osobną szyfrowaną kopię prywatnego PayCheck**, niezależną od zwykłego JSON EDHOME. Po odblokowaniu prywatnego sejfu można podać osobne hasło kopii (12–64 znaki) i wybrać miejsce zapisu JSON. Dane i UUID operacji są szyfrowane AES-GCM z osobnym losowym salt oraz kluczem wyprowadzonym z hasła kopii. Przy imporcie trzeba podać hasło istniejącego sejfu oraz hasło archiwum; nowe rekordy trafiają transakcyjnie, identyczne UUID są pomijane, konflikt przerywa całą operację. Prywatne kwoty/hasła nie powinny trafić do wspólnej kopii ani logów. **Nie zapisuj niezabezpieczonego JSON archiwum ani hasła w repo.**
- To nie jest jeszcze zakończony test migracji prywatnego sejfu na inny telefon: przed odinstalowaniem starej Bety wymagane są **dwie oddzielne, sprawdzone kopie** oraz odbiór eksport/import na fizycznym urządzeniu. Samo przejście CI i wynik analizy źródła nie gwarantują odzyskania konkretnej kopii. Oryginalne hasło sejfu i osobne hasło kopii mogą być różne.
- Poza zwykłą kopią EDHOME prywatny sejf pozostaje osobny. Nie modyfikować `main` i nie nazywać tego Stable bez akceptacji Edwina.

**Test na telefonie:** (1) dodaj co najmniej 15 kafelków i zmień cel jednego pierwotnego; (2) przenieś Minutniki i PayCheck do siatki; (3) sprawdź po restarcie/eksporcie/importcie, że układ pozostał; (4) w testowym sejfie dodaj transakcję, zapisz zaszyfrowaną kopię, zaimportuj dwa razy — drugi import powinien dopisać 0; (5) błędne hasło kopii i uszkodzony plik mają odrzucić import bez zmiany salda. **Nie testować na jedynej kopii realnych danych.**


## 0.5.0-beta.5 — ceny zakupów w spiżarni

- Wydanie [0.5.0-beta.5](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.5.0-beta.5), `versionCode=48`, SQLite `v22`. CI potwierdziło kompilację Beta/Stable, migracje i kontrakty cen; odbiór interfejsu i aktualizacji na telefonie nadal wymaga testu.
- Lista zakupów: przy oznaczaniu „Kupione” pojawia się dobrowolna cena **za jedną wskazaną jednostkę pozycji** (np. 1 szt./kg/l), opcjonalny sklep lub przycisk „Kupione bez ceny”. Nie podano ceny = **nieznana**, nigdy 0 zł. „Anuluj” nie zmienia stanu.
- Zakup i przyjęcie do magazynu to **odrębne fakty**: dopiero potwierdzone przyjęcie wiąże zapis ceny z konkretnym produktem i zmienia liczbę opakowań. Karta produktu pokazuje ostatnią powiązaną cenę oraz historię dat i sklepów; nie porównuje automatycznie różnych jednostek/opakowań.
- Stawki są przechowywane w pełnych groszach, zdarzenia mają identyfikator operacji. Dodatkowa tabela `pantry_purchase_prices` migruje bez kasowania historycznych produktów i transakcji; zwykły JSON EDHOME zawiera jej zwalidowane rekordy, starsze kopie nadal akceptowane. Ceny produktów mogą być udostępniane w przyszłym trybie tabletu **bez dostępu do prywatnych kont PayCheck**.
- **Żaden zakup, skan, przyjęcie, wyjęcie ani remanent nie księguje sam pieniędzy w PayCheck.** Powiązanie paragonu z jedną rzeczywistą transakcją, wielkości paczek, korekty błędnych cen i późniejsza szacunkowa wartość całego zapasu należą do kolejnych inkrementów 0.5.x.

**Odbiór ręczny:** zrób kopię przed aktualizacją; utwórz testową pozycję Ryż z jednostką „szt.”, zaznacz Kupione z ceną 6,49 zł i sklepem; sprawdź, że PayCheck i stan spiżarni nie zmieniły się. Przyjmij ją do konkretnej karty ryżu, sprawdź cenę na karcie i historię; kup drugi raz przez nowy wpis z inną ceną, sprawdź, że obie ceny pozostają. Powtórz scenariusz „Kupione bez ceny”, eksport/import JSON v22 oraz przywrócenie kopii v21. Test wykonuj na danych próbnych.

## 0.5.0-beta.6 — adaptacyjny panel i dwustopniowy gest (po udanym wydaniu)

- Kolumny wyliczane z dostępnej szerokości: na wąskim telefonie mniej, na typowym ok. trzy, na szerokim tablecie maksymalnie sześć czytelnych kolumn. Bez limitu liczby kafelków; strona przewijana w pionie. Podwójny kafelek zajmuje maksymalnie dwa pola i nie wystaje poza rząd.
- Dotknięcie otwiera funkcję. Krótsze przytrzymanie **po puszczeniu** otwiera menu, a dłuższe (w trakcie trzymania) rozpoczyna przeciąganie całego kafelka; menu nie przesłania dłuższego gestu. Uchwyt ⋮⋮ pozostaje alternatywą.
- Ustawienia → Kafelki • czas przytrzymania: krótki 300/450/600/800 ms (domyślnie 450), długi 900/1100/1400/1800 ms (domyślnie 1100), długi co najmniej 200 ms po krótkim. Oba ustawienia są w kopii JSON EDHOME i wracają po imporcie; starsze kopie dostają domyślne wartości.
- SQLite pozostaje v22, schemat transakcji i prywatny sejf niezmienione. Testy: szerokości 320/393/600/800 dp, gesty i backup. Odbiór palcem na Huawei i tablecie jest osobnym testem użytkownika — zielone CI go nie zastępuje.


## 0.5.0-beta.7 — diagnostyka przeciągania wielu kafelków

- Usunięto przyczynę crasha `HomeTileOrder.moved:34`: podgląd używa teraz tego samego dynamicznego `HomeTileCatalog.moved` co zapis kolejności, zamiast starszego limitu dziewięciu kafelków.
- Brak widoku kafelka, niekompletny zestaw pozycji lub nieaktualny stan przeciągania powoduje bezpieczne przerwanie gestu i zachowanie zapisanej kolejności.
- Nowe regresje statyczne i Java obejmują >9 kafelków, skróty niestandardowe, ukrywanie, sloty i zgodność podglądu z zapisem. SQLite v22 pozostaje bez migracji.
- Odbiór na telefonie jest osobny: aktualizacja bez odinstalowania, kilkanaście kafelków, długa/krótka interakcja, ukrywanie, edycja i restart aplikacji. Stable main bez zmian.


## 0.5.0-beta.8 — aktualizator bez zapętlonego instalatora

- Automatyczne sprawdzanie manifestu co najwyżej raz na 15 minut w aktywnej sesji; pobieranie nadal sprawdzane co 30 sekund. Ręczne sprawdzenie niezależne.
- Po przekazaniu zweryfikowanego APK Androidowi instalator nie otwiera się sam ponownie dla tego samego pliku. Po anulowaniu instalacji można jawnie ponowić z ekranu Aktualizacje. Brak zgody na instalowanie nie blokuje późniejszej próby.
- Zachowana weryfikacja podpisu i SHA-256, Beta bez PIN-u i SQLite v22 bez migracji. Stable `main` bez zmian.


## 0.5.0-beta.9 — weryfikowane kopie EDHOME

- Poprawione typy danych w imporcie historii cen: `unit_price_grosz` i `quantity_milli` są liczbami; cena bez przyjęcia do produktu ma puste `pantry_id`, a nieznana ilość jest pusta, nie zero. Puste `lent_to` niezapożyczonych rzeczy jest prawidłowe.
- Zapis JSON do wybranego dokumentu jest następnie odczytywany i porównywany bajt po bajcie przez długość i SHA-256. Sukces jest zgłaszany dopiero po weryfikacji. Niekompletny plik nie jest automatycznie usuwany ani uznawany za kopię.
- Jeżeli zapis ustawień przy odtwarzaniu się nie uda, transakcja SQLite cofa dane. SQLite i SharedPreferences nie mają wspólnej gwarancji atomowości na wypadek nagłego wyłączenia urządzenia.
- Nowy test zgodności typów/pustych kolumn i symulowany round-trip SQLite→JSON→SQLite. Odbiór na prawdziwym telefonie nadal osobno. Zwykły JSON nie zawiera prywatnego sejfu PayCheck, który ma własny szyfrowany eksport.
- Bez migracji SQLite v22, Beta bez PIN, Stable `main` bez zmian.


## 0.5.0-beta.9.1 — pilny fix Ustawień i diagnostyki

- Crash `IllegalStateException` w `MainActivity#settings` naprawiony: `card()` podłącza `gestures` do ekranu, więc nie dodawać tej samej karty ponownie przez `body.addView(gestures)`.
- Kopia do czatu ma konfigurowalny limit **5 000 / 12 000 (domyślnie) / 20 000 znaków**, pobiera najnowsze kompletne linie i opisuje pominięcie starszych wpisów. Nie zmienia limitu przechowywania diagnostyki: bieżący i poprzedni plik po 1 MB.
- Eksport `.txt` zawiera całą zachowaną historię z obu segmentów (poprzednio używał podglądu obciętego do 160 tys. znaków); nie usuwa logów i nie zapisuje prywatnych danych w diagnostyce. Odczyt UTF-8 dekoduje cały segment, bez dzielenia polskich znaków między bloki.
- Bez migracji SQLite v22; Stable `main` bez zmian. Testu Android UI na fizycznym telefonie CI nie zastępuje.


## 0.5.0-beta.9.2 — kompaktowy wybór stylu

- Ustawienia: sześć wysokich kart motywów zastąpiono pojedynczą kartą z rozwijaną listą, krótkim opisem wskazanego motywu i przyciskiem „Zastosuj styl”.
- Początkowo zaznaczony jest aktualny motyw, zmiana wyboru w menu sama nie zapisuje ustawień; po zatwierdzeniu zostają te same nazwy w preferencjach i kopii JSON. Zmiana nie wymaga migracji SQLite v22.
- Zachowano poprawkę zapobiegającą ponownemu dodaniu karty do widoku, limit kopiowania logów i pełny eksport .txt. Stable `main` bez zmian; test interakcji na urządzeniu pozostaje osobny.


## 0.5.0-beta.10 — odporność skanera (bez zmiany działania licznika)

- Start aparatu jest obsługiwany w `try/catch`: odmowa uruchomienia kamery zwalnia oczekujący wynik skanu, kończy serię bez nowej operacji magazynowej i oferuje ręczny wpis.
- Odczyt kartoteki produktu oraz jego opakowania jest chroniony przed wyjątkami SQLite; wyjątek trafia do diagnostyki bez wrażliwej treści, a użytkownik otrzymuje czytelny komunikat.
- Powtórzony identyfikator operacji daje jawny komunikat „nie zmieniono ponownie stanu”. Błąd zapisu nie powoduje automatycznego wznowienia aparatu ani podwójnego komunikatu „seria zakończona”; surowe komunikaty wyjątków SQLite nie trafiają do okna aplikacji.
- Test kontraktu sprawdza ścieżki resetu, brak automatycznego ponowienia, atomową operację SQL oraz brak zmian SQLite v22. Odbiór aparatu i odmowy uprawnienia na prawdziwym Androidzie pozostaje osobno.
- **Nie wprowadzono jeszcze odliczania 5 s** ani zmiany działania przy innym kodzie; to następny zatwierdzony etap. Stable `main` pozostaje bez zmian.


## 0.5.0-beta.11 — ciągłe wyjmowanie z odliczaniem

- Nowy aparat `PantryTakeCaptureActivity` tylko do wyjmowania; dodawanie +1 zachowuje dotychczasowy przepływ. Skan znanego produktu z dodatnim zapasem uruchamia automatyczne −1 po 5 s; ustawienia pozwalają zmienić czas na 3/5/8/10 s.
- Kamera pozostaje otwarta: inny kod zastępuje oczekujące wyjęcie, resetuje licznik i **nie** odejmuje pierwszego produktu. Błędny/nieznany kod i brak zapasu nie powodują korekty. Licznik anulowany na tle, wyjściu i anulowaniu nigdy nie zapisuje zaległej operacji.
- Ten sam kod z kolejnych klatek nie powoduje ponownych wyjęć: drugie opakowanie wymaga jawnego przycisku albo zeskanowania innego kodu. Opcja „Wyjmij teraz” omija tylko oczekiwanie, nigdy walidację i zapis transakcyjny. W trybie pojedynczym po jednym wyjęciu aparat się zamyka; w serii pozostaje do zakończenia.
- Preferowany czas jest objęty zwykłą kopią JSON i walidacją importu; bez migracji SQLite v22. Czysty test Java sprawdza zastępowanie kodu, brak podwójnego skanu i anulowanie po tle; kompilacja Androida sprawdza dostępność klas skanera. Odbiór ciągłego skanowania na fizycznym telefonie pozostaje osobnym testem.
- Stable `main` bez zmian.


## 0.5.0-beta.11.1 — stabilizacja linii 0.5

- Log z rzeczywistego telefonu pokazał jeden zatwierdzony `PANTRY_TAKE_COMMITTED`, bez `ERROR_UNCAUGHT` we wskazanym fragmencie; `PANTRY_TAKE_UNKNOWN` nie potwierdza usterki sam w sobie — oznacza kod bez powiązania z magazynem.
- Naprawa sekwencji kamery A → B → A: powrót do ostatnio **rzeczywiście wyjętego** A przerywa licznik B i wymaga jawnego wyboru kolejnego opakowania A. Przy nieznanym, anulowanym lub błędnym kodzie przycisk ponowienia nie jest automatycznie odblokowywany.
- Testy Java sprawdzają przerwanie, zatwierdzenie, anulowanie, klatki powtarzające kod, brak domyślnego drugiego ubytku i jawne ponowienie. Dodatkowa kontrola regresji kodu jest w CI.
- Bez migracji SQLite v22 i bez nowych modułów; pozostaje osobny odbiór na fizycznym telefonie. Stable `main` bez zmian.


## 0.5.0-beta.11.2 — stabilizacja lokalnego rozpoznawania kodów

- Wyjmowanie porównuje zapisany kod również z bezpiecznymi równoważnikami UPC-A / EAN-13 / GTIN-14 (wiodące zera), aby nie zgłaszać fałszywego „nieznany” wyłącznie z powodu sposobu odczytu kodu przez kamerę.
- Pierwszeństwo ma dokładnie zapisany kod. Przy sprzecznych powiązaniach wariantów z różnymi produktami odjęcie jest blokowane. Zmienia się wyłącznie sposób szukania istniejącego lokalnego powiązania: żaden nieznany kod nie zakłada automatycznie nowej kartoteki.
- Ponowny kod fizycznie tego samego produktu wymaga zatwierdzenia na podstawie ID produktu, nawet jeśli aparat zmieni zapis UPC na EAN. Do trwałego ruchu magazynowego przekazywany jest znaleziony lokalny kod, a identyfikator ostatniego produktu aktualizuje się dopiero po potwierdzonym `COMMITTED`.
- Testy kontraktu oraz istniejące testy kandydatów UPC/EAN i odliczania; kompilacja nie zastępuje odbioru na telefonie. Nie ma nowych modułów ani migracji SQLite v22. Stable `main` nietknięty.


## 0.5.0-beta.12 — zakupy, przyjęcie i miejsce docelowe

- Miejsce jest wybierane **opcjonalnie przy dodaniu konkretnej pozycji** zakupów i ponownie wybierane albo potwierdzane przy `Przyjmij do spiżarni`. Przyjęcie zapisuje `place_id` oraz nazwę miejsca w **historii tego przyjęcia**; miejsce nie jest sztywną cechą produktu. Można przyjąć także `Bez miejsca`.
- Usunięcie miejsca zeruje powiązanie z oczekującymi zakupami i historycznymi przyjęciami, ale zachowuje historyczną nazwę w przyjęciu. „Kupione” nie dodaje stanu; potwierdzenie przyjęcia wciąż jest jedną transakcją SQL i jednej pozycji nie da się przyjąć drugi raz.
- Cena jest ceną **za 1 szt./kg/l** wg jednostki pozycji. Podgląd liczy łączną wartość jako cena × ilość (grosze, BigDecimal, zaokrąglenie na sumie); przy nieznanej ilości suma pozostaje nieznana, a nie 0 zł. W historii cen widoczne są zapisane ilości i łączne koszty. Bez automatycznego wydatku PayCheck.
- SQLite **22 → 23** dodaje opcjonalne pole `shopping_items.place_id` i `shopping_receipts.place_id/place_name_snapshot`. Kopie v22 nadal są importowane, uzupełniając nowe pola bez miejsca; pełna kopia v23 zapisuje nowe powiązania. Wydanie wymaga testu instalacji/aktualizacji na telefonie, poza testami CI.
- Stable `main` pozostaje nietknięty.
