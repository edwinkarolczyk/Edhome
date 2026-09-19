# EDHOME — pełny rejestr ustaleń koncepcyjnych

> Status: **wymagania i założenia**, a nie gotowe funkcje. Marka: **EDHOME — Idea by Edwin** (wcześniej roboczo „Ranczo”). Na razie nie przenosimy kodu innych aplikacji. Dokument opisuje ustalenia z rozmowy, a roadmapa osobno określa kolejność wdrożenia.

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

- Prywatne repozytorium GitHub ma obecnie nazwę **Edhime**; nazwa marki **EDHOME**. `main` oficjalna, `beta` rozwój. Beta DEV może mieć inny package ID i odseparowane dane.
- Oficjalna wersja: raz po uruchomieniu sprawdzenie aktualizacji, opis zmian, aktualizuj / przypomnij później. Dystrybucja sklepowa używa mechanizmu Google Play.
- Beta DEV: duży napis „BETA”, kontrola metadanych co kilkanaście/kilkadziesiąt sekund tylko podczas działania, pobieranie nowego zatwierdzonego buildu, testy CI **przed** publikacją, sprawdzanie podpisu/integralności. Nie obiecywać bezgłośnej instalacji Androida ani natychmiastowych update'ów Play.
- Wersja aplikacji, build, schema danych i protokół synchronizacji osobno; kontrola zgodności, kopia przed migracją, testy offline/prywatności/finansów/QR/2 urządzeń. Przygotowanie Google Play od początku, rzeczywista publikacja po stabilizacji.

## 9. Otwarta lista przed implementacją

Wybór stosu Android, modeli danych/konfliktów Wi-Fi, audyt faktycznej wersji PayCheck i rozwiązań Trenera 2, dobór lokalnego silnika mowy, licencje bazy ogrodniczej, możliwości konkretnych urządzeń SUPLA, źródła katalogu kodów produktów, obsługa uprawnień Notification Listener, zasady przesyłania danych prywatnych, aktualne wymagania Google Play i polityka prywatności. Zadania wdrażać etapami wg ROADMAP.md.
