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

## Zasada nadrzędna: Desktop = APK 1:1

EDHOME Desktop **nie jest uproszczonym dodatkiem do aplikacji Android**. Docelowo ma zapewniać pełną zgodność funkcjonalną z aktualną wersją EDHOME Beta APK.

Każda funkcja dostępna użytkownikowi w APK powinna mieć odpowiednik w Desktopie:
- te same moduły,
- te same operacje na danych,
- te same pola i reguły walidacji,
- te same zależności między modułami,
- ten sam stan danych po synchronizacji,
- te same możliwości dodawania, edycji, usuwania, potwierdzania i przeglądania historii.

Jeżeli funkcja zależy od telefonu, aparatu, Androida lub systemowych powiadomień, Desktop ma dostać **równoważny sposób wykonania tej samej czynności**, a nie pominięcie funkcji.

Nowa funkcja dodawana do APK nie jest uznawana za domkniętą dla całego EDHOME, dopóki nie zostanie:
1. obsłużona przez synchronizację Android ↔ PC,
2. udostępniona w interfejsie Desktop,
3. zabezpieczona przed konfliktem równoczesnej edycji,
4. sprawdzona testem zgodności APK ↔ Desktop.

## Aktualny stan Desktop — 0.6.0.49

Aktualna linia Desktop obsługuje:
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
- akcje po QR/NFC: otwarcie, edycja, przeniesienie, wypożyczenie/zwrot i etykieta QR tam, gdzie operacja ma zastosowanie,
- zapamiętywanie domyślnej akcji dla konkretnego QR/NFC,
- pojedyncze i zbiorcze etykiety QR dla rzeczy, pudełek i miejsc,
- formaty etykiet 40 × 30 mm, 50 × 30 mm, 70 × 50 mm, A4 zbiorczo i własny rozmiar,
- podgląd etykiet, eksport do PDF i bezpośredni wydruk,
- zapamiętywanie drukarki etykiet,
- PayCheck: lokalny import PDF/CSV/XLSX, kolejka dowodów bankowych, deduplikacja i ręczne dopasowanie,
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

Zakres zatwierdzony dla QR/drukowania, skanowania, importów bankowych, analizy PayCheck, prywatnego PayCheck oraz drukowania kalendarza i zadań jest wdrożony w linii 0.6.0.49. Regresje tego zakresu są chronione testem `tests/check_desktop_approved_scope.py`.

### Ograniczenia importu PDF banków
Automatyczne rozpoznanie banku nie oznacza zgadywania układu dokumentu. W 0.6.0.49 bezpiecznie obsługiwany jest tekstowy PDF VeloBanku. mBank jest obsługiwany przez CSV/XLSX. Zwykły CSV jest obsługiwany, jeśli zawiera stabilny identyfikator transakcji i wymagane kolumny. PDF innych banków jest odrzucany, dopóki nie ma jawnego parsera dla ich układu.

## Braki do pełnej zgodności z APK

Poniższe elementy nadal wymagają domknięcia, mimo że zatwierdzony pakiet Desktop 0.6.0.49 jest już funkcjonalny.

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
