# EDHOME — pełny rejestr ustaleń koncepcyjnych

> **Główne źródło ustaleń EDHOME — Idea by Edwin.** Wymagania nie są automatycznie gotowymi funkcjami. Stan implementacji i kolejność dostaw: [ROADMAP.md](ROADMAP.md) oraz [README.md](../README.md). Rzeczywiste repo to `edwinkarolczyk/Edhome` (obecnie **publiczne**), a prace rozwojowe odbywają się wyłącznie na `beta`; `main` bez zmian. Beta 0.3.0 ma **celowo wyłączony PIN wyłącznie w Beta**, opcjonalnego wykonawcę, grafik z wyjątkami, trzy orientacyjne propozycje terminu oraz lokalną listę zakupów; SQLite v8. Stable zachowuje PIN. Priorytet i czas wprowadzono w 0.2.6, wykonawców w 0.2.7, grafik w 0.2.8. Dawne wpisy „Edhime”/„prywatne repo” poniżej są opisem historycznych ustaleń, nie aktualną konfiguracją GitHub. Nie kopiujemy automatycznie kodu innych aplikacji.

## 1. Wizja

Jeden system zarządzania gospodarstwem domowym dla kilku osób: finanse, dom i remonty, czynności, kalendarz, spiżarnia i magazyn, ogród, pojazdy, energia i SUPLA. Dane lokalnie; internet nie może być wymagany do codziennego działania. Moduły połączone logicznie, bez dublowania rekordów. Użytkownik decyduje o działaniach planera.

## 2. Wygląd, telefon i tablet

- Nowy, spójny design z gestami i użytecznością inspirowaną Trenerem 2, bez kopiowania jego logiki. Motywy: **Grafitowy** (domyślny), **Leśny**, **Jasny**; przełączane na żywo. Kafelki można dodawać, ukrywać, przeciągać i układać osobno dla telefonu i tabletu.
- Telefon ok. 6,4 cala; formularze przewijalne również przy otwartej klawiaturze. Pełnoekranowy kalendarz bez stałego dolnego paska: miesiąc domyślnie, poniedziałek jako pierwszy dzień, weekendy zawsze, skróty wydarzeń i kolory modułów. Widoki: miesiąc, tydzień, dzień, agenda; szybkie dodawanie, przeciąganie, wydarzenia cykliczne, załączniki/zdjęcia, kategorie, przypisanie osoby, widget Androida.
- Tablet **przenośny** (spiżarka), panel + pełna aplikacja, PIN do całości, ekran wygaszany po bezczynności. Każdy użytkownik może zmieniać panel i jego kafelki. Przykładowe kafelki: wspólne zakupy, minutniki prania/suszenia, magazyn spiżarni, domowe czynności, SUPLA, szybkie dodawanie głosowe; pełny kalendarz dostępny poza głównym panelem. Lokalny silnik mowy tylko po weryfikacji sprzętu i możliwości offline.
- Ikona EDHOME z zaokrąglonymi rogami, warianty Stable/Beta, duże widoczne oznaczenie BETA. Nazwa gospodarstwa może być własna i nie zmienia marki aplikacji.

## 3. Wspólny kalendarz i planer

- **Jeden kalendarz gospodarstwa**, z wydarzeniami płynącymi z właściwych modułów (finanse, ogród, śmieci, pojazdy, remonty, zadania itd.). Prywatne wydarzenie drugiego domownika widoczne jako „Zajęty” bez treści; planer zna dostępność, nie prywatny opis.
- Osobny moduł **Czynności**: samodzielne („Zebrać winogrona”, zakupy, posprzątać) lub opcjonalnie przypięte do 0..N rzeczy, miejsc, upraw, pojazdów czy instalacji. Osobno definicja, reguła i konkretne wykonanie; historia kolejnych wykonań.
- Powtarzanie: jednorazowo, codziennie, co tydzień/miesiąc/rok, co N jednostek, przed wiosną/latem/jesienią/zimą, ewentualnie wg przebiegu i motogodzin. Konfigurowalne początki sezonów, wyprzedzenie, liczenie terminu od daty planowanej albo rzeczywistej; sezonowy wykaz czynności i checklisty przygotowań (np. kosiarka, piła, dach/PV — przy zachowaniu zasad bezpieczeństwa).
- Szybkie dodawanie kategorii, czasu wykonania, terminu granicznego, priorytetu i uprawnionych wykonawców. Po analizie grafiku zmianowego z ręcznymi wyjątkami, wydarzeń, dojazdu/odpoczynku i czasu wykonania EDHOME **proponuje trzy terminy**. Nie przypisuje terminu samodzielnie. Domyślny jeden odpowiedzialny; przy 2+ osobach wybór według wolnego czasu, obciążenia i preferencji; wspólny wolny termin dla pracy wymagającej kilku osób.
- Obowiązki: stałe, rotacyjne lub wg dostępności; szablony, automatyczne checklisty, historia, zadania mieszczące się w wolnych 15/30/60 min. Zaległe zadanie pozostaje zaległe, a EDHOME proponuje nowy termin.
- Powiadomienia konfigurowalne per domownik i kategoria; godziny ciszy. **Tylko jawnie skonfigurowane krytyczne** mogą próbować ją ominąć, o ile Android na to pozwala; przypomnienia do potwierdzenia, odroczenie i sensowne ponaglenia bez spamu.
- Śmieci: frakcje, odbiór, przypomnienie o wystawieniu i potwierdzenie; ręcznie na start, później import harmonogramu gminy.
- Aktywne czynności: pralka/suszarka/zmywarka i własne urządzenia, oddzielne minutniki, wznowienie po zamknięciu aplikacji i restarcie urządzenia; etap „pranie zakończone” ≠ „wyjęto pranie”; łańcuch pranie→suszenie. Wpis czasu HH:MM bez niejednoznacznego 1,3h.

## 4. Finanse — PayCheck

- Każda osoba ma **osobisty PayCheck** (wydatki, przychody, konta, cele, raty, raporty, budżety), dodatkowo osobny **wspólny budżet** gospodarstwa. Prywatnych danych finansowych nie rozsyłać na wspólny tablet bez wyraźnej autoryzacji.
- Pełna funkcjonalność PayCheck jako moduł EDHOME po audycie wybranej wersji źródłowej; nie modyfikować oryginału.
- Automatyzacja: na telefonie właściciela odczyt wybranych **powiadomień bankowych** (po uprawnieniu systemowym), rozpoznawanie wpływu, wydatku i transferu między własnymi kontami; pokazanie **do zatwierdzenia**, następnie uzgodnienie z wyciągiem. Wpis ręczny opcjonalny, nie podstawowy (np. na tablecie). Import dziennych wyciągów i opcjonalna integracja bankowa później, pod warunkiem bezpieczeństwa i zgodności z zasadą minimalnego internetu.
- Reguły kont i budżetów, deduplikacja transakcji wykrytej w powiadomieniu i później w wyciągu; szacowany koszt energii nie może ponownie księgować faktury.
- Cel finansowy może być związany z konkretnym obiektem/terminem, np. OC Audi: cel 700 zł, odłożone 500 zł, do terminu cztery miesięczne wpłaty -> propozycja 50 zł/mies. Odłożenie środków nie jest nowym wydatkiem; koszt polisy księgowany po zakupie.

## 5. Magazyn / spiżarnia / QR

- Jedna lista rzeczy, bez konieczności używania pudełka. Stan ilości **opcjonalny**; brak ilości != 0. Zdjęcia i opisy opcjonalne; wyszukiwarka nazw i bez polskich znaków prowadzi do karty rzeczy, pudełka i aktualnego miejsca.
- Dowolne nazwy miejsc, np. Piwnica → Pod schodami, garaż, wnęka — **nie wymagamy regału/półki**. Pudełko i miejsce mają trwały osobny numer oraz QR z ID, bez lokalizacji; przedmioty/narzędzia również mogą mieć własny QR. Przeniesienie całego pudełka aktualizuje pochodną lokalizację zawartości bez zmiany naklejek.
- QR miejsca pokazuje zawartość oczekiwaną i potwierdzoną; możliwa inwentaryzacja z datą ostatniej kontroli. QR narzędzia pokazuje ostatnią/aktualną znaną lokalizację, historię i wypożyczenia.
- Wybór **celu skanowania**: znajdź, dodaj do pudełka, wyjmij, przenieś rzecz/pudełko, pożycz/oddaj; można zaznaczyć kilka nieprzydzielonych przedmiotów i zeskanować docelowe pudełko. Rozróżnienie rzeczy pożyczonych komuś / od kogoś, terminy zwrotu i propozycja odłożenia po oddaniu.
- **Tablet w spiżarce ma skaner produktów i kodów QR**: skan kodu kreskowego produktu (np. EAN/UPC) lub QR przedmiotu, pudełka i miejsca; wyświetla duże „Dodaj do spiżarni” / „Wyciągnij ze spiżarni” oraz opcjonalnie „Przenieś”, „Sprawdź”. Jedno skanowanie nie zmienia stanu, dopóki użytkownik nie wybierze celu i nie zatwierdzi operacji. Skan produktu rozwiązuje lokalny katalog, jeśli znany; nieznany produkt pozwala założyć kartę. Liczenie ilości dla produktów spożywczych jest opcjonalne, może mieć jednostki (szt., kg, l) i progi minimalne; zwykłe narzędzia nie wymagają stanu liczbowego. Stan aktualizuje się lokalnie, a potem synchronizuje w Wi-Fi. Skaner przez kamerę tabletu na start; zewnętrzny czytnik Bluetooth/USB jako przyszły adapter.
- Lista zakupów wspólna, możliwość powiązania z brakami, zapasami i ewentualnie planowaniem posiłków. Przy usuwaniu/przenoszeniu zachować historię i spójność relacji.

## 6. Pozostałe moduły

- **Pojazdy:** karta Audi/innych aut; OC, przeglądy i za ile dni, serwis, oleje, filtry, części, przebieg; komplety opon jako osobne obiekty (rozmiar, DOT, data zakupu, stan/bieżnik, okres użycia, lokalizacja), historia i koszty połączone z PayCheck i kalendarzem.
- **Dom i remonty:** pomieszczenia, instalacje, urządzenia, konserwacja, zadania sezonowe, projekty z etapami i budżetem, zdjęcia, gwarancje, dokumenty, kontakty i informacje awaryjne offline.
- **Ogród:** lokalna baza roślin open-source po weryfikacji licencji treści i obrazów; warzywa, owoce, zioła; siew, sadzenie, pielęgnacja, zbiór; osobne gatunek/odmiana i konkretna uprawa; dane sugerowane vs planowane vs rzeczywiste, plony, później grządki i płodozmian.
- **Energia i SUPLA:** PV, ogrzewanie, CWU, bufor, media, koszty; odczyt SUPLA w pierwszym etapie, przez LAN tam, gdzie urządzenie i API to umożliwiają; nie zakładać offline dla wszystkich urządzeń i nie odcinać zasilania urządzeń domowych zwykłym przekaźnikiem. Sterowanie dopiero po odpowiednich zabezpieczeniach.
- **Powiązania:** każdy obiekt może mieć powiązane czynności, dokumenty, miejsce, budżet, koszt i wydarzenie; karta „Powiązane” pomaga przechodzić między nimi bez duplikacji.

## 7. Wspólny rdzeń i bezpieczeństwo danych

Trwałe identyfikatory; typowane atrybuty dodatkowe i relacje; rejestr obiektów i zdarzeń; wspólne reguły, silnik czasu i uprawnień; lokalne transakcje i kolejka zmian, migracje schematu, historia i eksport. Nie stosować dwóch anonimowych pustych pól „na zapas”; nowe typowane właściwości/relacje dodawać bez utraty ID i historii. Moduły pozostają niezależne na poziomie odpowiedzialności, ale korzystają z tych samych usług. Offline-first, synchronizacja tylko domowym Wi-Fi, przyszły szyfrowany backup lokalny/NAS. Zgodność protokołu synchronizacji jest ważniejsza niż dosłownie identyczny numer całej aplikacji.

## 8. Dystrybucja i testy

- Faktyczna nazwa repozytorium: **edwinkarolczyk/Edhome**, widoczność **publiczna**; marka **EDHOME**. `main` oficjalna, `beta` rozwój. Repozytorium nie wymaga osobnego tokenu OTA — podpisane Beta APK trafiają do publicznych GitHub Releases. Beta DEV może mieć inny package ID i odseparowane dane.
- Oficjalna wersja: raz po uruchomieniu sprawdzenie aktualizacji, opis zmian, aktualizuj / przypomnij później. Dystrybucja sklepowa używa mechanizmu Google Play.
- Beta DEV: duży napis „BETA”, kontrola metadanych co kilkanaście/kilkadziesiąt sekund tylko podczas działania, pobieranie nowego zatwierdzonego buildu, testy CI **przed** publikacją, sprawdzanie podpisu/integralności. Nie obiecywać bezgłośnej instalacji Androida ani natychmiastowych update'ów Play.
- Wersja aplikacji, build, schema danych i protokół synchronizacji osobno; kontrola zgodności, kopia przed migracją, testy offline/prywatności/finansów/QR/2 urządzeń. Przygotowanie Google Play od początku, rzeczywista publikacja po stabilizacji.

## 9. Otwarta lista przed implementacją

Wybór stosu Android, modeli danych/konfliktów Wi-Fi, audyt faktycznej wersji PayCheck i rozwiązań Trenera 2, dobór lokalnego silnika mowy, licencje bazy ogrodniczej, możliwości konkretnych urządzeń SUPLA, źródła katalogu kodów produktów, obsługa uprawnień Notification Listener, zasady przesyłania danych prywatnych, aktualne wymagania Google Play i polityka prywatności. Zadania wdrażać etapami wg ROADMAP.md.

---

## 10. Ustalenia szczegółowe z rozmowy — kontekst, granice i warianty

### Marka oraz repozytorium

- Nazwa wybrana świadomie: **EDHOME**; podpis **Idea by Edwin**. Wcześniej padały inne pomysły nazw (DOMORA, HOMIQ, DOMEXA, SWOJO, EDHOME, DOMNIO), ale **żaden nie zastępuje aktualnego wyboru**.
- Dwa wizualnie rozpoznawalne warianty ikony: Stable oraz Beta, **zaokrąglone narożniki**. Obie grafiki zostały zaakceptowane koncepcyjnie; zasoby graficzne Androida wymagają osobnego eksportu/przygotowania i nie należy twierdzić, że istnieją w tym repo.
- GitHub: **aktualnie** `edwinkarolczyk/Edhome` jest publiczne. Wcześniejsze `Edhime` i prywatna widoczność to wyłącznie zapis historyczny. Nazwa produktu zawsze **EDHOME**. Nie zmieniać automatycznie repozytorium.
- Proponowane, niezatwierdzone do publikacji identyfikatory: `com.edwinkarolczyk.edhome` dla oficjalnej aplikacji, `com.edwinkarolczyk.edhome.beta` dla oddzielnej Beta DEV.
- `main` stabilna, `beta` prace rozwojowe. Beta DEV może być zainstalowana obok stabilnej dzięki oddzielnemu identyfikatorowi **i własnym testowym danym**; testowy kanał Google Play może używać identyfikatora stabilnego, więc nie należy tych dwóch koncepcji mylić.

### Przepływy wymagające szczególnej spójności

1. **OC pojazdu i oszczędności:** przykładowy koszt 700 zł, odłożone 500 zł i cztery miesiące do terminu → 200 zł braku, sugestia 50 zł miesięcznie. Jeśli koszt, termin lub oszczędności się zmienią, sugestia przelicza się; odkładanie pieniędzy to nie zakup polisy ani podwójny wydatek.
2. **Zmiana kół:** jedna operacja aktualizuje komplet zamontowany na aucie, komplet odłożony do magazynu, serwis pojazdu i zadanie; rachunek w PayCheck pojawia się tylko gdy rzeczywiście jest wydatek. Osobne dane każdego kompletu/opony: rozmiar, DOT vs data zakupu, bieżnik/stan, okres i przebieg użytkowania, aktualne miejsce.
3. **Przedmiot w pudełku:** przeniesienie pudełka zachowuje numer i QR; wyszukanie jego zawartości wskazuje bieżące położenie pudełka. Pozostałe narzędzia mogą leżeć luzem w miejscu; brak pudełka nie oznacza braku lokalizacji.
4. **Pożyczenie:** odróżniaj „moja rzecz u kogoś” od „cudza rzecz u mnie”. Własność, posiadacz i aktualne miejsce to inne pola. Po zwrocie poprzednie miejsce to propozycja, a nie automatyczne przepisanie historii.
5. **Powiadomienie bankowe:** wpływ i wydatek rozpoznawać odrębnie, filtrować źródłowe aplikacje. Przykład wpływu 1,00 PLN jest tylko przykładem formatu wiadomości. Zapis wstępny czeka na zatwierdzenie; późniejsze księgowanie z wyciągu uzgadnia i deduplikuje ten sam transfer.
6. **Prywatność:** „Zajęty” to ujawnienie zajętości, nie prywatnej treści. Nie wysyłać na wspólny tablet całej prywatnej bazy transakcji z myślą, że jej przyciski będą ukryte.
7. **SUPLA i PV:** początkowo odczyt, nie sterowanie. Gdy lokalne API/protokół nie istnieje dla danego urządzenia, nie oznaczać integracji jako „offline”; grzałka CWU i inne urządzenia nie mogą być nieostrożnie rozłączane.
8. **Prace na dachu i PV:** zadanie „ocenić zaleganie śniegu” może być cykliczne/sezonowe, ale nie oznacza automatycznej rekomendacji samodzielnego wejścia na zaśnieżony dach. Usuwanie śniegu, jeśli wymagane, jako zadanie z odpowiednim wykonawcą i zasadami bezpieczeństwa.

### Decyzje, które pozostają propozycjami

- Dokładny stos Android, baza/ORM, metoda lokalnego wykrywania urządzeń i transport synchronizacji, przechowywanie kluczy, rozwiązywanie konfliktów po pracy offline.
- Katalog produktów po EAN/UPC i botaniczna baza offline: sprawdzić faktyczne źródła, zawartość, licencje, dostępność i możliwość przechowywania lokalnego; nie twierdzić, że OpenFarm/Growstuff są już zweryfikowane.
- Powiadomienia o nowych buildach Beta DEV: szybka kontrola **małego manifestu** tylko podczas działania i pobranie nowej kompilacji po opublikowaniu w CI; Android może wymagać zgody użytkownika na instalację. Aktualizacje przez Google Play podlegają mechanizmowi Play.
- Model okresowego remanentu, progi dla spiżarni, zasady stanu orientacyjnego i obsługa opakowań są szczegółowo opisane niżej jako nowy wymóg użytkownika i elementy do dopracowania.

## 11. NOWE USTALENIE — kreator okresowego remanentu spiżarni na tablecie

**WYMAGANIE UŻYTKOWNIKA:** w spiżarni EDHOME ma co ustawiony, cykliczny czas uruchamiać/proponować **remanent**. Kreator przechodzi **po kolei przez zapisane rzeczy**. Dla każdej użytkownik podaje faktyczną liczbę albo naciska **„Zgadza się / Dalej”**, jeśli liczba na półce jest zgodna z zapisem. Remanent ma być wygodny na przenośnym tablecie.

### Reguła uruchamiania

- Konfigurowalna częstotliwość: co tydzień, miesiąc, co N dni/tygodni/miesięcy, kwartalnie, przed sezonem lub ręcznie; osobno można wybierać pełną spiżarnię, wskazane miejsce/kategorię i remanent częściowy. **Żaden interwał nie jest narzucony jako jedyny.**
- Przypomnienie trafia do Czynności i kalendarza, ale **remanent jest procesem/kreatorem**, nie zwykłą jednozdaniową czynnością. Domownik wybiera dogodny termin; urządzenie nie rozpoczyna samowolnie sesji.
- Każda sesja ma stałe ID, datę, wykonawcę, wybrany zakres, postęp, status i zapamiętany stan oczekiwany/snapshot dla porównania; można przerwać i później wznowić.

### Ekran pojedynczego produktu

Przykład ilustracyjny, bez rzeczywistych danych gospodarstwa:

```text
EDHOME > Spiżarnia > Remanent
Produkt 7 z 38                         [pauza]
Ryż 1 kg
Miejsce: Spiżarnia / Górna półka
Zapisany stan: 4 sztuki

[ Zgadza się — 4 szt. ]     [ Zmień liczbę ]
[ Brak na półce ]          [ Pomiń / sprawdź później ]
```

- „**Zgadza się / Dalej**” potwierdza zapisany stan fizyczny i automatycznie przechodzi do następnej pozycji, bez konieczności pisania „4”.
- „**Zmień liczbę**” otwiera duży klawiaturowy edytor liczby (np. zapisano 4, faktycznie 2) i po zatwierdzeniu przechodzi dalej. Obsłużyć 0, ułamki tam, gdzie sensowne (kg/l), jednostki i różnicę między zamkniętym opakowaniem a ilością szacunkową.
- „**Brak na półce**” to jawne potwierdzenie zera, inne niż brak danych; „Pomiń” oznacza **niezweryfikowane**, nie zero.
- Zdjęcie i krótka notatka opcjonalne; dostępne „Wstecz” oraz wyszukanie/zeskanowanie produktu poza kolejnością. Można skanować kod kreskowy w trakcie remanentu.
- Jeśli produkt ma **stan nieokreślony** (np. narzędzie lub rzecz niemierzona w sztukach), kreator nie wymusza zmyślonej liczby: umożliwia „Jest / Nie ma / Nie sprawdzono”. Stan orientacyjny oznacza się jako taki.
- Produkt, którego nie ma w zapisanej liście, można dodać bez utraty postępu sesji. Nieznany EAN → lokalna karta produktu, jeśli brak katalogowego odpowiednika.

### Zakończenie i uzgadnianie rozbieżności

- Na końcu wyświetlić **raport różnic**: zgodne, mniej niż zapisano, więcej niż zapisano, brakujące, nowe, pominięte; rozdzielić wynik sesji od faktycznej korekty magazynu.
- **Nie zmieniać automatycznie ilości już przy samym przejściu kreatora**: pokaż proponowane korekty i dopiero po decyzji użytkownika zapisz nowe stany (możliwa korekta poszczególnych wierszy przed akceptacją). Zachować stary stan, nowy stan, różnicę, użytkownika i czas w historii.
- Wznowienie po zamknięciu aplikacji, braku sieci lub wygaszeniu tabletu; po wznowieniu nie dublować korekt. Po równoległych dodaniach/wyjęciach na innym urządzeniu wykryć konflikt, przedstawić zmiany i nie nadpisywać ich ślepo przestarzałym snapshotem.
- Remanent tylko wybranego zakresu **nie potwierdza** niewidocznych produktów/pozostałych miejsc. Pozycje pominięte pozostają do sprawdzenia; odrębna data **ostatniego fizycznego potwierdzenia** per produkt lub miejsce.
- Po zakończeniu sesja pozostaje w historii, wraz z czasem, zakresem, wykonawcą, wynikiem i wprowadzonymi korektami. Częstotliwość następnej sesji liczyć według jawnej reguły (od planu/od wykonania, zależnie od ustawienia).
- Dla produktów z progiem minimalnym spadek po zatwierdzonym remanencie **może zaproponować** dopisanie do wspólnej listy zakupów, a nie dopisywać bez zgody.

### Rozróżnienie trzech działań spiżarni

| Działanie | Co robi |
|---|---|
| Codzienny skan → „Dodaj” | Zwiększa stan po zatwierdzeniu |
| Codzienny skan → „Wyciągnij” | Zmniejsza stan po zatwierdzeniu |
| Remanent | Potwierdza faktyczny stan pozycji i, po osobnej akceptacji raportu, może skorygować różnice |

Wszystkie trzy używają **tej samej kartoteki produktu, jednostek, miejsc, uprawnień i historii operacji**. Wymóg „sam skan niczego nie zmienia” pozostaje aktualny.

## 12. Orientacyjna kolejność realizacji — uzupełnienie ROADMAP

- **0.1.0**: pulpit tabletu, przewijanie, nawigacja, lokalna baza i ustawienia.
- **0.2.0**: Czynności, reguły „co miesiąc / co N / przed zimą” i późniejsze przypinanie procesów remanentu do kalendarza.
- **0.3.0**: wspólna lista zakupów, lokalna kartoteka spiżarni i podstawowa ilość/jednostka.
- **0.4.0**: QR/EAN i codzienne Dodaj/Wyciągnij; **kreator remanentu ze snapshotem, Dalej, faktyczną liczbą, pominięciem, wznowieniem i raportem korekt**. Rozdzielić zadanie w kalendarzu od stanu sesji.
- **0.8.0**: synchronizacja telefon–tablet, deduplikacja operacji i konflikty równoległych zmian. Wcześniejszy remanent ma działać lokalnie również bez synchronizacji.
- Wersje i kolejność to **proponowana roadmapa**, nie obietnica gotowego terminu ani stan wdrożenia.

## 13. Dokumenty powiązane

- [ARCHITEKTURA.md](ARCHITEKTURA.md) — tożsamość, typowane relacje, bezpieczeństwo i migracje.
- [ROADMAP.md](ROADMAP.md) — etapy i kryteria odbioru.
- [USTALENIA_PRODUKTU.md](USTALENIA_PRODUKTU.md) — krótszy indeks funkcjonalności.
- Ten plik jest **głównym rejestrem zakresu i decyzji EDHOME**, aktualizowany przy kolejnych uzgodnieniach. Różnica między zakresem docelowym a faktycznie zrealizowaną funkcją jest obowiązkowa.


## 14. Diagnostyka beta vs stable — ustalenie z 19.09.2026

- **Beta DEV:** od pierwszej uruchamialnej kompilacji automatycznie zapisuj lokalny plik diagnostyczny obejmujący techniczne zdarzenia aplikacji, błędy, wersję i etapy operacji. Interfejs ma umożliwiać **kopiowanie całego dostępnego logu do schowka** oraz **eksport do pliku .txt**, który użytkownik może wkleić lub przesłać do analizy.
- **Stable:** diagnostyka beta musi być wyłączona na poziomie wariantu kompilacji — nie tylko ukryty przycisk. Nie instalować obsługi błędów diagnostycznych ani nie tworzyć pliku. Niezależny standardowy mechanizm crash reporting wymaga odrębnej decyzji i zgód.
- Minimalizacja danych: nie zapisywać PIN, haseł, danych bankowych, tekstu powiadomień, nazw produktów, pełnych obiektów ani tokenów. Zapisywać typ zdarzenia, ekran, status, wersję i klasę/ramki wyjątku bez jego potencjalnie prywatnej wiadomości. Nie pobierać całego systemowego Android logcat.
- Log pozostaje offline w prywatnym katalogu aplikacji; automatyczne przesyłanie poza dom/urządzenie **nie jest ustalone**. Kopiowanie i eksport wykonuje człowiek.
- Prototyp implementacji i instrukcję znajdziesz w [BETA_0_1_0.md](BETA_0_1_0.md); fakt istnienia kodu nie jest potwierdzeniem działającego APK, dopóki build nie przejdzie.


## 15. Zatwierdzona zmiana użytkownika — Beta bez PIN-u (20.09.2026)

- **Beta DEV uruchamia się bez PIN-u** i nie blokuje się ponownie po zejściu do tła ani po imporcie kopii. Dotychczasowy PIN nie jest usuwany, ale w Beta nie może zatrzymywać ekranu startowego.
- **Stable** nadal wymaga uwierzytelnienia PIN. Nie należy przenosić poluzowania zabezpieczenia z Beta na Stable.
- Wersja 0.2.7 dodaje lokalną listę domowników i opcjonalne przypisanie jednej osoby do czynności. Harmonogramy i automatyczny wybór terminu nie są jeszcze zrealizowane; etap 0.2.8 jest osobny.
- Odróżniać testy kodu/CI od rzeczywistej weryfikacji na telefonie.


## 16. Ustalenie wdrożeniowe — lokalny grafik i wyjątki (0.2.8)

- Każdy domownik ma niezależny tygodniowy grafik poniedziałek–niedziela. `Nie ustawiono` nie znaczy `Wolne`. Zmiany początkowe: 06–14, 14–22, 22–06, wolne; nie oznacza to automatycznej rotacji.
- Wyjątek na datę ma pierwszeństwo przed grafikiem tygodniowym. Usunięcie wyjątku przywraca tygodniową regułę bez naruszania innych dat.
- Nie przypisuj czynności ani terminu automatycznie na podstawie samego grafiku. W kolejnej wersji planer ma **proponować trzy terminy**, dopiero na jawnych danych i po potwierdzeniu człowieka.


## 17. Dostarczony pierwszy planer dat — 0.2.9-beta.1

- Propozycje wyłącznie dla jawnie wybranego wykonawcy, z grafikiem i wyjątkami w ciągu najbliższych 28 dni. Do trzech osobnych dat. `Nie ustawiono` nie staje się sztucznie dniem wolnym.
- Początkowa lista okien 08:00–21:00, poza godzinami zmian z zapasem i odpoczynkiem po nocnej. Zapis do bazy obejmuje **wybraną datę** dopiero po naciśnięciu `Zapisz`; orientacyjna godzina pozostaje niezapisywaną sugestią.
- Brak pełnej analizy kolizji z innymi zadaniami, wydarzeniami i dojazdem. Nie nazywać sugestii potwierdzoną dostępnością; to następny etap z przechowywaniem godzin i danymi kalendarza.


## 18. Inkrement 0.3.0 — lista zakupów offline

- Lokalna lista zakupów jest niezależna od stanów spiżarni; oznaczenie zakupione nie zwiększa automatycznie zapasu. Nie należy deklarować wspólnej synchronizowanej listy przed etapem Wi-Fi.
- Ilość opcjonalna: `NULL` jest nieokreśloną ilością, nie zerem; `0` i ujemne ilości są odrzucane. Jednostki na start: szt., kg, l; precyzja maks. trzy miejsca po przecinku w przeliczeniu na całkowite tysięczne.
- Pozycje można oznaczać i usuwać; nowe zakupy bez zmiany stałego układu dziewięciu kafelków. Etap 0.3 obejmuje także śmieci, obowiązki, minutniki i przypomnienia — te funkcje nie są automatycznie ukończone przez samą listę.


## 19. Inkrement 0.3.1 — odpady w jednym rejestrze Czynności

- Odpady są typowaną czynnością, nie oddzielnym zbiorem przypomnień. Mają frakcję, datę **wystawienia** (nie wymyśloną datę odbioru) i regułę powtarzania; działają w tym samym kalendarzu, historii i powiadomieniach.
- Potwierdzenie wystawienia oznacza wykonanie konkretnej czynności. Cykliczna wyznacza kolejny termin; nie powstaje drugi rekord przy każdym wykonaniu. Duplikat niezakończonej tej samej frakcji na tę samą datę jest blokowany.
- Powiadomienia wymagają jawnego włączenia i zgody Androida; początkowy wspólny alert jest około 09:00 na dzień terminu i zaległe. Indywidualne godziny, wyprzedzenie przed terminem i harmonogramy gminy to odrębny etap, nie deklaracja gotowej integracji.


## 20. Inkrement 0.3.2 — indywidualne przypomnienia

- Czynność, także odpady, może mieć własne HH:mm i wyprzedzenie 0/1/2/3/7 dni względem daty wykonania/wystawienia. Brak własnej godziny zachowuje wspólne przypomnienie około 09:00; czynność bez daty jest nadal dopuszczalna, ale nie ma indywidualnego alarmu.
- Cisza w tym inkremencie jest stała: 22:00–07:00. Ustawienie wieczorne przesuwa się na 21:00, wczesnoporanne na 07:00, a alarm spóźniony do ciszy nie powinien wybudzić użytkownika. Bez obietnic dokładnej minuty w Androidzie.
- Doręczenia wymagają zgody Androida i globalnego opt-in, mają osobną identyfikację daty/godziny; po wykonaniu zadania cyklicznego wyznaczany jest nowy termin i alarm.
- Backup v10 zachowuje ustawienia na zadaniach, import starszych kopii daje bezpieczne wartości domyślne. Beta bez PIN-u, Stable z PIN-em, a synchronizacja między telefonami później.


## 21. EDHOME — panel zarządzania energią i priorytetami nadwyżek PV (uzgodnienie)

**Cel:** jeden wspólny ekran pokazujący bilans energii domu oraz priorytety wykorzystania realnej nadwyżki. Panel stanowi część modułu Energia, a nie osobną niezależną aplikację.

### Widok bilansu (na telefonie i tablecie)

- Produkcja PV teraz i w czasie (kW/kWh), zużycie domu, pobór z sieci, oddawanie do sieci, bieżąca nadwyżka i zużycie własne — tylko dla wielkości rzeczywiście mierzonych; odróżnić pomiary od szacunków i prognoz. Gdy dane są niepełne, wyświetlić „brak danych”, nie pozorować dokładności.
- Zestawienie „dokąd trafia energia”: domowe odbiorniki, CWU, ogrzewanie/bufor, opcjonalny magazyn energii (jeśli istnieje), sieć. Nie twierdzić, że aplikacja zna udział każdego odbiornika bez licznika/czujnika danego obwodu.
- Wykresy godzinowe/dobowe/miesięczne, historia uruchomień odbiorników i oszczędności wyłącznie na podstawie jawnych założeń taryfy/kosztu; kWh, kW, zł oraz temperatura bufora i CWU mają właściwe jednostki i źródła.
- Czytelny wskaźnik: „Nadwyżka do dyspozycji”, „Ograniczenie mocy”, „Odbiorniki aktualnie aktywne”, „Dlaczego odbiornik nie został uruchomiony?”.

### Priorytety i reguły (konfigurowalne przez użytkownika)

1. Obciążenia podstawowe domu mają pierwszeństwo: panel nie może wyłączać przypadkowych urządzeń, żeby wymuszać autokonsumpcję.
2. Użytkownik może ustawić kolejność uprawnionych odbiorników, np. grzałka CWU → pompa ciepła/bufor w dopuszczalnym zakresie → inne dopuszczone urządzenia → pozostała nadwyżka do sieci. To **przykład**, nie narzucona konfiguracja ani polecenie grzania PC do 90°C.
3. Każdy odbiornik ma osobny tryb: tylko monitoruj / zaproponuj uruchomienie / automatyczne sterowanie (dopiero po technicznej weryfikacji i zgodzie); minimalna nadwyżka i czas utrzymania warunku, maksymalny pobór/moc, zakres godzin, priorytet, minimalny czas pracy i postoju, wyprzedzenie, histereza oraz ręczne wstrzymanie.
4. Odrębne ograniczenia: docelowy maksymalny eksport/pobór względem warunków przyłączenia, limity mocy urządzeń i obwodów, temperatury CWU/bufora, zabezpieczenia przeciwprzegrzaniu i awaryjne, minimalna rezerwa ciepłej wody, tryb sezonowy oraz możliwość ręcznego nadpisania. Ustawienie limitu eksportu nie zmienia formalnie zgłoszonej mocy instalacji PV i nie zastępuje wymogów operatora.
5. Zachowanie przy utracie internetu, LAN, SUPLA, telemetrii czy komunikacji: nie zgadywać aktualnych wartości i nie przełączać odbiorników na podstawie nieaktualnej nadwyżki; bezpieczny stan wynika z lokalnego sterownika i zabezpieczeń sprzętowych, nie tylko z aplikacji Android.
6. Najpierw **monitoring i rekomendacje**; zdalne sterowanie dopiero po identyfikacji rzeczywistego falownika, licznika, czujników, SUPLA i rodzaju sterowania urządzeń (np. wejście SG Ready vs odcinanie zasilania). Zwykły przekaźnik nie może bez analizy odcinać zasilania sprężarki pompy ciepła, pralki czy innego nieprzystosowanego urządzenia.
7. Ręczne decyzje i faktyczne załączenia trafiają do historii oraz wspólnego silnika zdarzeń. Połączenia z Czynnościami (np. sezonowy przegląd PV), kalendarzem, powiadomieniami oraz PayCheck (faktyczne rachunki, szacunki oddzielnie) bez dublowania kosztów.

### Architektura przyszłego sterowania

- EDHOME pokazuje bilans i pozwala edytować priorytety; **właściwy automat powinien działać w lokalnym, stale dostępnym sterowniku/bramce**, jeśli ma reagować wtedy, gdy telefon jest zamknięty. Tablet nie musi pozostawać włączony przez całą dobę.
- Adaptery źródeł pomiaru i wykonawców są rozdzielone: falownik/licznik/SUPLA → warstwa pomiarowa → algorytm priorytetów → bezpieczne polecenie → potwierdzony stan urządzenia. Identyfikator, czas pomiaru, źródło, dostępność, przyczyna decyzji i stan wykonania są jawne.
- Nie zakładać dostępności lokalnego API, pomiaru każdego obwodu ani możliwości płynnej regulacji mocy grzałek bez sprawdzenia konkretnego osprzętu. Prawdziwe sterowanie może wymagać dodatkowego licznika energii, sterownika lub czujników i fachowego projektu elektrycznego.

**Status:** ustalenie koncepcyjne. Panel, priorytety i sterowanie nie są automatycznie wdrożone przez dodanie tego opisu; kolejność poniżej w roadmapie.


## EDHOME UI Refresh 0.3.3-beta.2 — zatwierdzone przez Edwina

- Jedno gospodarstwo, te same 9 kafelków, zawartość i logika; cztery wizualne motywy: Neonowy, Naturalny, Pastelowy i Szklany (rozmiar i proporcje zrzutów jako punkt odniesienia, bez obietnicy dosłownej identyczności każdego piksela na wszystkich Androidach).
- Karty i kafelki z dużym zaokrągleniem, palety i kontrast dostosowane do motywu, graficzne ikony i czytelne dotykowe przyciski. Układ 3×3 przewijany pionowo.
- Kliknięcie otwiera moduł; **przytrzymanie wyświetla Edytuj / Przesuń**; przeciąganie za uchwyt zmienia kolejność. Edycja prezentacji pozwala zmienić podpis i kolor kafelka, ale **nigdy powiązanie ID/modułu ani dane**. Nie usuwamy systemowych 9 kafelków.
- Po ponownym uruchomieniu zachowuje się kolejność, motyw i wygląd. Kopia JSON uwzględnia edycje wizualne. Wersja bazy SQLite pozostaje v10; Beta bez PIN-u, Stable z PIN-em, repo i kanał aktualizacji bez zmian.
- Obrazy koncepcyjne są inspiracją, nie zrzutem z uruchomionego Androida; wykonanie UI w aplikacji wymaga kompilacji i osobnego testu na telefonie. Dalsze 0.3.4: minutniki urządzeń.

## 22. Integracja SUPLA — Cloud API, OAuth2 i broker MQTT (doprecyzowanie na podstawie zrzutów 21.09.2026)

**Zrzuty pokazują dostępne na koncie użytkownika:** Swagger/OpenAPI SUPLA Cloud, formularz rejestracji aplikacji OAuth, broker MQTT włączony z serwerem, TLS i przyciskiem generowania hasła, a także katalog integracji. To potwierdza istnienie tych dróg integracyjnych, **nie** dostępność konkretnego pomiaru ani działającego połączenia EDHOME. Nie zapisywać w repo identyfikatora konta MQTT, haseł, tokenów, client secret ani dokładnych danych urządzeń użytkownika.

### Trzy rozdzielone kanały

| Kanał | Do czego w EDHOME | Ważne ograniczenie |
|---|---|---|
| SUPLA Cloud API / OpenAPI | Odkrywanie uprawnionych kanałów i lokalizacji, stany, pomiary/historia o ile endpointy i dany typ kanału je oferują | Wymaga internetu i ważnego uwierzytelnienia; serwer instancji SUPLA musi zgadzać się z kontem |
| OAuth2 SUPLA | Połączenie konta z EDHOME z odpowiednimi zakresami uprawnień i odwoływaniem dostępu | Nie umieszczać client secret/PAT w APK; użyć właściwego przepływu dla natywnej aplikacji z PKCE, jeśli wspierany, z poprawnym redirect URI; zakres minimalny, najpierw odczyt |
| Broker MQTT SUPLA | Aktualizacje stanów/pomiarów w miarę ich publikacji dla uprawnionych kanałów | Pokazany broker jest adresem **chmurowym**, a nie lokalnym MQTT/LAN; wymaga internetu oraz bezpiecznej obsługi konta, TLS i ponownego połączenia |

SUPLA Apps/katalog integracji jest wykazem rozwiązań, **nie** dodatkowym mechanizmem automatycznie zapewniającym dostęp do urządzeń. Swaggerowe operacje zmieniające stan mogą działać na prawdziwym koncie; nie używać „Wypróbuj” na endpointach zapisu/sterowania do testu odczytu.

### Ekran konfiguracji EDHOME → Integracje → SUPLA

- Status: rozłączono / łączenie / połączono / brak uprawnień / dane nieaktualne. Wybór instancji SUPLA, połączenie konta i ewentualny lokalny adapter po odrębnej konfiguracji.
- Pokaż **tylko uprawnione kanały** i umożliw ich mapowanie do trwałych obiektów EDHOME, np. licznik energii, temperatury CWU, bufor, obwód grzałki, pompa ciepła, pozostałe odbiorniki. Żaden sensor nie jest obiecywany, jeśli nie istnieje na konkretnym koncie/urządzeniu.
- Dane źródłowe rozdzielone od wartości obliczanych; prezentować wartość, jednostkę, czas pomiaru, źródło i status jakości. MQTT nie zastępuje API, jeśli potrzebny jest odczyt historyczny lub odkrywanie kanałów.
- Początkowo **tylko odczyt i bilans**. Sterowanie jako oddzielny etap po mapowaniu zdolności konkretnych kanałów, zgodzie użytkownika i weryfikacji zabezpieczeń. Nie traktować kanału „włącz/wyłącz” jako pozwolenia na odcinanie zasilania PC lub urządzeń nieprzystosowanych.
- Chmura SUPLA nie spełnia sama z siebie założenia pełnej pracy offline: przy braku internetu wyświetlić ostatni pomiar z datą i oznaczeniem „nieaktualny”, bez automatyzacji na nieświeżych danych. Lokalność badać osobno dla konkretnego modułu/protokołu.
- Nie udostępniać konta użytkownika ani danych SUPLA wszystkim domownikom przez samo ukrycie UI; uprawnienia gospodarstwa, osobna obsługa tokenów, szyfrowanie i wylogowanie/cofnięcie zgody. Dziennik diagnostyczny beta nie może zawierać sekretów.
- Repozytorium EDHOME może być publiczne; **żadne dane uwierzytelniające SUPLA ani identyfikatory konta nie trafiają do kodu, dokumentacji ani commitów**.

### Plan akceptacyjny

1. Sprawdzić aktualną dokumentację SUPLA i rzeczywiste typy kanałów dostępne na koncie; wybrać minimalne tylko-do-odczytu uprawnienia.
2. Połączyć konto w bezpiecznym flow; pokazać listę kanałów bez uruchamiania urządzeń.
3. Zmapować rzeczywiste dane energii, temperatur i stanów, oznaczyć brakujące pomiary.
4. Dodać opcjonalne MQTT i odporność na zerwanie łączności; rozróżnić chmurę od lokalnego adaptera.
5. Dopiero później rozważyć jawnie dopuszczone polecenia i priorytety nadwyżek PV.

**Status:** kierunek i wymagania zaakceptowane; ekran SUPLA nie jest potwierdzoną działającą integracją w APK.


## Miejsca 0.3.4 — własna hierarchia lokalizacji

- Nazwy nadaje użytkownik, osobno od rodzaju: np. Dom → Kuchnia → Szafka → Półka 1. Rodzaj jest opcjonalnym opisem (dom, pomieszczenie, regał, półka itp.), a nie sztywną listą nazw. Miejsca mogą być zagnieżdżane; nazwy powtarzają się w różnych gałęziach, lecz nie pod tym samym rodzicem.
- Każde miejsce ma własne ID i może mieć ikonę z lokalnej biblioteki. Edycja/przenoszenie nie zmienia ID ani przypisanych czynności. W ich widokach prezentować pełne ścieżki, by rozróżnić jednakowe nazwy półek.
- Przenoszenie do siebie lub potomka jest zabronione. Usuwanie miejsca z podmiejscami wymaga najpierw przeniesienia/usunięcia dzieci; nie kasować całej gałęzi przypadkiem. Usunięcie pojedynczego miejsca nie usuwa czynności ani historii, tylko usuwa ich opcjonalne przypisanie miejsca.
- SQLite v11 i backup JSON v11 przechowują parent_id i icon; starsze kopie importują swoje miejsca na poziom główny i dostają neutralną ikonę. Motyw nadal jest lokalny dla instalacji EDHOME, a nie dla domownika.


## 23. Obowiązujące decyzje po formularzu 30 pytań — 21.09.2026

**[Pełny rejestr 30 decyzji i nierozstrzygniętych pytań](DECYZJE_2026-09-21_FORMULARZ_30.md) ma pierwszeństwo przed wcześniejszymi, sprzecznymi wariantami koncepcyjnymi w tym pliku.** Zapis decyzji oznacza zakres do wdrożenia, nie potwierdzenie ukończenia w aplikacji.

- Wszystkie sześć motywów. Dowolna liczba kafelków (dodawanie/ukrywanie), małe i podwójne kafelki, przeciąganie całego kafelka po przytrzymaniu.
- **Tablet = przełączany tryb tej samej aplikacji EDHOME**, z uproszczonym/współdzielonym zestawem funkcji i prawidłowymi uprawnieniami; nie oddzielny program lub APK. Użytkownik wybiera tryb/układ.
- Czynności: szybkie/zaawansowane dodawanie, data/przedział/sezon, liczenie terminu od planu lub wykonania per czynność. Kalendarz najpierw łączyć z innymi modułami.
- Nazwa **i rodzaj** miejsca definiowane przez użytkownika. Pudełka mogą być w innych pudełkach, z niezmiennym ID/QR i bez cykli hierarchii.
- Dwa tryby skanera: **Dodaj produkty do spiżarni** (seryjny odczyt i legalna otwarta baza danych produktów, gdy brak lokalnej karty) oraz **Wyciągnij** (pokaż stan, proponuj -1 szt./paczkę, automatycznie zatwierdzaj po konfigurowalnej ciszy bez zmian; zmiana ilości restartuje odliczanie; powtórny odczyt tego samego kodu w trakcie odliczania **ignoruj**). Zapewnić Anuluj/Cofnij, historię i idempotencję; starsza reguła bezwzględnego ręcznego potwierdzania każdej pozycji **nie obowiązuje** w tym ustalonym trybie.
- Zakupy mogą oczekiwać jako **„Do dodania do [wybrane miejsce]”**. Miejsce docelowe określane **dla pozycji zakupu**, nie jako stała właściwość produktu; „Spiżarnia” to przykładowa edytowalna lokalizacja.
- Konflikty remanentu: ponownie sprawdzać zmienione pozycje, pozostały postęp zachować. Powiadomienia per domownik i rodzaj; niewykonane zadanie stale widoczne do potwierdzenia.
- Kopie: JSON ręczny, lokalna automatyczna i NAS. Aktualizacje użytkownik ocenia jako działające z zachowaniem danych, co nie zastępuje testów migracji.
- Profil osobisty na telefonie i tryb wspólny tabletu; wkłady osób i sumy wspólne w budżecie; PayCheck **proponuje** transakcję do akceptacji. Połączenia wymagać dla konkretnie wskazanych operacji: transferów między urządzeniami, sterowania SUPLA/energią, rozliczeń wspólnego budżetu; pozostały tryb offline nie jest przez to anulowany.
- **PayCheck pierwszy duży kolejny moduł**. SUPLA najpierw lista urządzeń i stanów, automatyka PV etapami. Rozwój fundamentu równolegle z funkcjami. Największa obawa: brak powiązań między modułami — każdy inkrement musi wykazać wspólne ID, właściciela danych, relacje i testy integracji.

**Otwarte bez decyzji:** dziedziczenie widoczności czynności z miejsc nadrzędnych (pytanie 13), model priorytetów PV (28), dokładny czas odliczania i zachowanie skanera dla innego kodu, szczegółowa lista ograniczeń tabletu i precyzyjny zakres operacji wymagających połączenia. Nie wypełniać ich domysłami.


## 24. Spiżarnia — koszt produktu i historia cen (ustalenia potwierdzone 21.09.2026)

**Status: trzy decyzje użytkownika zatwierdzone 21.09.2026; funkcja planowana, NIE wdrożona w APK.** Ta sekcja rozdziela uzgodnioną intencję („ile coś kosztowało, z historią w spiżarni”) od poniższych rekomendacji modelu danych. W tym czacie wolno zmieniać specyfikację i roadmapę na `beta`; nie modyfikować kodu, APK ani `main` bez odrębnego polecenia.

### Cel i przykład interfejsu

- Na karcie produktu można zobaczyć **ostatnią znaną cenę**, opcjonalnie **typową cenę za porównywalną jednostkę**, historię rzeczywistych zakupów z datą/sklepem/ilością/ceną oraz — jeśli dane wystarczają — **szacunkową wartość aktualnego zapasu**. Użytkownik nie musi podawać ceny przy każdym skanie „Wyciągnij”.
- Dane o cenie są **opcjonalne**. Brak ceny to „nieznana”, **nie 0 zł**. Ostatnia cena zakupu nie jest aktualną ceną sklepową ani gwarancją wartości tego, co pozostało na półce.
- Widok może pokazywać np. „Ryż 1 kg · 4 opakowania · ostatnio 6,49 zł/op. · kupiony 12.09 · historia cen”; wartość zapasu tylko jako oznaczony szacunek, gdy część przyjęć nie ma cen.
- W historii zachować cenę faktycznie zapłaconą, wraz z ilością i datą. Późniejsza edycja nazwy, katalogu, EAN lub ceny referencyjnej **nie przelicza historycznych paragonów**.

### Trzy odrębne rodzaje zapisu

1. **Kartoteka produktu/wariantu:** trwały ID, nazwa i ewentualnie EAN, rozmiar opakowania, jednostka oraz ostatnia znana cena/podgląd. Wariant „ryż 1 kg” i „ryż 500 g” może należeć do tej samej rodziny, ale nie mieszać cen za opakowanie.
2. **Zakup / przyjęcie do magazynu:** ID zdarzenia, ID produktu, data (czas lokalny + znacznik techniczny), ilość i jednostka, cena jednostkowa/łączna rzeczywiście zapłacona, waluta, opcjonalnie sklep, promocja/rabat, dokument/zdjęcie paragonu, domownik i docelowe miejsce. **Zapis zakupu** i **fizyczne zwiększenie stanu** to różne fakty: zakup z listy może oczekiwać jako „Do dodania do [miejsce]” i dopiero po potwierdzeniu wejść do spiżarni. Własne uprawy, prezenty i korekty remanentu nie są zakupami po cenie 0 zł.
3. **Ruch magazynowy:** dodanie, wyciągnięcie, przeniesienie, zwrot, strata, korekta remanentu. Zapisuje ilość i źródło; zwykłe wyjęcie produktu **nie tworzy nowego wydatku finansowego**. Dla produktu bez historii ceny ruch pozostaje w pełni możliwy.

### Przepływy użytkownika (proponowane domyślne zachowanie)

- **Lista zakupów → kupione:** można wpisać faktyczną ilość, cenę i sklep dla danej pozycji; cena jest opcjonalna. Pozycje mogą zostać oznaczone jako kupione, lecz nie zmienia to automatycznie stanu spiżarni; pozostają „Do dodania do [miejsce]”. Gdy przyjęcie jest potwierdzone, wskazać ID konkretnego zakupu, bez tworzenia drugiego zakupu.
- **Skaner Dodaj:** dopisuje potwierdzoną ilość i miejsce; może użyć istniejącej oczekującej pozycji zakupu lub utworzyć samo przyjęcie bez ceny. Nie wymuszać formularza ceny w szybkim skanowaniu.
- **Skaner Wyciągnij:** zmniejsza ilość, zapisuje historię, bez zmiany ceny zakupu i bez księgowania nowej transakcji.
- **Remanent:** korekta stanu i historia rozbieżności nie udają nowego zakupu ani sprzedaży; mogą zmienić wartość zapasu wyłącznie po świadomym zatwierdzeniu sesji.
- **Edytuj cenę:** korekta błędu konkretnego zakupu ma być audytowalna; odrębna „cena referencyjna” służy podpowiedzi i nie nadpisuje przeszłości.

### Historia cen i wycena — nie mylić pojęć

- „Ostatnia cena” = ostatni **rzeczywisty, datowany zakup tego samego wariantu**, z daną walutą. „Najniższa/najwyższa/średnia” liczyć tylko z porównywalnych jednostek i danych oznaczonych jako faktycznie zapłacone, nie z szacunków/promocyjnych etykiet bez zakupu.
- Do porównywania różnych opakowań pokazywać cenę za kg/l/szt. **tylko po znanym przeliczeniu** rozmiaru opakowania; nie porównywać bezpośrednio ceny za paczkę 500 g z paczką 1 kg. Ułamkowe stany kg/l i części opakowań muszą mieć jawne jednostki.
- Historia promocji/rabatów: cena końcowa po rabacie (dla pozycji), opcjonalnie cena przed rabatem, kod dokumentu i źródło kwoty; nie dopisywać domyślnego sklepu lub rabatu.
- Szacowana wartość tego, co aktualnie jest na stanie, jest **informacją magazynową, nie saldem konta ani nowym wydatkiem**. Gdy wiadomo, które partie pozostały, można używać ich kosztów; przy braku powiązań proponowany fallback: średnia ważona znanych przyjęć, z jasną etykietą „szacunek / część bez ceny”. Metodę wyceny (partie, FIFO czy średnia) i politykę mieszanego stanu ustalić przed implementacją; **nie pokazywać fałszywie dokładnej sumy**. Przeterminowanie, ubytki i podarowanie także nie tworzą drugi raz kosztu zakupu.

### Powiązanie z PayCheck i prywatność

- Docelowo jeden paragon/zakup zbiorczy może zawierać **wiele pozycji spiżarni**, ale być powiązany z **jedną rzeczywistą transakcją finansową**, nie osobną transakcją za każdą paczkę. Nie wymuszać identyczności sumy bez uzgodnienia rabatów, zwrotów, opłat i artykułów spoza spiżarni; różnicę pokazać użytkownikowi do wyjaśnienia.
- Zakup zapisany ręcznie, później rozpoznany z bankowego powiadomienia i wyciągu bankowego jest **tym samym wydatkiem**: łączyć po stabilnym ID/uzgodnieniu i potwierdzeniu, bez podwójnego księgowania.
- **Decyzja Edwina:** tablet we wspólnym trybie pokazuje **wszystkie zapisane ceny produktów oraz historię ich cen/zakupów**, także gdy zakup został opłacony prywatnie, ale **bez danych kont, kart, metody płatności, sald i osobistych transakcji PayCheck**. Ceny są udostępnioną częścią kartoteki spiżarni; prywatne rekordy finansowe pozostają chronione i nie są kopiowane do wspólnego cache tabletu. Nie ujawniać przez szczegóły zakupów prywatnych paragonów/dokumentów z danymi bankowymi.
- Cena i paragon nie trafiają do logów Beta; dokumenty oraz historia uwzględniają eksport/backup, retencję i osobę dokonującą korekty.

### Kryteria projektowe i pytania otwarte

1. Jeden produkt kupiony kilkukrotnie po różnych cenach ma kilka niezmiennych zapisów cen; jego karta pokazuje ostatnią cenę, nie nadpisuje starej.
2. Skanowanie bez ceny, darowizna, ubytek i remanent pozostają legalnymi operacjami; 0 zł nie zastępuje braku ceny.
3. Zakupy nie dublują się między listą zakupów, przyjęciem, historią ruchów i PayCheck.
4. Różne rozmiary paczek, promocje, częściowo zużyte opakowania oraz partie bez ceny nie generują pozornej dokładności raportów.
5. Po synchronizacji Wi-Fi zdarzenia mają identyfikator, źródło i idempotencję; równoległe zmiany i korekty ceny nie niszczą historii.

**Zatwierdzone przez Edwina — 21.09.2026:** (1) cena jest **opcjonalna przy oznaczaniu pozycji listy jako „kupione”** i nigdy nie blokuje skanowania/wyciągania; (2) wspólny tablet pokazuje **wszystkie ceny produktów**, ale **żadnych danych kont ani osobistej księgowości PayCheck**; (3) **wartość całego zapasu dopiero w późniejszym etapie**, po historii zakupów i cen. Nadal otwarta jest szczegółowa metoda późniejszej wyceny (partie/FIFO/średnia) i polityka niepełnych danych; nie zastępować nieznanej ceny zerem.
