# EDHOME — pełny rejestr ustaleń koncepcyjnych

> **Główne źródło ustaleń EDHOME — Idea by Edwin.** Wymagania nie są automatycznie gotowymi funkcjami. Stan implementacji i kolejność dostaw: [ROADMAP.md](ROADMAP.md) oraz [README.md](../README.md). Rzeczywiste repo to `edwinkarolczyk/Edhome` (obecnie **publiczne**), a prace rozwojowe odbywają się wyłącznie na `beta`; `main` bez zmian. Beta 0.2.6 dodaje priorytet i czas czynności oraz SQLite v5. Dawne wpisy „Edhime”/„prywatne repo” poniżej są opisem historycznych ustaleń, nie aktualną konfiguracją GitHub. Nie kopiujemy automatycznie kodu innych aplikacji.

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
