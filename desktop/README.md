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

## Aktualny stan Desktop

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
- panel Skaner: skaner USB/klawiaturowy, QR/kod z kamery lub obrazu i NFC PC/SC,
- trwałe, synchronizowane powiązania tagów NFC z obiektami EDHOME,
- podstawowe widoki: Pulpit, Dzisiaj, Kalendarz, Zadania, Czynności, Magazyn, Spiżarnia, Zakupy, PayCheck, Pojazdy, Odpady, Timery, Energia, SUPLA, Miejsca i Ustawienia.

Desktop **nie jest już read-only**.

## Braki do pełnej zgodności z APK

Poniższe elementy istnieją w aktualnym APK, ale Desktop nie ma jeszcze ich pełnego odpowiednika.

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
- grafik tygodniowy,
- wyjątki grafiku,
- przypisanie wykonawcy,
- rotacja wykonawców,
- pełne reguły powtarzania,
- przypomnienia czynności,
- historia wykonań pojedynczej czynności,
- historia wszystkich wykonań,
- kompletna logika zaległych czynności.

### Kalendarz
Desktop pokazuje obecnie głównie listę terminów. Brakuje zgodności z widokiem APK:
- nawigacji po datach,
- przejścia do konkretnej daty,
- obsługi zaległych,
- prezentacji wszystkich typów zdarzeń,
- OC i przeglądów pojazdów jako zdarzeń,
- pełnego powiązania czynności z kalendarzem.

### Spiżarnia
- skanowanie kodów produktów,
- szybkie +1 / -1,
- skanowanie seryjne,
- ręczne wpisanie kodu,
- historia skanów,
- filtrowanie kategoriami,
- wyszukiwarka,
- historia cen,
- pełna obsługa opakowań i ilości,
- logika wyjmowania produktu,
- integracja z remanentem.

### Zakupy
- przyjęcie zakupu bezpośrednio do spiżarni,
- wybór miejsca docelowego,
- zapis ceny zakupu,
- powiązanie z historią cen,
- rozróżnienie „kupione” / „przyjęte do spiżarni”,
- kompletna logika potwierdzenia przyjęcia.

### Magazyn i Miejsca
- zdjęcia i miniaturki rzeczy,
- QR rzeczy, pudełek i miejsc,
- pełna obsługa kamery/webcam do QR (USB/klawiatura i odczyt z obrazu są już obsługiwane),
- skanowanie QR,
- drukowanie pojedynczych i zbiorczych etykiet,
- PDF i udostępnianie etykiet,
- historia skanowania i drukowania,
- przenoszenie rzeczy/pudełek,
- wypożyczenie i zwrot,
- pełne drzewo lokalizacji,
- działania po zeskanowaniu QR.

### PayCheck
Desktop ma podstawową obsługę wspólnych transakcji, ale brakuje:
- pełnej kolejki „do potwierdzenia”,
- potwierdzania na podstawie banku/wyciągu,
- importu CSV / mBank / XLSX,
- obsługi powiadomień bankowych lub desktopowego odpowiednika,
- kolejki banków i potwierdzeń,
- celów wspólnych,
- pełnej historii źródła potwierdzenia,
- prywatnego PayCheck,
- szyfrowanego sejfu,
- eksportu/importu zaszyfrowanej kopii prywatnych finansów.

### Pojazdy
- historia polis OC,
- przypomnienia OC i przeglądu,
- widoczność terminów w kalendarzu,
- dokumenty pojazdu,
- koszty pojazdu,
- przekazanie kosztu do oczekujących PayCheck,
- historia serwisu,
- komplety opon,
- stan i lokalizacja opon,
- montaż/demontaż kompletu.

### Odpady
- kreator dodawania terminu,
- cykle odbioru,
- przypomnienia,
- pełna integracja z Czynnościami i kalendarzem.

### Minutniki
- uruchamianie minutnika,
- pozostały czas,
- zatrzymanie bez wykonania,
- potwierdzenie zakończenia,
- powiadomienia o zakończeniu.

### Remanent
Desktop nie ma jeszcze pełnego kreatora remanentu:
- rozpoczęcie/wznowienie sesji,
- licznik postępu,
- „zgadza się”,
- podanie faktycznego stanu,
- brak na półce,
- pominięcie pozycji,
- cofnięcie ostatniej odpowiedzi,
- zatwierdzenie korekt,
- wykrywanie konfliktów,
- anulowanie bez zmian.

### Kopia danych, aktualizacje i diagnostyka
Desktop ma własną aktualizację jednym przyciskiem, ale do zgodności z APK należy jeszcze domknąć:
- pełny eksport kopii danych,
- pełne przywracanie kopii,
- diagnostykę i eksport logów,
- wspólny status synchronizacji i wersji,
- kontrolę zgodności wersji Android ↔ Desktop.

### Ustawienia
- nazwa gospodarstwa,
- globalne przypomnienia,
- godziny ciszy,
- ustawienia skanera/spiżarni,
- ustawienia wyglądu zgodne z APK,
- pozostałe ustawienia modułów dostępne w aplikacji Android.

## Kryterium ukończenia Desktop

Desktop można uznać za funkcjonalnie zgodny z APK dopiero wtedy, gdy przejście przez wszystkie moduły Androida i Desktopu daje tę samą listę dostępnych operacji użytkownika oraz identyczny wynik danych po synchronizacji.

Sama obecność ekranu lub tabeli **nie oznacza zgodności**. Przykład: ekran „Pojazdy” na PC nie jest ukończony, dopóki nie obsługuje również polis, dokumentów, kosztów, serwisów, opon, przypomnień i powiązania z PayCheck tak jak APK.

## Uruchomienie

GitHub Actions buduje paczkę Windows z dołączonym runtime Java. Po rozpakowaniu uruchom `EDHOME-Desktop-Beta.exe`.

Na telefonie wejdź w **Ustawienia → EDHOME Desktop • Wi-Fi**. Najprościej sparować urządzenia przez QR wyświetlany na PC. Po sparowaniu aplikacje synchronizują dane przez lokalną sieć Wi-Fi/LAN.
