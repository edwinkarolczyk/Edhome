# EDHOME Desktop Beta

Desktopowy klient EDHOME dla Windows.

## Zasada technologiczna

Desktop ma być **funkcjonalny, wygodny i zgodny z EDHOME**, ale nie ma obowiązku pozostać aplikacją javową. Java/Swing jest obecną implementacją, a nie ograniczeniem architektury. Jeżeli inna technologia zapewni lepszą obsługę Windows, urządzeń, aparatu, NFC, drukowania lub interfejsu, klient Desktop może zostać przeniesiony bez zmiany kontraktu danych Android ↔ PC.

Sprzęt dostępny przy komputerze jest częścią funkcji Desktop. EDHOME PC ma obsługiwać skanowanie, gdy użytkownik podłączy odpowiednie urządzenie:
- skaner kodów/QR USB lub bezprzewodowy działający jak klawiatura,
- QR i kody z obrazu/zrzutu ekranu,
- czytnik NFC zgodny z Windows PC/SC,
- bezpośrednią kamerę/webcam do QR, jeśli jest dostępna.

NFC nie jest tylko lokalnym dodatkiem PC. Powiązania tagów z rzeczami, pudełkami, miejscami, produktami i pojazdami są częścią synchronizowanych danych EDHOME.

## Zasada nadrzędna: wspólne dane, różna ergonomia

Android i Desktop pracują na **tym samym modelu danych i tych samych regułach domenowych**, ale nie muszą mieć identycznego interfejsu.

Desktop jest centrum szybkiego tworzenia i porządkowania: obsługuje klawiaturę, mysz, zaznaczanie wielu rekordów, operacje zbiorcze, drzewka, zależności i seryjne dodawanie. Android pozostaje interfejsem terenowym: skanowanie QR/NFC, zdjęcia, szybkie przenoszenie, Start/Stop pracy, potwierdzenia i powiadomienia.

Oba interfejsy muszą zachowywać:
- ten sam stan danych po synchronizacji,
- te same reguły walidacji i bezpieczeństwa,
- zgodne zależności między modułami,
- ochronę przed konfliktami równoczesnej edycji,
- pełną historię i identyfikatory rekordów.

Funkcja może mieć inną formę na PC i telefonie, jeżeli rezultat danych i reguły biznesowe pozostają zgodne.

## Instalacja Windows

**Zalecany sposób:** pobierz i uruchom `EDHOME-Desktop-Beta-Setup.exe`. Instalator zawiera wymagany runtime, tworzy wpis EDHOME w menu Start i może utworzyć skrót na pulpicie. Użytkownik nie musi instalować Javy.

`EDHOME-Desktop-Beta-Windows.zip` pozostaje wersją portable i pakietem aktualizacji. Po wypakowaniu plik `EDHOME-Desktop-Beta.exe` musi pozostać razem z folderami `app` i `runtime`. Skopiowanie samego EXE powoduje błąd `Failed to launch JVM`.

Pipeline wydania uruchamia `EDHOME-Desktop-Beta.exe --smoke-test` z gotowego app-image przed utworzeniem i publikacją instalatora, aby wykryć problemy z uruchomieniem JVM.

## Aktualny stan Desktop — 0.7.0.95

Ustawienia Desktop są celowo uproszczone do trzech codziennych operacji: połączenie telefonu przez QR, sprawdzenie połączenia PC ↔ telefon oraz pobranie logów telefonu + Desktop na Pulpit. Dane adresu i token parowania pozostają zapisane wewnętrznie; użytkownik nie musi ich ręcznie edytować. Automatyczna synchronizacja w obie strony pozostaje aktywna. Przy pierwszym parowaniu QR na Windows Desktop może jednorazowo poprosić o zgodę UAC i dodać regułę TCP 45824 ograniczoną do lokalnej podsieci; listener parowania nasłuchuje na lokalnych interfejsach, aby poprawnie działać także przy kilku kartach sieciowych.

Aktualna linia Desktop obsługuje:
- przy starcie otwiera się zmaksymalizowany na cały dostępny pulpit Windows, z zachowaniem paska zadań i standardowych kontrolek okna,
- osobny interfejs Windows,
- połączenie Android ↔ PC przez Wi-Fi/LAN,
- parowanie kodem i QR,
- lokalny cache danych,
- automatyczne pobieranie zmian,
- zapis zmian PC → telefon,
- kontrolę konfliktu snapshotu,
- aktualizację Desktop jednym przyciskiem,
- autostart z Windows i start zminimalizowany,
- skaner USB/klawiaturowy,
- QR/kody bezpośrednio z kamery/webcam,
- QR/kody z obrazu lub zrzutu ekranu,
- NFC przez Windows PC/SC,
- trwałe, synchronizowane powiązania tagów NFC z obiektami EDHOME,
- Desktop 0.6.0.64 pokazuje akcję `NFC` bezpośrednio przy Rzeczy, Pudełku, Miejscu, Produkcie i Pojeździe; można przypisać tag z czytnika PC/SC, zmienić tag i usunąć powiązanie, a usunięcie obiektu czyści jego NFC bez osieroconych UID,
- akcje po QR/NFC: otwarcie, edycja, przeniesienie, wypożyczenie/zwrot i etykieta QR tam, gdzie operacja ma zastosowanie,
- zapamiętywanie domyślnej akcji dla konkretnego QR/NFC,
- pojedyncze i zbiorcze etykiety QR dla rzeczy, pudełek i miejsc,
- formaty etykiet 40 × 30 mm, 50 × 30 mm, 70 × 50 mm, A4 zbiorczo i własny rozmiar,
- podgląd etykiet, eksport do PDF i bezpośredni wydruk,
- zapamiętywanie drukarki etykiet,
- PayCheck: lokalny import PDF/CSV/XLSX, kolejka dowodów bankowych, deduplikacja i ręczne dopasowanie,
- PayCheck: osobny „Budżet przyszły” z kreatorem ręcznym i importem PDF/XLSX/CSV/TXT, rozpoznawaniem kwot i dostawców (m.in. TAURON/woda/banki), ratami/kredytami, cyklami, datą końcową/liczbą rat oraz prognozą 12 miesięcy zestawioną z potwierdzonymi transakcjami,
- import do „Budżetu przyszłego” działa teraz w trybie **najpierw podgląd, potem decyzja**: dla rozpoznanego wyciągu bankowego pokazuje przed kreatorem datę, opis, kwotę, typ i bank oraz pozwala `Dodaj do budżetu / Pomiń / Pomiń resztę pliku / Zakończ import`; kwota i data z banku są przekazywane do kreatora bez ponownego przepisywania,
- Desktop 0.6.0.61 używa natywnego okna wyboru pliku Windows dla importów PayCheck; podgląd ma pole `Sklep / odbiorca`, pamięć lokalnych reguł sklep→kategoria oraz podpowiedzi dla popularnych sklepów, stacji, subskrypcji i dostawców mediów,
- słownik kategorii obejmuje m.in. Subskrypcje, Media, Paliwo, Transport, Zdrowie, Odzież, Restauracje, Rozrywkę, Edukację, Dzieci, Ubezpieczenia, Kredyty i raty, Świadczenia, Oszczędności/inwestycje i Transfery,
- wspólny PayCheck ma `Usuń wpis`; przy usunięciu pozycji uzgodnionej z bankiem dowód wraca do kolejki, a powiązany koszt pojazdu pozostaje poza PayCheck.\n- Desktop 0.6.0.62 przebudowuje główny PayCheck w dashboard zamiast generycznych dużych kart: saldo, wpływy miesiąca, wydatki miesiąca i liczba oczekujących są widoczne na górze, niżej znajduje się zwarta lista transakcji z bezpośrednią zmianą kategorii i usuwaniem.\n- główny PayCheck ma zakładki `Transakcje / Sklepy / Kategorie`; statystyki sklepów liczą rozpoznane wydatki z ostatnich 12 miesięcy i bieżącego miesiąca, a zakładka Kategorie wykorzystuje rozszerzony wspólny słownik kategorii.\n- Desktop 0.6.0.63 zmienia `Usuń wpisy` PayCheck na wielokrotny wybór z `Zaznacz wszystko`; jedno potwierdzenie usuwa wszystkie zaznaczone pozycje i poprawnie ponownie otwiera powiązane dowody bankowe.\n- możliwość usunięcia obejmuje wszystkie główne rekordy edytowalne w Desktopie: Czynności/Zadania (także Dzisiaj i Odpady), Spiżarnię, Zakupy, Magazyn, Pojazdy, Timery i Miejsca; zależności są czyszczone lub blokowane z komunikatem zamiast pozostawiania osieroconych danych.\n- cele PayCheck można usuwać, a prywatny PayCheck obsługuje wielokrotne usuwanie zaznaczonych wpisów; prywatny sejf akceptuje pełny nowy słownik kategorii.
- plan „Budżet przyszły” jest celowo lokalny i wersjonowany w `~/.edhome/paycheck-budget-plan.json`; nie księguje planu jako wydatku i nie zmienia salda przed faktycznym potwierdzeniem,
- bezpieczny parser VeloBank PDF oraz mBank CSV/XLSX; nieznane układy PDF są odrzucane zamiast zgadywane,
- zachowywanie oryginalnych PDF bankowych lokalnie na komputerze,
- tworzenie brakujących wspólnych operacji z wyciągu jako oczekujących — bez zmiany salda przed potwierdzeniem,
- historia importów bankowych i obsługa wielu etykiet bank/konto,
- analiza PayCheck z porównaniem 12 miesięcy i kategoriami,
- wspólne cele oszczędnościowe,
- masowa zmiana kategorii transakcji,
- prywatny PayCheck jako osobny zaszyfrowany sejf zgodny z formatem przenośnej kopii Androida,
- import/eksport zaszyfrowanej kopii prywatnego PayCheck,
- prywatne operacje bankowe jako oczekujące, z idempotencją,
- drukowanie kalendarza, zadań i czynności w A4 z podglądem,
- podstawowe widoki: Pulpit, Dzisiaj, Kalendarz, Zadania, Czynności, Magazyn, Spiżarnia, Zakupy, PayCheck, Pojazdy, Odpady, Timery, Energia, SUPLA, Miejsca, Skaner i Ustawienia.

Desktop **nie jest read-only**. Prywatny PayCheck pozostaje celowo poza zwykłym snapshotem Android ↔ PC; przenoszenie prywatnych finansów odbywa się wyłącznie przez zaszyfrowany format sejfu.

## Zatwierdzony zakres Desktop — decyzja użytkownika

Zakres zatwierdzony dla QR/drukowania, skanowania, importów bankowych, analizy PayCheck, prywatnego PayCheck oraz drukowania kalendarza i zadań jest wdrożony w linii 0.6.0.54. Regresje tego zakresu są chronione testem `tests/check_desktop_approved_scope.py`.

### Ograniczenia importu PDF banków
Automatyczne rozpoznanie banku nie oznacza zgadywania układu dokumentu. W 0.6.0.49 bezpiecznie obsługiwany jest tekstowy PDF VeloBanku. mBank jest obsługiwany przez CSV/XLSX. Zwykły CSV jest obsługiwany, jeśli zawiera stabilny identyfikator transakcji i wymagane kolumny. PDF innych banków jest odrzucany, dopóki nie ma jawnego parsera dla ich układu.

### Szybkie zadania i kalendarz Desktop
- kreator szybkiego dodawania serii zadań działa w trybie klawiaturowym,
- przed startem użytkownik wybiera, o które pola kreator ma pytać przy każdym zadaniu,
- dostępne pytania: szacowany czas, termin, priorytet, wykonawca i miejsce,
- pola pominięte dostają wartości domyślne: 30 min, bez terminu, normalny priorytet, bez wykonawcy i bez miejsca,
- po zapisaniu zadania kreator natychmiast pyta o następne; puste „Co trzeba zrobić?” kończy serię,
- podsumowanie pokazuje liczbę zadań i łączny szacowany czas,
- dodatkowo pozostaje tryb „Wklej listę” dla seryjnego importu jednej pozycji na linię,
- kalendarz Desktop ma widok miesiąca od poniedziałku do niedzieli, nawigację miesiącami i przycisk „Dziś”,
- dzień pokazuje zadania i liczbę dodatkowych pozycji, a agenda pokazuje szczegóły wybranego dnia,
- podwójne kliknięcie zadania z agendy otwiera edycję,
- z kalendarza można dodać zadanie bezpośrednio na wybrany dzień,
- kalendarz pokazuje również terminy OC i przeglądów pojazdów,
- druk kalendarza pozostaje dostępny.

### Jedna instancja Desktop
- Windows może uruchomić tylko jedną instancję EDHOME Desktop dla danego użytkownika,
- druga próba uruchomienia pokazuje komunikat, że EDHOME już działa i kończy drugi proces,
- blokada korzysta z systemowego locka pliku `~/.edhome/desktop-instance.lock`,
- lock jest zwalniany przy zamknięciu procesu, również po awarii systemowej/JVM; sam plik może pozostać, ale bez aktywnego locka nie blokuje kolejnego uruchomienia.

### Synchronizacja wielourządzeniowa v2 — Desktop 0.6.0.54 / Android 0.6.0.44
- zapis na PC trafia do lokalnego cache natychmiast,
- po około 1,5 s ciszy Desktop wysyła wyłącznie zmienione rekordy,
- Android zapisuje patch bezpośrednio do właściwych rekordów SQLite w jednej transakcji; ścieżka /patch nie wykonuje pełnego restore bazy,
- każdy synchronizowany rekord ma niezależny globalny `sync_uuid` w warstwie `sync_records`,
- metadane zawierają `revision`, `updated_at` i hash treści rekordu,
- lokalna zmiana zrobiona na Androidzie jest wykrywana po hash i podbija rewizję bez przerabiania wszystkich modułów,
- usunięcie pozostawia tombstone `deleted_at`, więc urządzenie wracające po dłuższym offline nie powinno wskrzesić starego rekordu,
- nowe rekordy Desktop dostają kolizyjnie odporne duże ID zamiast MAX(id)+1,
- różne rekordy mogą zostać zmienione równolegle; stale zmieniany ten sam rekord kończy się konfliktem zamiast cichego nadpisania,
- protokół v2 używa `syncUuid + rowKey + baseRevision`; stary patch v1 jest przyjmowany kompatybilnie,
- Desktop wykonuje lekki heartbeat stanu Androida co 10 s, a pełne pojednanie pozostaje kontrolą bezpieczeństwa co około 3 minuty,
- pełny snapshot pozostaje ścieżką zgodności dla ustawień i starszych klientów, ale nie jest używany przez normalny endpoint rekordowy `/patch`.

### Roczny kreator odpadów
- kreator prowadzi miesiąc po miesiącu od stycznia do grudnia,
- obsługuje: Zmieszane, Metale i tworzywa, Papier, Szkło, Bio i Inne,
- w jednym miesiącu można podać kilka terminów tej samej frakcji, np. `5, 19`,
- można cofać się do poprzedniego miesiąca lub pominąć miesiąc,
- na początku wybiera się tryb: daty odbioru albo daty wystawienia,
- przy datach odbioru EDHOME automatycznie wylicza dzień wystawienia o wybraną liczbę dni wcześniej,
- końcowe podsumowanie zapisuje cały rok do Czynności i Kalendarza,
- istniejące identyczne terminy są pomijane, aby nie tworzyć duplikatów.

### Kompaktowe listy i miniaturki
- karty list Desktop są domyślnie kompaktowe i mieszczą około 2–3× więcej pozycji na ekranie niż poprzedni układ,
- tytuł i najważniejsze pola są ułożone poziomo; przyciski akcji pozostają po prawej,
- długie wartości są skracane w karcie, a pełna treść jest dostępna w podpowiedzi,
- Magazyn/Rzeczy pokazuje miniaturę 56×56 px, jeżeli telefon ma zapisaną miniaturę dla danej rzeczy,
- Desktop korzysta wyłącznie z małego `storageThumbnails/jpegBase64` z synchronizowanego snapshotu; nie pobiera oryginalnego zdjęcia ani zewnętrznego URI,
- brak miniatury nie rezerwuje pustego miejsca w karcie.

### Pomieszczenia i fizyczna lokalizacja
- osobna zakładka `Pomieszczenia` buduje jeden widok z istniejących danych `places` i `storage_items`,
- `Miejsca` pozostają edytorem struktury, a `Pomieszczenia` służą do szybkiego sprawdzania gdzie coś fizycznie się znajduje,
- drzewo pokazuje hierarchię: pomieszczenie → podmiejsce/strefa → pudełko → rzecz/narzędzie,
- po zaznaczeniu elementu Desktop pokazuje pełną ścieżkę lokalizacji,
- wyszukiwarka `Gdzie jest` odnajduje pomieszczenia, pudełka i rzeczy po nazwie lub ścieżce,
- elementy bez poprawnie przypisanego miejsca trafiają do grupy `Bez przypisanego miejsca`,
- rzecz/pudełko pokazuje miniaturę 96×96 px, jeśli synchronizowana miniatura istnieje,
- zaznaczone miejsce lub element magazynu można edytować bez opuszczania widoku `Pomieszczenia`,
- widok nie tworzy drugiej bazy ani kopii magazynu; korzysta z tych samych rekordów co Android i moduł Magazyn.

### Plan domu / posesji — docelowy edytor CAD-like
- Desktop 0.6.0.59 wdraża zatwierdzony układ ekranu: drzewo Struktura po lewej, duży plan pośrodku, panel Właściwości po prawej, górny pasek Import JPG / Import DXF / Rozmiar posesji / Dodaj budynek / Dodaj pomieszczenie / Dodaj strefę oraz przełączniki kondygnacji, siatki i snap,
- JPG działa jako rzeczywisty lokalny podkład planu i jest kopiowany do `~/.edhome/floor-map-assets`; DXF można już przypiąć do planu jako źródło szablonu, ale parser geometrii pozostaje następnym krokiem,
- można ustawić wymiary posesji, przełączać Podwórko / Parter / Piętro / Piwnicę, przeciągać istniejące pomieszczenia w trybie `Edytuj plan` oraz korzystać z przyciągania do siatki,
- istniejący `floor-map.json` v1 jest czytany kompatybilnie; nowy zapis v2 zachowuje dotychczasowe pomieszczenia i przejścia oraz dodaje parametry widoku, rozmiar posesji i źródła podkładów,
- prototyp mapy 0.6.0.57 nie jest docelowym UX; nie rozwijać dalej modelu „przesuwanych gotowych prostokątów”,
- użytkownik rysuje pomieszczenie kliknięciem i przeciągnięciem prostokąta; po puszczeniu nadaje nazwę,
- oprócz prostokątów musi być możliwe rysowanie osobnych ścian i pomieszczeń o kształtach L/T/nieregularnych,
- gotowe pomieszczenia są domyślnie zablokowane; dopiero tryb `Edytuj plan` pozwala przesuwać, skalować i usuwać,
- kondygnacje działają warstwowo: Piwnica / Parter / Piętro / kolejne; podczas rysowania wyższej kondygnacji niższa pozostaje widoczna jako wyszarzony półprzezroczysty podkład,
- wszystkie kondygnacje mają wspólny punkt odniesienia 0,0,
- włączyć siatkę pomocniczą z przyciąganiem; dokładna skala/metraż może zostać dodana później,
- narzędzia edytora: Pomieszczenie, Ściana, Drzwi, Schody, Opis, Usuń, Cofnij,
- obsłużyć podkład JPG oraz DXF; DWG traktować jako opcjonalny import po zweryfikowaniu biblioteki/konwersji, bez uzależniania podstawowego edytora od DWG,
- użytkownik wybiera rozmiar obszaru roboczego posesji/podwórka, aby na jednym planie rozmieścić kilka budynków i pomieszczeń,
- narysowane pomieszczenie ma być przypinane do istniejącego rekordu `places` albo tworzyć nowe Miejsce po potwierdzeniu,
- wewnątrz pomieszczenia można później rozmieszczać regały, pudełka i rzeczy/narzędzia; nadal korzystają z istniejących `place_id` i `parent_box_id`,
- drzwi i schody tworzą graf przejść potrzebny później do wyznaczania trasy „jak dojść”,
- bieżąca lokalizacja w pierwszej wersji może być wskazywana ręcznie; później QR/NFC pomieszczenia może ją ustawiać automatycznie.

### SUPLA — zaplanowana nakładka na ten sam plan
- nie tworzyć drugiego planu dla SUPLA,
- używać dokładnie tej samej geometrii posesji, budynków, kondygnacji, ścian, pomieszczeń, drzwi i schodów,
- nakładka SUPLA pokazuje urządzenia przypisane do istniejącego `place_id`, ich stan online/offline, włączone/wyłączone, czujniki i wspierane akcje,
- zmiana geometrii planu automatycznie obowiązuje warstwę SUPLA,
- zakres SUPLA pozostaje na później.


## Braki do pełnej zgodności z APK

Poniższe elementy nadal wymagają domknięcia mimo funkcjonalnego Desktop 0.6.0.54.

### Pulpit i personalizacja
- konfigurowalne kafelki,
- dodawanie/usuwanie/ukrywanie kafelków,
- zmiana celu kafelka,
- przeciąganie i kolejność,
- szerokość pojedyncza/podwójna,
- motywy,
- paczki ikon 3D,
- ustawienia czasów gestów.

### Czynności, zadania i domownicy
- domownicy/wykonawcy,
- grafik tygodniowy i wyjątki,
- rotacja wykonawców,
- pełne przypomnienia i historia wykonań,
- kompletna logika zaległych czynności.

### Kalendarz
- pełny widok kalendarzowy miesiąc/tydzień/dzień,
- nawigacja po datach i zaległych,
- wszystkie typy zdarzeń,
- pełne powiązanie zdarzeń pojazdów i czynności.

### Spiżarnia
Skanowanie pojedynczego kodu i szybkie +1/−1 są dostępne. Nadal brakuje:
- skanowania seryjnego,
- pełnej historii skanów,
- filtrów kategorii i wyszukiwarki zgodnych z APK,
- pełnej historii cen i obsługi opakowań,
- pełnego kreatora remanentu.

### Zakupy
- pełne przyjęcie zakupu do spiżarni,
- wybór miejsca docelowego,
- zapis ceny i historia cen,
- pełna logika „kupione” kontra „przyjęte”.

### Magazyn i Miejsca
QR/NFC, etykiety, druk, przenoszenie oraz wypożyczenie/zwrot po skanie są dostępne. Nadal brakuje:
- pełnej obsługi zdjęć i miniaturek,
- historii skanowania i drukowania zgodnej 1:1 z APK,
- kompletnego drzewa i wszystkich operacji modułu magazynowego poza ścieżką skanera.

### PayCheck
Import bankowy, kolejka, analiza, cele, masowa edycja i prywatny sejf są dostępne. Nadal brakuje:
- parserów PDF dla kolejnych banków poza obsługiwanym VeloBankiem,
- pełnej zgodności wszystkich ekranów i historii PayCheck z APK,
- desktopowego odpowiednika odbioru systemowych powiadomień bankowych w czasie rzeczywistym.

### Pojazdy
- historia polis OC,
- przypomnienia OC i przeglądu,
- dokumenty i koszty pojazdu,
- integracja kosztów z oczekującym PayCheck,
- historia serwisu,
- komplety opon, ich stan, lokalizacja i montaż/demontaż.

### Odpady i minutniki
- pełne kreatory, cykle, powiadomienia i integracja z kalendarzem,
- pełna obsługa start/stop/potwierdzenie minutnika.

### Remanent
- rozpoczęcie/wznowienie sesji,
- licznik postępu,
- korekty, pomijanie i cofanie,
- wykrywanie konfliktów i końcowe zatwierdzenie.

### Kopia danych, diagnostyka i ustawienia
- pełny eksport/przywracanie wspólnej kopii danych z poziomu Desktop,
- diagnostyka i eksport logów,
- kompletna kontrola zgodności wersji Android ↔ Desktop,
- pełna zgodność ustawień wyglądu, przypomnień i godzin ciszy z APK.

## Kryterium ukończenia Desktop

Desktop można uznać za funkcjonalnie zgodny z APK dopiero wtedy, gdy przejście przez wszystkie moduły Androida i Desktopu daje tę samą listę dostępnych operacji użytkownika oraz identyczny wynik danych po synchronizacji.

Sama obecność ekranu lub tabeli **nie oznacza zgodności**. Przykład: ekran „Pojazdy” na PC nie jest ukończony, dopóki nie obsługuje również polis, dokumentów, kosztów, serwisów, opon, przypomnień i powiązania z PayCheck tak jak APK.

## Uruchomienie

GitHub Actions buduje paczkę Windows z dołączonym runtime Java. Po rozpakowaniu uruchom `EDHOME-Desktop-Beta.exe`.

Na telefonie wejdź w **Ustawienia → EDHOME Desktop • Wi-Fi**. Najprościej sparować urządzenia przez QR wyświetlany na PC. Po sparowaniu aplikacje synchronizują dane przez lokalną sieć Wi-Fi/LAN.
