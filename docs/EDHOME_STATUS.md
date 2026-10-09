# EDHOME — aktualny stan prac

> **Stały dziennik kontynuacji projektu.** Przed rozpoczęciem pracy nad EDHOME przeczytaj najpierw ten plik, a po zakończeniu każdego etapu zaktualizuj go w tym samym repozytorium. Szczegółowe wymagania pozostają w [ROADMAP.md](ROADMAP.md); ten plik jest krótkim, aktualnym punktem wznowienia pracy.

## Metryka

| Pole | Stan |
|---|---|
| Ostatnia aktualizacja | 2026-10-09 17:50 — realny VeloBank PDF: 7 błędów od daty 08.10, import w całości odrzucony; poprawki wielowierszowych kwot Android Beta **0.8.0.81/273 KANDYDAT**, CI #2097 PASS, podpisany APK 0.8.0.81/273 opublikowany (13 345 032 B, SHA-256 af4cd09d63649660bc1c46d9036d2f653313cdd404a1b054be11d5f3edb41794fee7). Test rzeczywistego PDF pozostaje P0 / NIEODEBRANY. |
| Repozytorium | `edwinkarolczyk/Edhome` |
| Gałąź robocza | `beta` |
| Stable | `main` — **zakaz zmian, merge i publikowania nowego Stable bez osobnej, wyraźnej akceptacji Edwina** |
| Android Beta | **0.8.0.81 / versionCode 273** — [CI #2097 PASS](https://github.com/edwinkarolczyk/Edhome/actions/runs/37955351342), [podpisany APK](https://github.com/edwinkarolczyk/Edhome/releases/download/beta-v0.8.0.81/edhome-beta.apk) (13 345 032 B, SHA-256 `af4cd09d63649660bc1c46d9036d2f653313cdd404a1b054be11d5f3edb41794fee7`). Kod/testy PASS, rzeczywisty VeloBank PDF na telefonie nadal NIEODEBRANY. |
| Desktop Beta | **0.7.0.115 — CI #292 PASS** [GitHub Actions](https://github.com/edwinkarolczyk/Edhome/actions/runs/37895745689); test użytkownika po LAN otwarty. |
| Ostatni odczytany HEAD `beta` przed utworzeniem tego pliku | `85fec69ff4a6c154d90bd9e9e0a625e20a9bb2d0` — commit wyłącznie roadmapy |
| Ostatni zweryfikowany CI Android | **0.8.0.81/273, CI #2097 PASS** — [GitHub Actions](https://github.com/edwinkarolczyk/Edhome/actions/runs/37955351342), [podpisany release](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.81), commit `1dd89e24ecc4a46a4ed9a2441c02387aff4e28fe`. Syntetyczne testy parsera PASS, nie potwierdzono kompletności prawdziwego pliku. |
| Ostatni zweryfikowany CI Desktop | **Desktop Beta 0.7.0.115, CI #292 PASS** [GitHub Actions](https://github.com/edwinkarolczyk/Edhome/actions/runs/37895745689), commit `cc56a74b3cb7595440d29d0b09e3b43ac5c466f1` |
| Bieżący etap | **P0 realny PDF VeloBank Android:** Beta 0.8.0.81/273 CI #2097 PASS; obsługa +/− bez waluty w pojedynczych wierszach, wieloliniowych przelewów, sygnalizacja błędu przy niewczytanej operacji, etap diagnostyki w oknie. Czyta plik użytkownika poprawnie? NIEPOTWIERDZONE. Nie mylić z przejściem CI |
| Następny krok | Na telefonie zainstalować Beta 0.8.0.80, w PayCheck → Import historii bankowej → VeloBank wybrać rzeczywisty PDF. Zgłosić dokładny komunikat z etapem jeśli odrzucony, lub liczbę/datę ostatniej pozycji i brakujący przelew 09.10 jeśli wczytany. W razie dalszego niepowodzenia uzyskać zamaskowaną próbkę źródła. Nie zmieniać Stable main, nie zamykać P0 przed kompletnym testem historii, salda i duplikatów |

**Ważne:** zielone CI dotyczy wskazanego commita, a nie automatycznie wszystkich przyszłych zmian. Wydania i funkcje wymagające testów na fizycznych urządzeniach są oznaczane jako *nieodebrane*, dopóki taki test faktycznie nie przejdzie.

## Audyt przyczyny brakujących transakcji VeloBank — 09.10.2026, 14:58

**Stan:** analiza źródła Android Beta 0.8.0.80/272, CI #2092 PASS; **bez zmian kodu importu i bez zmian salda/danych bankowych**. Zgłoszenie brakujących przelewów z 09.10 oraz rozbieżności między datą nagłówka w PayCheck 05.10 a datą 07.10 w opisie nadal **P0 niezamknięte**.

**Potwierdzone ścieżki kodu:**
- `MainActivity.importStatementFilesWorker()`: PDF → `BankPdfText.extract()` → `BankStatementVeloPdf.parse()` → `BankEvidenceStore.ingest()`. Ostatnia wyświetlana data jest obliczana z dat rzeczywiście *rozpoznanych* przez parser, a nie wszystkich dat obecnych w źródłowym tekście PDF.
- `BankStatementVeloPdf.parseStatementRows()`: bierze **pierwszą datę w datowanym wierszu**, nie rozdziela jawnie daty operacji od daty księgowania. Screenshot z 05.10 w nagłówku i 07.10 w opisie jest zgodny z takim scenariuszem, ale bez rzeczywistego dokumentu nie wiadomo, które pole pochodzi z której kolumny banku.
- **Konkretna luka parsowania:** datowany wiersz z **dwiema datami, ale bez frazy rozpoznawanej przez `TRANSACTION_LINE` i bez kwoty w tej samej linii**, po którym dopiero kolejne linie niosą nazwę przelewu i kwotę, jest pomijany bez zwiększenia `unreadableCount`. Wynika wprost z warunku `if(moneyTokens.isEmpty()&&TRANSACTION_LINE.matcher(line).find())` oraz wcześniejszego obsłużenia tylko `line.equals(statementDate)`. To możliwa przyczyna niewidocznych 09.10 mimo syntetycznych testów.
- `BankPdfText.extract()` używa `PDFTextStripper.getText()` bez ustawienia kolejności pozycyjnej. W układzie tabelowym PDF tekst może trafiać do parsera w kolejności strumienia, nie w kolejności kolumn; nie ma potwierdzenia, że to występuje w pliku użytkownika.
- `BankEvidenceStore.ingest()`: transakcja SQL wstawia wszystkie kandydaty zwrócone przez parser albo wycofuje całą partię przy błędzie. Nie znaleziono datowego filtra do 05.10. Sama deduplikacja może jedynie odrzucić istniejący `evidenceKey`, nie wylicza daty granicznej.
- `MainActivity.autoSettleImportedBankEvidence()`: `sourceKind=velo_pdf` **nigdy nie uzgadnia się automatycznie**, więc nieobecność 09.10 na wcześniejszej liście 70 otwartych nie wynikała z automatycznego potwierdzenia PDF.
- `BankEvidenceStore.listPageAll()` w Beta 0.8.0.80 pokazuje wszystkie statusy; poprzednia lista „Wszystkie” odnosiła się do otwartych.
- Testy CI Velo to pliki tekstowe syntetyczne, a nie rzeczywisty PDF VeloBanku użytkownika.

**Następny bezpieczny krok:** porównać tę samą rzeczywistą historię PDF z widocznymi w banku datami i liczbą pozycji: źródłowe operacje do 09.10 → tekst wypisany przez PDFTextStripper (może być próbka po zamaskowaniu danych) → wynik parsera 0.8.0.80 → `Cała historia bankowa`. Jeśli brak próbki, dodać najpierw diagnostykę tylko odczytu „najpóźniejsza data występująca w tekście PDF” vs „najpóźniejsza rozpoznana transakcja” (daty nagłówka ≠ transakcje, więc bez automatycznego księgowania). Dopiero potem zmieniać rozdzielanie dat lub składanie datowanych wieloliniowych rekordów. Nie zamykać P0 na podstawie CI. Stable main nietknięta.

## P0 — rzeczywisty VeloBank PDF: 7 nieodczytanych operacji od 08.10 (09.10.2026, 17:50)

**Dowód z telefonu:** po imporcie jednego pliku PDF aplikacja pokazuje `Etap: VeloBank / transakcje • plik 1/1` oraz `7 potencjalnych operacji nie dało się poprawnie odczytać (pierwsza data 08.10.2026)`; **partia odrzucona, 0 zaimportowanych**. Stan lokalnej historii przed nową próbą: **74 operacje**, w tym 69 `open`, 1 `matched`, 4 `dismissed`. Osobno 13 pozycji Budżetu `do potwierdzenia`. Brak połączenia nasłuchu powiadomień Android nie ma związku z lokalnym parserem PDF.

**Potwierdzenie błędu:** Beta 0.8.0.80 / 272, CI #2092 PASS, ale **rzeczywisty import FAIL**. `BankStatementVeloPdf.parseStatementRows()` szukał podpisanej kwoty albo `PLN/zł` w maks. 4 kolejnych wierszach po datowanym przelewie, nie odczytywał ostrożnie kwot typu „Kwota przelewu: 4300,00” bez znaku i jednostki. Łączenie daty ze słowem operacji działało tylko dla wiersza z jedną datą. Nie można stwierdzić bez zamaskowanego PDF, jaki dokładnie układ wszystkich siedmiu operacji był w pliku.

**Wprowadzony kandydat Android Beta 0.8.0.81 / 273, wyłącznie `beta`:**
- `BankStatementVeloPdf.java` `ddf515c3`: podpisana lub jawnie nazwana „Kwota / Kwota przelewu / Kwota operacji” z groszami bez PLN, jeśli typ transakcji określa kierunek; 10 wierszy kontynuacji zamiast 4, bez przekraczania kolejnej datowanej operacji ani salda; data + druga data + typ przelewu w następnej linii; błąd nie tylko „7”, ale **brak kwoty / wiele kwot / brak jednoznacznego kierunku**. Gdy istnieje co najmniej jedna niejednoznaczna operacja, odrzuca CAŁĄ partię bez księgowania.
- `BankStatementVeloPdfSmoke.java` `f4fea4ab`, `a4624194`: regresje dla dwóch dat, niepodpisanego +4300/-280 według nazwy przelewu, dłuższych rekordów, siedmiu nieodczytanych i zakazu uznania salda za kwotę transakcji.
- `app/build.gradle` `a82bcf79`: `0.8.0.81 / 273`; release notes `1dd89e24`. **CI #2097 PASS**, [podpisane APK Beta 0.8.0.81](https://github.com/edwinkarolczyk/Edhome/releases/download/beta-v0.8.0.81/edhome-beta.apk), [przebieg CI](https://github.com/edwinkarolczyk/Edhome/actions/runs/37955351342); test rzeczywistego wyciągu nieodebrany.
- **Nie zmieniono żadnych wpisów lokalnych ani poprzednich kluczy transakcji rozpoznawanych w całości z jednego wiersza; `main` nietknięty.**

**Odbiór obowiązkowy:** zweryfikować CI, instalację podpisanego APK; ponowny import tego samego dokumentu powinien wyświetlić najpóźniejszą datę 09.10 (jeśli rzeczywiście występuje w pliku), liczbę rozpoznanych nowych/duplikatów i przynajmniej 7 odzyskanych lub jawny błąd z kategoriami. Przed stwierdzeniem kompletności zestawić liczbę wszystkich operacji i daty z PDF banku, rozdzielić datę operacji od księgowania i sprawdzić saldo. Jeśli nadal nie działa, uzyskać PDF po maskowaniu danych i przeprowadzić regresję na realnym układzie; nie zgadywać transakcji ani nie importować częściowej historii.

## P0 — VeloBank PDF nadal nieczytelny na Androidzie (09.10.2026)

**Nowe zgłoszenie:** w Beta 0.8.0.79 użytkownik nie może odczytać wyciągu VeloBanku. Nie znamy jeszcze dokładnego komunikatu/układu oryginalnego dokumentu. Zielone CI i syntetyczne pliki NIE są dowodem, że rzeczywisty PDF importuje się w całości.

**Weryfikacja kodu wykazała:** dotychczasowe wyrażenie regularne `MONEY` wymagało symbolu waluty `PLN/zł` na każdej linii. W PDF, gdzie waluta występuje tylko w nagłówku tabeli, podpisane kwoty w wierszach operacji były pomijane. Parser mógł też przy jednej rozpoznanej pozycji milcząco pominąć następną nieczytelną; niepoprawnie mógł próbować łączyć dane kolejnej datowanej transakcji. Mogą istnieć też inne układy PDF, których bez rzeczywistej próbki nie znamy.

**Kandydat Android 0.8.0.80/272 tylko na `beta`:**
- `BankStatementVeloPdf.java` commit `615b5ea` — akceptacja jednoznacznie podpisanych kwot z groszami, gdy `PLN` jest tylko w nagłówku, także w wieloliniowych transakcjach. Nie łączy linii przekraczając kolejną datę ani słowo `saldo`; odrzuca import przy chociaż jednej wykrytej nieczytelnej operacji, zamiast po cichu wczytać fragment. Zachowuje identyfikatory dotychczas poprawnie rozpoznawanych wierszy.
- `BankStatementVeloPdfSmoke.java` commit `41e3a9a` — testy dla operacji bez `PLN` w każdej linii, wpływu +4300 zł, historii z 09.10, niezapisania częściowej historii i niewykorzystywania salda jako kwoty przelewu.
- `MainActivity.java` commit `207a9ea` — przy nieudanym imporcie widoczny etap `PDF / tekst` lub `VeloBank / transakcje` z numerem pliku bez ujawniania treści wyciągu. Brak zapisów dla błędnej paczki.
- `app/build.gradle` commit `5862077` — 0.8.0.80 / 272, opis zmian Beta commit `a428bfb`; [CI #2092 PASS](https://github.com/edwinkarolczyk/Edhome/actions/runs/37924874674), podpisany APK 0.8.0.80 opublikowany.

**Weryfikacja publikacji:** [CI #2092 PASS](https://github.com/edwinkarolczyk/Edhome/actions/runs/37924874674). [Beta 0.8.0.80 podpisana i opublikowana](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.80); 13 344 671 B; SHA-256 `a6fdd2726ebdaf1a7ec11fc7eed9bed777475a5bcef7f8d50c6c8af39394fee7`. Rzeczywisty plik klienta nadal nieweryfikowany; wynik CI nie zamyka P0.

**P0 nadal nieodebrane:** potrzebny dokładny komunikat i etap błędu z telefonu (bez danych wrażliwych), ewentualnie kopia wyciągu po maskowaniu numerów rachunków i danych osobowych. Sprawdzić kompletną listę operacji po imporcie i brakujący 09.10, duplikaty, potwierdzenia, saldo. Nie twierdzić, że samo CI dowodzi naprawy. Stable `main` nietknięta.

## P0 — czytelny PayCheck, import i usuwanie historii (09.10.2026)

**Zgłoszenie z telefonu:** wybór banku przy imporcie nie pozwalał skutecznie dodać historii; brakowało usuwania pojedynczych importowanych operacji. Lista pokazywała długie teksty zamiast kompaktowych kwot, trzeba ją rozwijać jak w Budżecie: jeden wiersz na raz.

**Wdrożenie tylko `beta`, kandydat Android 0.8.0.79 / 271:**
- `MainActivity.selectStatementCsv()` commit `7f651ead`: usunięte łączenie `AlertDialog.setMessage()` z `setItems()` — lista banków mBank / VeloBank / Inny ma teraz osobny własny nagłówek `setCustomTitle`. `ACTION_OPEN_DOCUMENT` z rezerwowym `ACTION_GET_CONTENT`, zapamiętanie banku także po odtworzeniu aktywności, widoczny błąd przy braku wyboru pliku.
- `BankEvidenceStore.deleteUnmatched()` commit `f77ebb56`: wyłącznie `open` lub `dismissed` można usunąć; stan `matched` lub dowód użyty w PayCheck jest chroniony. Operacja usunięta z kolejki wróci po ponownym imporcie tego samego pliku; nie księguje żadnego salda.
- `MainActivity.showBankEvidenceList()` commit `cf07543f`: kwota pod kwotą, kliknięcie otwiera szczegóły (opis, bank, data i akcje), drugie kliknięcie zwija, kliknięcie innej automatycznie zwija poprzednią. Jeden rozwinięty wiersz. Obejmuje otwarte i wszystkie statusy. Import i filtry są dostępne bez powrotu do głównego ekranu.
- `MainActivity.paycheck()` i `showSharedPaycheckHistoryPage()`, commity `ef55dd1a` i `034b924c`: także lista ostatnich 40 i pełna historia PayCheck są w formie jednoliniowych kwot, ze szczegółami i akcjami dopiero po dotknięciu. Historię przegląda się stronicowo.
- Testy kontraktowe `bea510db`, `5d0e59d`, `7c9190a`, `95fe0d0` — sprawdzają nowe UI, picker i usuwanie. Pierwszy CI #2082 FAIL na testowej starej nazwie przycisku; CI #2085 PASS po dostosowaniu kontraktów wcześniejszych nazw i filtrów; podpisany APK 0.8.0.79 opublikowany. `app/build.gradle` commit `e87398b`: 0.8.0.79/271, changelog commit `35d776c`.

**Wydanie:** [CI #2085 PASS](https://github.com/edwinkarolczyk/Edhome/actions/runs/37922923711), [Release 0.8.0.79/271](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.79), podpisany APK 13 344 423 B, SHA-256 `f8eefae36a16421205c00d8160ddf3f17c2dc000e3a6f2534cfa8e3c4c886eee`. Changelog manifestu Beta potwierdzony. Zakończono CI; telefon i rzeczywisty wyciąg jeszcze nieodebrane.

**Otwarte P0 przed uznaniem za gotowe:** potwierdzić CI/publikację i test na fizycznym telefonie. Nie ma jeszcze dowodu, że wybierak i parser obsłużyły rzeczywistą historię Velo do 09.10 ani że dane są w pełni kompletne. Usuwanie matched pozostaje celowo zablokowane, by nie zmienić salda bez świadomego odłączenia. Stable `main` nietknięta.

## P0 — różne kwoty Bank ↔ Budżet ↔ PayCheck (09.10.2026)

**Ustalenie użytkownika:** plan jest tylko planem. Jeśli w Budżecie oczekiwany wpływ wynosi 4500 zł, a wyciąg pokazuje 4300 zł, PayCheck po zatwierdzeniu ma przyjąć **+4300 zł**, nie +4500 zł. Tak samo dla każdej kategorii wpływów i wydatków; rachunek plan −300 zł, bank −280 zł oznacza saldo −280 zł. Nadwyżka bankowego wydatku jest rozliczana jako faktyczna nadwyżka kosztu, a nie dopasowana do fikcyjnego planu.

**Implementacja — tylko gałąź `beta` (Android kandydat 0.8.0.78 / 270):**
- `BankEvidenceStore.matchBudgetActual`, commit `a9b90fadd`: atomowe, ręczne potwierdzenie dowodu bankowego i zmiana kwoty **wyłącznie oczekującej transakcji pochodzącej z Budżetu** na wartość rzeczywistą, wraz z datą i unikalnym `evidence_key`. Reimport/duplikat/dowód zużyty nie zmienia salda drugi raz. Automatyczne uzgodnienie równych kwot pozostaje konserwatywne.
- `MainActivity.openBankEvidenceRow` i `confirmBankEvidenceMatch`, commit `c895652413`: zamiast wymagać identycznej kwoty, pozwalają użytkownikowi wybrać aktywną pozycję Budżetu z tego samego miesiąca i kierunku oraz potwierdzić „plan / bank / różnica”. Mocniejsze rozpoznanie odbiorcy to tylko podpowiedź; **nierówne kwoty nigdy nie księgują się automatycznie bez decyzji**. Również importowane PDF VeloBanku można ręcznie dopasować po upewnieniu się co do pozycji.
- `PaycheckMonthlyBudget.addPaymentEvents`, commit `c13d1a87b`: w dzienniku pojawiają się również `INCOME_SHORTFALL` i `INCOME_SURPLUS` dla wpływów; dla wydatków zachowano dotychczasowe zaległości i nadpłaty. Plan nie jest nadpisywany.
- `MainActivity`, commit `3b934f6f9`: w otwartej kolejce bankowej widoczna jest informacja o potencjalnie innej kwocie; lista niezrealizowanych pozycji pokazuje potwierdzony wpływ/wydatek i resztę względem planu. Zablokowane ręczne potwierdzanie kwoty **samego planu** bez wskazania rzeczywistego bankowego dowodu, żeby przez pomyłkę nie zaksięgować 4500 zł zamiast 4300 zł.
- `tests/check_budget_bank_automatch_android.py`, commit `677744c129`: kontrakt dla wypłaty 4500→4300, rachunków 300→280 i 150→170, premii 500→650, idempotencji i salda wynikającego z faktycznych groszy. Test SQL jest symulacją, nie oznacza wykonania pełnego testu interfejsu na telefonie.
- `app/build.gradle` commit `a241eec3d`: 0.8.0.78/versionCode 270, [CI #2072 PASS](https://github.com/edwinkarolczyk/Edhome/actions/runs/37920454073), podpisany [APK 0.8.0.78](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.78) opublikowany.
- `.github/workflows/android-beta.yml`, commit `07e37da`: poprawiono «Co nowego» dla wypłaty/rachunków z inną kwotą, różnic w Budżecie i parsera Velo. Ostateczna publikacja w CI #2072. Wersja 0.8.0.77/269 poprzednio potwierdzona przez CI #2065 PASS i wydany podpisany APK. Stable `main` bez zmian.

**Weryfikacja publikacji Beta 0.8.0.78/270:** [CI #2072](https://github.com/edwinkarolczyk/Edhome/actions/runs/37920454073) **PASS** na commicie `07e37da6020e484f710490c95f33fea9ceab6c32`; podpisany [edhome-beta.apk](https://github.com/edwinkarolczyk/Edhome/releases/download/beta-v0.8.0.78/edhome-beta.apk), 13 342 421 B, SHA-256 `bd2b462b6505d6cb6b453d8cd25dce149a231eeece51d035286ff263b3a8b4c3`. Changelog aktualizatora odświeżony. **Test rzeczywistych transakcji na fizycznym Androidzie nieodebrany.**

**Otwarte i nieprzeskakiwalne:** fizyczny test wpływów/wydatków o różnych kwotach na Androidzie; nie rozwiązywać automatycznie niejednoznacznych odbiorców; nie przeliczać planu po imporcie; sprawdzić stały identyfikator dowodu i dwa importy tego samego wyciągu; jeśli zapis w PayCheck powiedzie się, ale przypisanie do Budżetu zawiedzie, aplikacja powinna ujawnić błąd i udostępnić ręczne przypisanie bez ponownego księgowania; kompletność PDF VeloBanku i dat pozostaje osobnym P0. Desktop i LAN/backup dopiero po odbiorze.

## P0 — kontynuacja po CI #2062: wieloliniowe przelewy VeloBank (09.10.2026)

**Nie uznawać za naprawione wyłącznie na podstawie zielonego CI.** Poprzednie testy obejmowały przede wszystkim wiersze PDF z datą i kwotą w jednej linii. Prawdziwe wyciągi mogą rozdzielać datę księgowania, datę operacji, opis i kwotę na kilka linii, a importer miał prawo zgubić przelewy mimo prawidłowego wczytania kart. Ze zdjęcia: 70 pozycji w dawnym oknie „Wszystkie” oznaczało 70 otwartych, nie całe archiwum. Wpis 05.10 zawierał w opisie 07.10: wciąż wymaga porównania znaczenia dat z oryginałem.

**Weryfikacja poprzedniego etapu:** Android Beta **0.8.0.76 / 268**, [CI #2062 PASS](https://github.com/edwinkarolczyk/Edhome/actions/runs/37917478402), [release z podpisanym APK](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.76), opublikowany 09.10.2026 (APK 13 339 627 B). Ten etap dodał pełną historię bankową (open/matched/dismissed), liczby i zakres dat importu oraz odrzucanie rozpoznanych niekompletnych przelewów. **Nie odzyskiwał jeszcze operacji, której kwota była w innej linii PDF.**

**Następna korekta, wyłącznie `beta` — kandydat Android 0.8.0.77/269:**
- `BankStatementVeloPdf.java`, commit `29b4bc17` — obsługa daty + typu operacji + kwoty rozdzielonych na linie (maks. kilka wierszy), bez łączenia z następną datowaną operacją, bez traktowania „Saldo” jako kwoty przelewu. Dotychczasowe poprawne pojedyncze linie pozostają bez zmian identyfikatora.
- `BankStatementVeloPdfSmoke.java`, commit `03530cfb` — dodatkowe przypadki: 2 transakcje kartą i przelew 09.10 na kilku liniach, osobna linia daty, kierunek wydatku, grosze i stabilny identyfikator przy ponownym imporcie. Niepełny przelew nadal ma zostać odrzucony jawnie, bez cichego obcinania.
- `app/build.gradle`, commit `93e7ee39` — numer 0.8.0.77/versionCode 269. [CI #2065](https://github.com/edwinkarolczyk/Edhome/actions/runs/37918452566) w toku podczas aktualizacji wpisu.
- **Ryzyko nierozwiązane:** inne nieznane układy PDF, przelewy bez rozpoznawalnego kierunku/kwoty, wiersze w nietypowej kolejności i rozbieżność dat (księgowania/transakcji). Testy syntetyczne nie potwierdzają poprawnego importu 100% realnej historii. Brak automatycznego potwierdzania wpływów/wydatków bez jednoznacznego dowodu; saldo nie może być naliczone dwukrotnie. Stable `main` nietknięta.

## P0 — historia bankowa VeloBank: znikające nowsze przelewy w kolejce (09.10.2026)

**Zgłoszenie z telefonu:** okno „Banki i potwierdzenia • Wszystkie” pokazało 70 operacji otwartych z najnowszą datą 2026-10-05, choć użytkownik ma w eksportowanym pliku operacje nawet z 2026-10-09. Opis pozycji 2026-10-05 zawiera datę 07.10.2026, co wymaga weryfikacji, czy są to daty operacji i księgowania; bez surowego PDF nie przyjmować automatycznie, że któryś zapis jest nieprawidłowy.

**Potwierdzone w kodzie:** `showBankEvidenceQueue()` wykonywał `BankEvidenceStore.count(...,"open",filter)` i `listPage(...,"open"...)`, więc napis „Wszystkie” dotyczył tylko otwartych, nie rozliczonych/odrzuconych. `BankStatementVeloPdf.parseStatementRows()` pomijał bez powiadomienia datowane linie przelewów przy braku jednoznacznej kwoty i kierunku w tej samej linii tekstowej PDF. Nie ustalono jeszcze, czy konkretne 09.10 występują w źródłowym PDF oraz czy miały status `matched`.

**Naprawy wyłącznie `beta`:**
- `BankEvidenceStore.java` commit `8e347c7`: read-only `countAll()` i `listPageAll()` po WSZYSTKICH stanach (otwarte, uzgodnione, odrzucone), bez migracji danych.
- `MainActivity.java` commit `f46985b`: odrębny przycisk „Cała historia bankowa”, paginacja i oznaczenia statusów; dawne „Wszystkie” jednoznacznie zmienione na „Wszystkie otwarte”; po imporcie komunikat z liczbą wpływów/wydatków i zakresem dat rozpoznanych operacji oraz ostrzeżeniem o możliwych pominięciach w PDF.
- `BankStatementVeloPdf.java` commit `bc7655e`: gdy parser ma więcej niż jeden rozpoznany wiersz, ale dostrzegł równocześnie datowane linie przypominające nieodczytane przelewy/operacje, odrzuca import całego pliku z komunikatem (zamiast cicho częściowo go zapisać). Rozpoznawanie jest konserwatywne, nie gwarantuje wykrycia wszystkich wieloliniowych układów PDF.
- `BankStatementVeloPdfSmoke.java` komity `6d6d91a` i `e7e9424`: regresja „przelew datowany 09.10 zapisany w rozbitych liniach nie może zniknąć”.
- `app/build.gradle` commit `3da9abf`: Beta **0.8.0.76/versionCode 268**; poprzedni build 0.8.0.75 #2053 pomimo kompilacji nie został opublikowany, bo wersja wydania była już zajęta. CI **#2059 FAIL (przestarzały kontrakt UI); #2062 PASS, podpisany APK 0.8.0.76 opublikowany. Następna korekta 0.8.0.77/269 jest nadal w CI #2065**.

**Pozostałe P0 przed odbiorem:** pobrać przykładowy rzeczywisty plik PDF/CSV VeloBanku obejmujący 09.10 i porównać liczbę przelewów/ich daty z „Rozpoznane operacje”, „Data najnowsza” i „Całą historią”; sprawdzić dopasowane, odrzucone, pozycje w PayCheck, wpływy, różnicę dat operacji/księgowania. Bez pliku nie wolno twierdzić, że wszystkie operacje od 09.10 zostały naprawione. Sprawdzić #2059, wydać APK, fizyczny test na Androidzie, backup i sync; Stable `main` pozostaje nietknięta.

## P1 — wybór banku z listy w imporcie PayCheck (09.10.2026)

**Zgłoszenie:** użytkownik nie chce ręcznie wpisywać nazwy banku przy każdym imporcie; dotychczasowe formaty bankowe to mBank i VeloBank.

**Zmiana tylko Android `beta`, commit `75e360b3b2fe793338cde514b2ded23228c4da35`:**
- `MainActivity.selectStatementCsv()`: zamiast pola tekstowego pokazuje listę `mBank`, `VeloBank`, `Inny bank (CSV / XLSX)`. Dwa pierwsze wybory bez wpisywania nazwy; następnie systemowy wybór pliku/plików. „Inny bank” zachowuje ręczne pole do pierwszego wpisania nazwy i zapamiętuje ostatnią nazwę niestandardową.
- Dotychczasowa automatyczna identyfikacja natywnych formatów mBank i tekstowych PDF VeloBanku pozostaje bez zmian; wybór nazwy w interfejsie jest istotny zwłaszcza dla zwykłego CSV, bo wpływa na `evidence_key` i deduplikację.
- **Uwaga migracyjna:** jeśli w starszej wersji użytkownik wpisywał inną nazwę tego samego banku (np. `Velo` zamiast `VeloBank`) dla zwykłego CSV, przejście na nowy kanoniczny wybór może wygenerować inny klucz operacji. Przed uznaniem deduplikacji za zamkniętą potrzebny test takich aliasów lub bezpieczna migracja, bez samoczynnego zaksięgowania operacji.
- **Stan weryfikacji:** kod zapisany w repo `beta`; Android CI #2053 zakończył się FAIL przy próbie powtórnego opublikowania istniejącej wersji 0.8.0.75; zmiana listy banków została następnie zintegrowana i przeszła CI #2062 PASS wraz z 0.8.0.76. Nadal nie przeprowadzono odbioru na telefonie. Ostatni wcześniej potwierdzony APK pozostaje 0.8.0.75/267 (CI #2052).

## P0 — automatyczny Budżet → PayCheck → wyciąg (09.10.2026)

**Doprecyzowana decyzja użytkownika:** wydatki i wpływy dodawane są wyłącznie w Budżecie; mają trafiać **automatycznie** do `PayCheck → Do potwierdzenia`. Importowane historie bankowe są porównywane i jednoznaczne operacje mają się potwierdzać i rozliczać automatycznie, a wieloznaczne pozostawać do decyzji człowieka. Bez dopisywania niezależnych transakcji ręcznie w PayCheck.

**Kod Beta 0.8.0.75/267 — kandydat, nieodebrany:** 
- `MainActivity.ensureBudgetPaycheckPending(month)` tworzy idempotentne wpisy `PaycheckStore.add(...)` ze stabilnym ID po `item.id+month` w chwili wejścia do PayCheck/Budżetu, po dodaniu pozycji Budżetu i przy weryfikacji importu za dany miesiąc; stan `pending`, bez wpływu na saldo. Główny formularz ręcznego dodawania niezależnej transakcji PayCheck ukryty/usunięty.
- `autoSettleImportedBankEvidence` po zapisie zaimportowanej historii dopasowuje wyłącznie istniejący, stworzony z Budżetu `pending` o identycznej kwocie/znaku/miesiącu i silnym dopasowaniu nazwy odbiorcy (test `BankBudgetMatchRules`). Jedna pozycja↔jedna operacja; wielokrotne bankowe dopasowanie, identyczne kwoty różnych odbiorców, brak opisowej identyfikacji i tekstowe Velo PDF — do ręcznej weryfikacji, bez automatycznego zatwierdzenia. Nie korzysta z niepotwierdzonych powiadomień bankowych jako dowodu.
- `BankEvidenceStore.match` jednorazowo przestawia `pending → confirmed` i rezerwuje unikalny `evidence_key`; następnie `PaycheckMonthlyBudget.match` przypisuje płatność do rachunku, nie księgując po raz drugi. Nie wprowadzać do Stable bez fizycznego testu rzeczywistych danych bankowych.
- Otwarta uwaga: pliki wyciągów są użytkownika, nie są uwierzytelnionym API bankowym. Dopasowanie jest konserwatywne i może wymagać ręcznej decyzji także przy rzeczywistej zgodności. Jeżeli przypisanie do planu nie powiedzie się już po zatwierdzeniu księgi, raport i diagnostyka mają ujawniać problem; ręczne przypisanie potwierdzonej transakcji musi pozostać dostępne.
- Regresja: `BankBudgetMatchSmoke.java`, `check_budget_bank_automatch_android.py` + aktualizacja kontraktu wcześniejszego przepływu. CI / podpisanie APK / test urządzenia do potwierdzenia.

**Weryfikacja publikacji 0.8.0.75/267:** commit implementacji `c0b7bc032adee8b5f6a439672efd164652e7e09f`, kontrakt zaktualizowany w `785b708215e3a19ea63234a72e2d1f8401605e92`; [Android CI #2052](https://github.com/edwinkarolczyk/Edhome/actions/runs/37912832678) **PASS** (108 pomyślnych kroków), [Release z podpisanym APK](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.75), wersja 267, SHA-256 `da2a99c427d31fe92edf701b415bb14b23f0ba1d3f5f2a0e25ff5ccc003e9736`. Brak fizycznego testu na realnych wyciągach, **automatyczne dopasowanie należy traktować jako prototyp wymagający odbioru**. Dla tekstowego PDF VeloBank świadomie pozostaje ręczne rozstrzyganie; nie importuje się na ślepo do salda. Tylko Beta; Stable main bez zmian.

## P0 — Android: import całej historii bankowej (09.10.2026)

**Zgłoszenie:** mimo wcześniejszego naprawienia widoku historii PayCheck, użytkownik nadal nie może zaimportować pełnego wyciągu bankowego. **Audyt kodu wersji 0.8.0.73:** import jest niezależny od stronicowanej księgi PayCheck, miał ograniczenia 256 KB / 250 pozycji *na plik i łącznie na partię*, 5000 pozycji w trwałej kolejce i niewidoczny ogon po pierwszych 500. XLSX zatrzymywał się przy 2000 wierszy; tekstowy PDF mógł zostać uznany za pojedyncze potwierdzenie przez priorytet parsera, mimo tabeli z wieloma operacjami. Import całych plików działał synchronicznie na głównym wątku, co mogło blokować UI.

**Kandydat Android Beta 0.8.0.74 / 266** (tylko `beta`; przed CI):
- CSV/mBank: limit bezpiecznego odczytu **8 MB / 25 000 operacji na plik**; 10 plików na wybór; do **50 000 różnych operacji w partii**. Dalsze pliki można importować osobno. Limity jawne i brak cichego obcięcia; błąd przy przekroczeniu limitu i atomowy brak częściowego zapisu danej partii.
- Trwała kolejka `bank_evidence_queue`: limit techniczny 200 000 wpisów. Odpytywanie **count()** (bez podglądowego LIMIT 500); **listPage()** z filtrowaniem w SQLite *przed* `LIMIT 100 OFFSET`, zarówno kolejka otwarta, jak i odrzucona. Widać zakres i liczbę operacji, kolejne/poprzednie strony, wpływy/wydatki i filtry dopasowania.
- XLSX: do 12 000 wierszy w rozpoznawanym arkuszu, 16 MB rozpakowanych danych; dla większych historii eksport CSV. VeloBank PDF: do 25 000 rozpoznanych wierszy; parsowanie całej tabeli ma pierwszeństwo przed pojedynczym potwierdzeniem. **Nadal wymagany tekstowy, rozpoznawalny format PDF — brak OCR dla skanów.**
- Odczyt, parsowanie i zapis dużej partii wykonują się poza wątkiem UI. Dialog importu pokazuje sumę rozpoznanych, nowych oraz duplikatów; odrzucona partia ma jawny błąd i nie kasuje wcześniej zaimportowanych danych.
- **Bezpieczeństwo finansowe:** import wyciągu dopisuje jedynie dowody w `bank_evidence_queue`, **nie tworzy ani automatycznie nie potwierdza transakcji PayCheck**. Ręczne parowanie, brak duplikatów i saldo tylko przy zatwierdzeniu zachowane. Automatyczne uzgadnianie jednoznacznych bankowych dopasowań według ostatnich ustaleń użytkownika **pozostaje osobnym, nieukończonym wymaganiem**.
- Testy regresyjne: Java `BankStatementLongHistorySmoke` — CSV 3500, mBank 1600, XLSX 2200, Velo PDF 350 operacji, test stabilnych ID i duplikatów; Python `check_bank_large_history_android.py` — kompletność 3001 wierszy i SQL-paginacja filtrów. Kontrakty CSV, bank queue zaktualizowane. Android CI musi potwierdzić wynik.

**Wynik wydania 09.10.2026:** commit zmian `3e99fc04ecdcf17a3b70a8c83f5246f78b7c684c`, poprawka bezpiecznych callbacków UI `2bfef5b1d96db0bd89cf49e4203c88673679be9d`. [Android CI #2050](https://github.com/edwinkarolczyk/Edhome/actions/runs/37910463724) **PASS — 107 kroków pomyślnych**, w tym długie wyciągi CSV 3500, mBank 1600, XLSX 2200, Velo PDF 350 oraz Android 5.1/API22 lint i kompilacja. [Release 0.8.0.74](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.74) z podpisanym `edhome-beta.apk` (13 334 040 bajtów), SHA-256 `eaf132aef96e07aa7ec6079150176b9f1fa3dfd2ee8eddc765b59b661714b22d`. Skorygowano opis „Co nowego” w manifeście. **CI nie jest testem kompletnego pliku bankowego użytkownika.**

**Nierozwiązane i do odbioru:** prawdziwy plik bankowy użytkownika (jeśli układ nie odpowiada rozpoznawanym CSV/mBank/XLSX/PDF, sam większy limit nie wystarczy), liczba operacji w pliku vs „rozpoznane / zapisane / duplikaty”, historia wszystkich stron, restart telefonu, backup→restore (obecny format kopii JSON nadal ma odrębny limit 32 MB; przy ogromnych historiach może wymagać przebudowy), synchronizacja Desktop↔Android i parowanie z Budżetem. `main` bez zmian.

## P0 — Budżet → PayCheck / Do potwierdzenia (09.10.2026)

**Zgłoszenie:** użytkownik oczekuje, że pozycja Budżetu trafi do PayCheck do obsłużenia i potwierdzenia. Audyt Beta 0.8.0.72: `showBudgetItemDialog` wykonuje tylko `PaycheckMonthlyBudget.add`; PayCheck wyświetla jedynie niezależną kolejkę `paycheck_transactions` oraz przycisk dopasowania *już istniejącego* przelewu do Budżetu. **Brakowało automatycznej ekspozycji pozycji planu w PayCheck i przejścia Budżet → PayCheck.**

**Naprawa przygotowana dla Android Beta 0.8.0.73/265** (tylko `beta`):
- Po otwarciu PayCheck automatyczna sekcja **„Z Budżetu miesiąca • do obsłużenia”** pokazuje wszystkie aktywne, nierozliczone pozycje planu bieżącego miesiąca, osobno ze znakiem wpływu `+` albo wydatku `−`. Lista jest wyliczana z danych Budżetu; **samo wejście nie dodaje wpisów do `paycheck_transactions` i nie zmienia salda**.
- Przy pozycji można **wybrać istniejący przelew** (w tym potwierdzony/importowany) albo **zgłosić do PayCheck** nową operację `pending`. Wyraźna informacja, że zgłoszenie nie oznacza potwierdzenia bankowego. Dopiero świadome `pending→confirmed` i przypisanie do rachunku aktualizują saldo raz i historię Budżetu. Przycisk do zgłoszenia także w rozwiniętej pozycji Budżetu.
- Stabilny `operation_id` z `item.id + miesiąc` uniemożliwia ponownemu kliknięciu stworzenie duplikatu tego samego zgłoszenia; istniejąca transakcja jest kierowana do potwierdzenia/przypisania bez drugiego księgowania. Częściową kolejną płatność można wybrać spośród **innych istniejących przelewów**.
- W Budżecie dodano jawny przycisk „← Wróć do PayCheck”. Dla innych miesięcy nie tworzymy na ślepo bankowego przelewu z bieżącą datą; historia może użyć istniejących transakcji.
- Nowy kontrakt `tests/check_budget_to_paycheck_flow_android.py` kontroluje brak automatycznego księgowania, UI, ID oraz zmianę salda tylko po potwierdzeniu na przykładowej bazie SQLite. Dodany etap w `.github/workflows/android-beta.yml`.
- **Nieodebrane:** fizyczne testy Android: wpis planu od razu widoczny w PayCheck, wejście/wyjście, wykrywanie już istniejących przelewów, przejście `pending→confirmed`, dokładnie 1× saldo, dopasowanie do pozycji, rata/cykl/wpływ/pozycja opcjonalna, nadpłata/niedopłata i synchronizacja Desktop. CI i publikacja muszą być osobno potwierdzone przed deklaracją sukcesu. `main` bez zmian.
- **Weryfikacja wydania 09.10.2026:** commit przepływu `a8807ed08aef2f08801392d73e22225242166abd`; poprawka testu `08dd9d5b4da1e06e84e4829ae60894d363497f91`. Android CI [#2048](https://github.com/edwinkarolczyk/Edhome/actions/runs/37902154090) **PASS** (107 zakończonych kroków sukcesem); [Release Beta 0.8.0.73](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.73) zawiera podpisane `edhome-beta.apk` (13 329 950 B). Manifest wersji 265, SHA-256 `7d49e5a728f63321e5c717c829659646ada82a9d7f48481dd2cf2ce7914297ba`, opis „Co nowego” poprawiony. **Nie oznaczać testów na realnym telefonie jako zaliczonych bez potwierdzenia.**

## P0 — Historia PayCheck i znaki kwot (09.10.2026)

**Zgłoszenie użytkownika:** PayCheck / Budżet nie pokazuje całej historii, szczególnie wpływów; każdy wydatek ma mieć jawny znak **−**, a każdy wpływ **+** przed kwotą. Nie wolno traktować tego jako prośby o odwrócenie znaków w bazie: `amount_grosz` zostaje dodatnią wartością, `kind` określa kierunek.

**Audyt kodu:**
- Android: stary ekran historii PayCheck miał limit 40 ostatnich operacji (potwierdzone wcześniejszym audytem). Kolejka 60 oczekujących z etapu 6 nie zastępuje całej historii. `PaycheckMonthlyBudget.sharedActual` iteruje po wszystkich operacjach danego statusu i liczy osobno `income`/`expense`, ale to nie dowodzi, że lista historii wyświetla wszystkie wpisy. Kod olbrzymiego `MainActivity.java` nie został udostępniony przez API `fetch_file` (pusta treść mimo istniejącego SHA) i **nie wykonano poprawki UI Android**.
- Desktop: `EdhomeDesktop.paycheckTransactionsView` pobierał wszystkie transakcje wspólne ze snapshotu, ale celowo urywał renderowanie po 100. Ten błąd widoczności nie usuwał rekordów.
- Import na PC: wykrywanie kierunku w ogólnym CSV i mBanku rozpoznawało minus ASCII, ale nie minus typograficzny `−`; wydatek mógł być błędnie przedstawiony jako wpływ.

**Zmiany tylko `beta`:**
- `EdhomeDesktop.java`: historia wszystkich operacji ze snapshotu dostępna porcjami po 100 (przycisk „Pokaż kolejne 100”, aktualny licznik); brak twardego ucięcia przy 100. Commit `76a8c5b`. Znak w głównej liście `+ / −` już istniał, bez zmiany księgowania.
- `DesktopBudgetMirror.java`: `+ ` przy planowanym wpływie i `− ` przy planowanym wydatku, także w nagłówku Budżetu i kartotece odbiorców. Commit `4a2ee455`.
- `DesktopBankImporter.java`: normalizacja `−` / `-` i twardej spacji w wyciągach CSV i mBank. Commit `3a69aa64`.
- Testy: dodano `DesktopBankAmountSignTest` (3 transakcje +, -, −); `DesktopBudgetMirrorTest` i kontrakt `check_paycheck_budget_stage6_desktop.py` kontrolują widoczność znaków i stronicowanie. Commity `6e05d9a`, `0fc99a9`, `0f102b0`.
- Kandydat Desktop Beta **0.7.0.115**, wersja spójna w klasie, Gradle i Desktop workflow; ostatni commit wydaniowy `cc56a74b`. **CI nie zostało jeszcze zweryfikowane, nie ogłaszać gotowego instalatora.**

**OTWARTE / nie uznawać za naprawione:** (1) Android — pełna historia bez limitu 40, stronicowanie i jawny podział wpływy/wydatki ze znakami we wszystkich listach oraz szczegółach; (2) stwierdzić, czy brakujące stare wpływy faktycznie istnieją w bazie/snapshot po LAN oraz porównać sumy i ilości po każdej stronie, bez przepisywania starych danych; (3) rozróżnić błędy widoczności od błędów importu/synchronizacji i przeprowadzić test na rzeczywistym telefonie; (4) zweryfikować Desktop CI 0.7.0.115 i testy jednostkowe. **Nie ruszać `main` i nie usuwać historii.**

### Android — korekta historii PayCheck i znaków (+ / −), kandydat 0.8.0.72 / 264

**Odczyt rzeczywistego kodu:** mimo ograniczeń zwykłego `fetch_file` pełny `MainActivity.java` pozyskano przez GitHub `fetch_blob`, SHA poprzedni `fdbdc5d3`. Zidentyfikowano w kodzie `paycheck()` zapytanie `ORDER BY id DESC LIMIT 40`, w `showBudgetItemHistory()` obcięcie do `Math.min(events.size(),100)`. Dane historyczne nie były usuwane przez te zapytania, jedynie niewyświetlane. **Nie wiadomo jeszcze, czy brakujące wpływy użytkownika są już w lokalnej bazie — to wymaga porównania z bankiem / PC i kopią.**

**Wdrożenie kandydujące na gałęzi `beta`:**
- Android PayCheck ma jawny przycisk **„Pełna historia • wpływy + / wydatki −”**, widoczny obok podglądu 40 najnowszych operacji; ten sam dostęp jest z Budżetu miesiąca. Filtry: wszystkie / tylko wpływy / tylko wydatki; pobieranie ze wszystkich `paycheck_transactions` `scope='shared'` bez filtra na `status`, stronicowanie SQLite po **60** z „Następne” / „Poprzednie”, licznik wszystkich operacji oraz wpływów i wydatków. Historia nie jest sztucznie skracana na 40 ani 100; wybór pozycji otwiera datę, kierunek, kategorię, kwotę, notatkę, status, z możliwością świadomego potwierdzenia pending.
- Historia pojedynczej pozycji Budżetu wykorzystuje istniejące `PaycheckBudgetHistoryStore.loadAll` (archiwum SQLite + cache), ale prezentuje wszystkie zdarzenia w stronach po 60 zamiast tylko pierwszych 100.
- Znaki **+ dla wpływów, − dla wydatków** w nagłówku planu, podsumowaniu potwierdzonych/oczekujących, pozycjach Budżetu, ręcznym wyborze płatności i listach historii. Nie zmieniano `amount_grosz`, obliczania salda ani księgi.
- `BankStatementCsv.java` oraz `BankStatementMbank.java` normalizują minus Unicode `−` i twarde spacje przed rozpoznaniem kierunku; dotyczy przyszłych importów, nie przepisuje wcześniej zatwierdzonych danych.
- Nowy test `tests/check_paycheck_history_all_android.py`: filtr, paginacja, znaki i symulacja SQLite ponad 180 operacji. Uruchamiany na Android CI razem z wykonywalnym Java `tests/task_rules/BankStatementSignsSmoke.java`, który parsuje prawdziwe przykłady CSV/mBank `+`, `-`, `−`.
- **Wydanie Android Beta 0.8.0.72/264: CI #2046 PASS, podpisany APK i Release opublikowane 09.10.2026, test użytkownika nadal nieodebrany.** Potwierdzić CI, podpisanie oraz na realnym telefonie: stare wpływy, obie strony paginacji, wszystkie/filtry, historia jednej pozycji ponad 100 zdarzeń, znak w każdej wyświetlanej kwocie, poprawny bilans oraz LAN Android↔PC bez duplikatów. Nie używać prawdziwych danych do testowych operacji finansowych.

**09.10.2026 — kontrola po buildzie:** [Android CI #2046](https://github.com/edwinkarolczyk/Edhome/actions/runs/37897468257) zakończył się `success`; w artefaktach jest `EDHOME-0.8.0.72-signed-apk`, a [Release 0.8.0.72](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.72) publikuje podpisany plik `edhome-beta.apk`. Manifest `beta-manifest.json` wskazuje wersję 264 i właściwy SHA-256 APK. Poprawiono w manifeście opis „Co nowego” dla tej wersji (wcześniej skopiowany ze starej wersji). **Wciąż wymagane:** próba odczytania brakujących wpływów i potwierdzenia przelewu na realnym Androidzie; backup przed testowaniem i późniejsza kontrola sync PC↔telefon. Nie przenosić do Stable bez akceptacji.

## Aktualizacje Android Beta — porządki na ekranie (09.10.2026)

- **Zgłoszenie z telefonu:** EDHOME Beta `0.8.0.40 / 232`. Stary ekran pokazywał 10 kafelków, w tym powtórny Panel główny, Status kanału, Wersję aplikacji, osobne Instaluj/Pobrany APK, Backup i QR. „Co nowego” zawierało tekst **na sztywno** o układzie Start 3×3, niezależnie od najnowszej wersji.
- **Naprawa kodu:** commit [`ee338e8`](https://github.com/edwinkarolczyk/Edhome/commit/ee338e84c2185da7b1274beea1126aa24e08ff1b), wyłącznie `beta`. `MainActivity.updates()` wyświetla zwięzły nagłówek wersji i 4 działania: Sprawdź aktualizację, Instaluj APK (jeżeli pobrano plik, użyj go; inaczej wybierz ręcznie), Co nowego, Opcje zaawansowane. Usunięto dodatkowy dolny kafelek Panel główny, kopię, wersję i status — **funkcje pozostają dostępne w innych miejscach**. QR pobierania przeniesiono do opcji zaawansowanych; tam link Beta preferuje najnowszą poprawnie odczytaną wersję manifestu.
- **Źródło prawdy dla opisu:** `BetaUpdater.showLatestChanges()` czyta manifest HTTPS z bieżącego kanału, waliduje `channel=beta`, `versionCode`, adres APK i SHA-256, pokazuje `changelog` przypisany do wersji. Ostatni zweryfikowany opis jest zapisywany lokalnie. Przy braku internetu pokazuje zapisany opis **wyraźnie oznaczony jako potencjalnie nieaktualny** lub informuje o niedostępności; bez tekstów reklamujących stare kafelki.
- **Dodatkowo:** poprawiono nieprawidłową informację „kontrola co 30 sekund” — rzeczywisty interwał automatycznej kontroli to około 15 min (30 s dotyczy taktu sprawdzania). Schemat SQLite i dane niezmienione. Numer kandydata `0.8.0.65 / 257`. Opis wydania w Android workflow odświeżony.
- **Testy przeprowadzone:** przed commitem kontrola zakresu zmian i statycznych warunków (stary tekst usunięty, 4 działania, prawdziwy changelog, QR zachowany, numer kompilacji). **To nie zastępuje kompilacji ani odbioru na urządzeniu.** CI [#2017](https://github.com/edwinkarolczyk/Edhome/actions/runs/37880359413) **FAIL** na starej asercji oczekującej kafelka QR w poprzednim miejscu; dopasowano regresję do nowego UX: [commit `f026fda`](https://github.com/edwinkarolczyk/Edhome/commit/f026fda16bd93b8d6838210a27ce5cbe7d3dddc5). Ponowny Android CI [#2018](https://github.com/edwinkarolczyk/Edhome/actions/runs/37880487760) w toku; test instalatora i inne regresje PASS, pełna kompilacja i publikacja nadal w toku.
- **Do zamknięcia:** (1) zielony build i opublikowany podpisany APK 0.8.0.65; (2) na telefonie sprawdzenie, czy są dokładnie 4 działania i nie ma starych kafli; (3) „Co nowego” z działającym internetem, przy braku internetu i przy nieaktualnej wersji telefonu; (4) QR w zaawansowanych, ręczna instalacja i odtwarzanie gotowego APK; (5) sprawdzić, że PayCheck, Projekty, Magazyn i LAN sync zachowały dane. **Stable `main` nietknięty.**

**Korekta na wyraźne życzenie użytkownika (09.10):** osobny kafelek **QR Beta / Stable** zostaje też na głównym ekranie Aktualizacji (łącznie **5 działań**: Sprawdź, Instaluj APK, Co nowego, QR Beta / Stable, Opcje zaawansowane). Dodatkowo QR pozostaje w Opcjach zaawansowanych. Commit [`8999850`](https://github.com/edwinkarolczyk/Edhome/commit/899985019780e91c1ce80cc3b1157b68af8cb8a1) aktualizuje kod i test kontraktowy, podnosi wersję do **Android Beta 0.8.0.66/258** w celu uniknięcia konfliktu z ewentualną publikacją 0.8.0.65. Nie oznaczać jako gotowego przed CI i publikacją podpisanego APK; `main` bez zmian.

**Druga poprawka P0:** przegląd tej samej klasy ujawnił jeszcze osiem okien wyboru z `setMessage` + `setItems`: rozstrzyganie kilku powiadomień o płatności, lista dopasowania wyciągu i wybór operacji, automatyczne dopasowanie przelewu do Budżetu, wybór nadpłaty, zakres zamknięcia, kartoteka odbiorców oraz korekta/zakończenie zobowiązania. Wszystkie osiem korzysta teraz z `setCustomTitle(paycheckChoiceDialogTitle(...))`, który pokazuje tytuł i do trzech wierszy instrukcji **nad klikalną listą**, bez ryzyka zasłonięcia `setItems`. Wcześniejsze dwa wybory przelewów nadal mają listę i osobny dialog świadomej zgody. Nowy test `tests/check_paycheck_choice_dialogs.py` w Android CI oraz kontrola 10 list.

**Właściwy pakiet do odbioru P0:** Android Beta **0.8.0.71/263**, [CI #2037](https://github.com/edwinkarolczyk/Edhome/actions/runs/37888963571) — oczekuje/w toku. Starsze 0.8.0.69 i 0.8.0.70 mają podzbiór tych poprawek. Dopiero po zielonym CI i podpisanym APK: na rzeczywistym Androidzie sprawdzić klikalność list, pending→confirmed, świadome dopasowanie i saldo 1×. **5C i etap 6 nadal bez fizycznego odbioru.**

## Etap 6 — P0 ukryte listy wyboru przelewu (09.10.2026)

**Diagnoza po zgłoszeniu:** Android 0.8.0.68 wprowadził nowe listy oczekujących i wybór transakcji do rachunku, ale w `MainActivity.showSharedPaycheckPendingQueue` i `showBudgetPaymentPicker` dialogi miały jednocześnie `AlertDialog.Builder.setMessage` i `setItems`. W natywnym `AlertDialog` komunikat może zająć panel przeznaczony na listę, przez co użytkownik nie ma czego kliknąć. To istotny kandydat na rzeczywistą przyczynę niemożności potwierdzania, choć **zachowanie na urządzeniu użytkownika wymaga potwierdzenia**.

**Poprawka:** usunięto `setMessage` jedynie z dwóch okien wyboru; pozostają tytuły, widoczne pozycje, stronicowanie, przycisk Zamknij, a następny osobny dialog zachowuje informację o sprawdzeniu przelewu w banku i przycisk świadomego potwierdzenia. Nie zmieniano tabel, księgowania, historii ani synchronizacji. Test regresyjny `tests/check_paycheck_pending_contract.py` pilnuje list bez komunikatu i utrzymania dialogu potwierdzenia.

**Kandydat finalny serii P0:** Android **0.8.0.71/263**, [build #2037](https://github.com/edwinkarolczyk/Edhome/actions/runs/37888963571), łączy naprawę 10 list i `PaycheckBudgetAllocationGuard`. Poprzednia 0.8.0.70 miała CI #2032 PASS, ale naprawiała tylko pierwsze dwa okna. Odbiór fizyczny B6 i A1–A20 nadal otwarty; Stable `main` bez zmian.

## Etap 6 — P0 powtórne wykorzystanie przelewu (09.10.2026)

**Zidentyfikowany defekt w kodzie:** `PaycheckMonthlyBudget.allocatedForOperation` używana przez `allocateMatch` zliczała tylko `matchedAllocationsGrosz`. Gdy operacja była wcześniej przypisana **w całości** przez `match` (bez osobnej alokacji), wynik wynosił **0 gr** i późniejsze częściowe przypisanie tej samej transakcji mogło przejść. Dodatkowo ignorowano `splitSurplusesGrosz`. To narusza warunek jednego rozliczenia i wymagało poprawki mimo wcześniejszych zielonych testów.

**Naprawa `beta`:** nowy `PaycheckBudgetAllocationGuard.addUsed` sumuje dla każdej powiązanej pozycji pełną kwotę przy braku jawnej alokacji, jawne przydziały i wydzielone nadpłaty, odrzuca osierocone i ujemne kwoty oraz wykorzystanie większe niż potwierdzona transakcja. `PaycheckMonthlyBudget.allocatedForOperation` korzysta z niego podczas `allocateMatch`. Test wykonywalny `PaycheckBudgetAllocationGuardSmoke.java` oraz kontrakt `check_paycheck_full_allocation_guard.py` podłączone do Android CI.

**Wersje:** zainstalowana u użytkownika 0.8.0.68/260 — bez tej ostatniej ochrony w lokalnej ścieżce ręcznego podziału. Zbiorczy kandydat Beta `0.8.0.70/262` zawiera tę poprawkę oraz poprawę widoczności dialogów. Oczekuje na CI #2032 i publikację. **Nie wykonywać testów celowego podwójnego przypisania na prawdziwej księdze.** Fizyczny odbiór pending→confirmed oraz 5C A1–A20, etap 6 B1–B12 nadal OTWARTE. Stable `main` bez zmian.

## P0 — potwierdzanie przelewów, zgłoszenie z telefonu (09.10.2026)

**Stan odbioru: ZABLOKOWANY.** Użytkownik zgłosił, że nie udało mu się potwierdzić żadnego przelewu. Zielony CI nie jest dowodem działania na telefonie. Nie oznaczać punktów 9–14 kontraktu 25/25 ani scenariusza B6 jako odebranych przed fizycznym testem.

**Co wykryto w kodzie:** potwierdzanie dostępne było przy transakcjach w historii PayCheck (tylko 40 ostatnich), bez bezpośredniego przycisku w Budżecie. Automatyczne sugerowanie dopasowań wymaga wyraźnej zgodności kwoty/kategorii; potwierdzona płatność bez dopasowania pozostawała poza rachunkiem. To potwierdzone luki obsługi, ale **nie jest jeszcze udowodnioną przyczyną** nieudanego zapisu na urządzeniu użytkownika.

**Kandydat 0.8.0.68/260:** (1) jawna kolejka oczekujących przelewów w PayCheck i Budżecie, po 60 szt. na stronę, bez limitu historii 40; (2) w rozwiniętym rachunku akcja „Rozlicz / przypisz przelew”, lista nieprzypisanych operacji tego samego rodzaju i miesiąca; (3) osobny dialog świadomego potwierdzenia pending i przypisania lub przypisania istniejącej confirmed, bez nowego INSERT w księdze; (4) sprawdzanie rezultatu, logi PAYCHECK_PENDING_QUEUE / PAYCHECK_BUDGET_PAYMENT_PICKER / PAYCHECK_BUDGET_MANUAL_CONFIRM; (5) osobny komunikat, gdy PayCheck został potwierdzony, a zapis przypisania się nie udał. Nie usuwano historii, migracji ani danych istniejących.

**Obowiązkowy test:** nowy wpis pending → lista → ręczne potwierdzenie → saldo 1× → podpięcie do rachunku → spadek „do zapłaty” bez zmiany salda drugi raz → historia → restart. Osobno sprawdzić istniejący już confirmed, ponowne wskazanie tego samego przelewu, różnice kwoty, konflikt, import bankowy oraz synchronizację Desktop→Android. Na błędzie pobrać diagnostykę, bez ujawniania danych bankowych. **Nie zmieniać `main`**.

## Etap 6 — Android, zwarty bilans (09.10.2026)

**Wdrożenie kodowe Beta 0.8.0.67/259 (kandydat):** Android Budżet miesiąca ma dwa zwarte wiersze: planowane wpływy i wydatki, różnicę oraz pozostało do zapłaty. Kwoty do potwierdzenia, zaległości i nadpłaty pozostają jawne bez rozwijania. Pozostałe informacje („Wykonanie pozycji Budżetu”, pozostałe transakcje PayCheck, sumy potwierdzone i wyjaśnienie księgowania) są pod przyciskiem **„Szczegóły rozliczenia”**. Stan rozwinięcia zachowuje się podczas przerysowania widoku; zmiana nie zapisuje danych. Sekcja wpływów i wydatków oraz pojedynczo rozwijane pozycje bez zmian.

**CI #2021 — FAIL:** odzyskano wywołanie Budżetu i resztę `MainActivity`, ale regresja wymaga jednego ciągłego tekstu „transakcje potwierdzone po sprawdzeniu banku / wyciągu”. Poprawiono tylko podział literału Java na jeden napis, bez zmiany semantyki. Kolejny CI oczekuje.

**CI #2020 — FAIL:** pierwsza wersja patcha w `MainActivity` objęła dwie sekcje o identycznym początku tekstu i przypadkiem usunęła wywołanie `sharedMonthlyBudgetBlock()`. Żaden APK nie został opublikowany z tej kompilacji. Naprawa: odtworzono poprzedni plik i zastosowano minimalną poprawkę wyłącznie **wewnątrz** `sharedMonthlyBudgetBlock`; porównanie odtworzeniowe potwierdziło nienaruszenie pozostałego pliku. Wersja nadal **0.8.0.67/259**, nowy CI oczekuje. 

**Testy:** rozszerzono kontrakt `check_paycheck_monthly_budget_contract.py` o obecność szczegółów, domyślne zwinięcie oraz jawne zaległości/do potwierdzenia. Nie zmieniano SQLite, tabel ani API synchronizacji. Wymagane: **nowy Android CI oraz fizyczny odbiór B1–B12**; bez tego etap 6 pozostaje nieodebrany. Stable `main` bez zmian.

## Etap 6 — bieżąca seria prac (08.10.2026)

**Wdrożono na Desktop w `beta`:** `DesktopBudgetMirror` pokazuje jeden zwarty nagłówek z polską nazwą miesiąca i planowanymi wpływami, wydatkami oraz różnicą; wpływy są zawsze nad wydatkami; wydatki domyślnie według planowanej daty zapłaty; jest przycisk „Bieżący miesiąc” i „Odbiorcy”. Podgląd kartoteki wyświetla rzeczywistych odbiorców oraz liczbę i planowaną kwotę aktywnych zobowiązań danego miesiąca. Nie tworzono drugiej księgi PayCheck; edycja nazwy i kwoty pojedynczego miesiąca działa dotychczasową ścieżką.

**Testy:** JUnit `DesktopBudgetMirrorTest` poszerzony o dochody przed wydatkami, sortowanie i odbiorców (także podwójne ID); regresja `tests/check_paycheck_budget_stage6_desktop.py` dodana do Windows workflow. **Desktop 0.7.0.114 / build #284 PASS, instalator opublikowany**. Android 0.8.0.64 / #2010 potwierdzony PASS.

**Lista odbiorowa:** [ODBIOR_PAYCHECK_ETAP6_25_25.md](ODBIOR_PAYCHECK_ETAP6_25_25.md) — wszystkie 25 wymagań i testy B1–B12, do wykonania na urządzeniach. 5C nadal wymaga fizycznych testów A1–A20 i backup→restore; dopóki to nie nastąpi, nie oznaczać 25/25 jako przyjęte i **nie dotykać `main`**.

## Aktywny temat 1: PayCheck — Budżet miesiąca

**Kontrakt produktu:** [ROADMAP.md — Budżet miesiąca 1.0, 25/25](ROADMAP.md#paycheck--budżet-miesiąca-10-kontrakt-odbioru-2525). Stan techniczny nie jest równoznaczny z odbiorem wszystkich 25 warunków.

| Część | Status | Potwierdzony zakres / brak |
|---|---|---|
| Etapy 0–4 | Wykonane kodowo według roadmapy; odbiór całości otwarty | Reguły budżetu i zachowania UI; nadal wymagane testy fizyczne i regresyjne |
| **5A — Android / trwałość** | **Kod wdrożony, CI success** | Zgodnie z roadmapą: SQLite v46, tabele `budget_items`, `budget_occurrences`, `budget_recipients`, `budget_credits`, `budget_history`; historia bez limitu 6000 wpisów w archiwum; migracja, backup i protokół `sync-records` |
| **5B — Desktop / odczyt** | **Kod wdrożony, CI success** | Podgląd wspólnego Budżetu z danych Androida; miesiące, faktury i korekty. Stary `paycheck-budget-plan.json` zachowany, **brak pełnej edycji wspólnego Budżetu z PC** |
| **5C — synchronizacja i konflikty** | **W REALIZACJI / P0** | Kod: ochrona historii append-only, kopia ZIP przy konflikcie, edycja istniejącej pozycji PC, per-ID delta Android→Hub dla pozycji/odbiorców/historii, ścisły CAS danych finansowych, kontrola sum alokacji/surplusów i zgodności JSON↔SQLite. Desktop→Android pobiera aktualny snapshot z kontrolą lokalnego CAS. Do odbioru: fizyczny test dwóch urządzeń, weryfikacja wpłat/nadpłat i backup→restore; edycja wpłat/nadpłat z PC nieukończona. |
| Etap 6 — ergonomia i odbiór | **W REALIZACJI** | Desktop 0.7.0.114: zwarty nagłówek, wpływy pierwsze, wydatki wg terminu, kartoteka odbiorców, nawigacja do bieżącego miesiąca. Testy B1–B12 otwarte, Android UX i pełna regresja 25/25 nadal wymagają odbioru |

### 5C — faktycznie wykonane w kodzie (nie mylić z odbiorem)

- **Android:** `DataBackup.restoreJson` przed kasowaniem tabel przechowuje lokalną historię SQL i cache, po imporcie scala zdarzenia append-only w tej samej transakcji. Niezgodny UUID → wyjątek i rollback; po scaleniu odtwarzane metadane `sync_records`. `SyncRecordStore.applyPatch` zabrania usuwania i aktualizacji istniejących zdarzeń `budget_history`.
- **Android / konflikt:** wybranie „Desktop wygrywa” najpierw zapisuje lokalne archiwum ZIP do wewnętrznego katalogu `hub-conflict-backups`; brak udanej kopii przerywa zastąpienie danych. Eksport i przywrócenie tej wewnętrznej kopii z poziomu UI wciąż wymaga osobnego odbioru.
- **Desktop Hub:** przy pełnym snapshot zachowuje poprzednie zdarzenia historii, nie nadpisuje różnej treści tego samego UUID, odrzuca destrukcyjne patche historii również w trybie „Telefon wygrywa”.
- **Desktop PayCheck:** istniejącą pozycję wspólnego Budżetu można edytować z PC (nazwa i planowana kwota pojedynczego miesiąca). Jedna kopia snapshotu aktualizuje legacy JSON ustawień, `budget_items`, `budget_occurrences` i dopisuje `budget_history`. Starszy `paycheck-budget-plan.json` pozostaje nietknięty.
- **Testy:** nowe JUnit `DesktopSharedBudgetEditsTest`, `DesktopBudgetHistoryArchiveTest`; źródłowy test `check_paycheck_budget_stage5c_guard.py`; Windows i Android CI uruchomione dla nowych zmian. **Nie oznaczaj 5C jako zakończonego bez wyników końcowego CI i testu dwóch fizycznych urządzeń.**
- **Wersje:** Android 0.8.0.56/248 — [podpisany APK opublikowany](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.56), CI Android #1950 sukces; Desktop 0.7.0.108 — [instalator Windows opublikowany](https://github.com/edwinkarolczyk/Edhome/releases/tag/desktop-beta-latest), workflow Desktop #249 zakończony sukcesem; ostatnie workflow dla HEAD po zmianach: [Android #1950](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767797526), [Desktop #249](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767797670) — oba success. CI wcześniejszego Android [#1944](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767321384) przeszło, ale nie obejmuje ostatniego patcha konfliktów ani edycji Desktopu.

**Pozostało P0:** (1) dwa kierunki zmian i idempotencja płatności oraz nadpłat; (2) rozstrzyganie konfliktów per pozycja/operacja bez wyboru całego snapshotu, w tym jednoczesnych zmian offline; (3) końcowe testy na fizycznych urządzeniach oraz odbiór backup→restore. Od tej chwili każdą finansową zmianę po obu stronach należy testować na kopiach danych.

### 5C — druga seria: mechanizm przyrostowy i ochrona finansów

- **Android → Hub Desktop:** `DesktopHubSync.buildPatch` oblicza zmiany w trzech kolekcjach ustawień (`paycheckMonthlyBudget`, `paycheckRecipients`, `paycheckBudgetHistory`) **po UUID**, do 500 operacji łącznie z tabelami. Dla innych ustawień lub nieobsługiwanych zmian pozostaje bezpieczny, pełny snapshot z CAS. Skrócenie cache historii nie usuwa zdarzeń archiwalnych.
- **Desktop Hub:** `DesktopBudgetDelta.apply` scala tylko zmienione pozycje/odbiorców i append-only historię na **odłączonej kopii** całego snapshotu. Ta sama pozycja zmieniona offline na obu urządzeniach powoduje konflikt 409; niezależne pozycje mogą się scalić. Retry identycznej historii nie dopisuje drugiego zdarzenia.
- **Integralność wpłat/nadpłat:** `DesktopBudgetIntegrity.assertAllocations` sprawdza łączne wykorzystanie jednej potwierdzonej transakcji, w tym `splitSurplusesGrosz`. `assertSidecars` sprawdza spójność `settings` z `budget_items`, `budget_recipients` i cache/archiwum `budget_history`, zatrzymując niespójny patch przed zapisem.
- **Desktop → Android:** telefon pobiera stan Huba, ale przed jego zastosowaniem weryfikuje, że dane lokalne nie zmieniły się podczas transmisji LAN. Pobieranie nadal wymaga pełnego snapshotu; docelowy przyrostowy download per UUID jest poza tą serią.
- **Konflikt „Telefon wygrywa”:** nie omija kontroli rewizji dla `budget_*` ani `paycheck_transactions`; nie jest automatycznym nadpisaniem księgi.
- **Regresja:** JUnit `DesktopBudgetDeltaTest`, `DesktopBudgetIntegrityTest` (+ poprzednie testy historii), test źródłowy `check_paycheck_budget_stage5c_sync_contract.py`; dodane do workflow obu aplikacji. **Zielony CI ≠ fizyczny odbiór.**
- **Wersje kodu:** Android 0.8.0.58/250, Desktop 0.7.0.110; commity `2cbc838`, `d0ae940`, `0bc8282`, `cbb4c5a`, `d481ad7`, `7680d0f`. Bieżący wynik CI: oczekujący. Nie używać na jedynej kopii danych bez wcześniejszego eksportu ZIP.

### 5C — trzecia seria: zmiany Desktop → Android po UUID

- **Desktop:** `DesktopBudgetOutboundDelta` wysyła edytowane pozycje/odbiorców/zdarzenia jako `budgetDelta` bez dołączania całego snapshotu. Odmawia przy zmianach pozabudżetowych (pozostaje CAS); po ACK pobiera rzeczywisty stan z Androida, nie ufa lokalnej kopii metadanych.
- **Android:** `PaycheckBudgetSyncPatch` waliduje każdy UUID, porównuje wersję poprzednią i docelową, odrzuca konflikty, akceptuje identyczne powtórzenia po zerwanym LAN. Buduje tabele 5A w odizolowanym SQLite i stosuje wynik przez `DataBackup.restoreJson` z istniejącym rollbackiem oraz ochroną historii.
- **Ograniczenia bezpieczeństwa:** paczka mieszana (Budżet + dowolne inne operacje) nie jest wykonywana po części; nadal wymaga oddzielnego przesłania albo kontrolowanego pełnego snapshotu. Nawet po zielonym CI potrzebny jest odbiór na rzeczywistych urządzeniach.
- **Testy:** `DesktopBudgetOutboundDeltaTest` oraz `tests/check_paycheck_budget_stage5c_reverse_patch.py` uruchamiane w GitHub Actions; Android #1978 w toku i [Desktop #269] PASS; build [Android #1978](https://github.com/edwinkarolczyk/Edhome/actions/runs/37773023083) i [Desktop #269](https://github.com/edwinkarolczyk/Edhome/actions/runs/37773023080) — obie zakończone sukcesem.
- **Ujawniony błąd CI (naprawiony w kodzie, ponowna kontrola trwa):** Android #1973 — `JSONObject.valueToString(Object)` nie istnieje w Android API; zastąpiono kanonicznym zapisem wartości obsługiwanym przez Androida.
- **Bieżące wersje robocze:** Android 0.8.0.59/251, Desktop 0.7.0.111. Zmiany w `beta`; `main` nietknięty.

**Odbiór 5C (stan końcowy tej serii):** Android 0.8.0.59/251 i Desktop 0.7.0.111 przeszły CI oraz mają opublikowane paczki Beta. Implementacja przyrostowych zmian Budżetu w obie strony jest gotowa do testów. **Nie zaliczono jeszcze testów A1–A16 na fizycznym telefonie i PC**; do tego czasu 5C pozostaje *kodowo wdrożony, lecz nieodebrany*. Nie wdrażać Stable bez akceptacji.

### 5C — dodatkowa poprawka P0 po aktualizacji na urządzeniach

**Nowa diagnoza:** `PaycheckBudgetSyncPatch.apply` dla zmiany *jednej pozycji* z Desktopu wywoływał `DataBackup.restoreJson` — czyli kasował/odtwarzał wszystkie tabele, w tym Projekty i Magazyn. Przy lokalnej edycji podczas synchronizacji mogło to prowadzić do utraty równoległych zmian. Samo zielone CI #1978 nie wykryło tego ryzyka.

**Poprawka:** odbieranie `budgetDelta` aktualizuje teraz wyłącznie trzy powiązane preferencje PayCheck i pięć tabel Budżetu poprzez `PaycheckBudgetSqliteStore.reconcileRaw`, `SyncRecordStore.ensureAll` oraz lokalną transakcję SQLite. Porównuje dane z tą samą migawką, na podstawie której sprawdziło UUID; odrzuca konfliktowe zdarzenia historii kodem 409 i uszkodzone paczki. Nie ma już globalnego `DataBackup.restoreJson` w tej ścieżce.

**Bezpieczeństwo:** nie uznawać zainstalowanej 0.8.0.59 za końcową wersję 5C; najpierw zweryfikować nowy build 0.8.0.60/252 i test A1–A16 na urządzeniach. Desktop pozostaje 0.7.0.111. Test kontraktowy zabrania regresji do pełnego przywracania bazy w finansowym patchu. Odbiór fizyczny nadal NIEODEBRANY.

**Dodatkowe zabezpieczenie P0:** odbiorca Android sprawdza sumę przydziałów i nadpłat każdej potwierdzonej transakcji PayCheck na podstawie istniejącej tabeli `paycheck_transactions`. Dwie niezależne alokacje, które łącznie przekroczą kwotę przelewu, skutkują 409 i brakiem zapisu. Poprawka dodana do testu kontraktowego 5C. Wersja kandydująca **0.8.0.61 / 253** — wynik CI jeszcze do sprawdzenia; wstrzymać odbiór do publikacji podpisanego APK.

### 5C — dodatkowe zabezpieczenia ponownego parowania QR i backupu (2026-10-08)

- **QR z tym samym PC:** `DesktopHubSync.pair` zachowuje lokalną bazę porównawczą `desktop-hub-baseline.json` i nierozstrzygnięty konflikt; ponowne skanowanie nie traktuje telefonu jako nowego urządzenia.
- **Nowy PC / brak bazowej synchronizacji:** jeśli stan telefonu i Desktopu jest inny, nie ma automatycznej podmiany danych. EDHOME prosi o rozstrzygnięcie. Ręczny wybór „Telefon” stosuje CAS z aktualnym SHA PC, a „Desktop” zachowuje ZIP telefonu i pilnuje, by lokalnych zmian z czasu transferu nie nadpisać.
- **Kopia na PC:** `hubReplace` nie zamieni pełnego snapshotu bez uprzedniego zapisania JSON w `hub-full-replace-backups` (do 20 ostatnich); dzienny backup PC pozostaje osobny. Nieudana kopia przerywa podmianę.
- **Historia:** zdarzenia append-only porównywane semantycznie (`sameJson`), z ignorowaniem kolejności pól JSON, ale wykryciem różnic kwoty i treści; przypadek idempotentnego odtworzenia nie wywołuje pozornego konfliktu.
- **CI:** test kontraktowy `tests/check_hub_repair_history_safety.py` podpięty w Android i Desktop workflow. Aktualne potwierdzone wydania: Android `0.8.0.63/255` — #2002 PASS, Desktop `0.7.0.113` — #279 PASS. A1–A19 **jeszcze nieodebrane na fizycznych urządzeniach**. Gałąź `main` bez zmian.

**Dodatkowe P0 — utrata ACK:** `DesktopBudgetDelta` rozpoznaje identyczne ponowienie tej samej pozycji lub odbiorcy bez duplikowania. `EdhomeDesktop.hubApplyPatch` rozpoznaje identyczny retry po stronie rekordów SQLite `budget_*` tylko, gdy UUID metadanych i rewizja równa `base+1`, treść rekordu jest identyczna (lub to samo usunięcie), a inna edycja nadal powoduje konflikt 409. JUnit `DesktopBudgetDeltaTest` rozszerzony o retry i nowszy konflikt; test `check_hub_repair_history_safety.py` blokuje regresję. CI PASS: Android `0.8.0.63/255`, Desktop `0.7.0.113`. Do odbioru A19 oraz pozostałe testy A1–A18.

**Weryfikacja pakietów 2026-10-08:** oba CI zakończone sukcesem; Android [0.8.0.63 / 255](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.63), Desktop [0.7.0.113](https://github.com/edwinkarolczyk/Edhome/releases/tag/desktop-beta-latest). Wymagane testy fizyczne A1–A19 na kopii danych, zachowanie stanu Projektów i Magazynu po zmianie Budżetu, porównanie wpłat/nadpłat z dokładnością 1 gr, backup ZIP → restore, re-pairing QR. Bez tych testów nie oznaczać 5C/25 jako ukończonych i nie przenosić do `main`.

### 5C — ochrona wykorzystania nadpłat w różnych miesiącach (P0)

**Problem:** wcześniejsze sprawdzenie `verifySharedAllocations` pilnowało, aby przelew nie był użyty podwójnie, ale nie sumowało `creditApplicationsGrosz` pochodzących z tego samego miesiąca źródłowego, rozdzielonych na różne miesiące docelowe. Inny rozkład nadpłat po synchronizacji mógł przejść mimo przekroczenia dostępnego kredytu.

**Zmiana:** Android `PaycheckMonthlyBudget.assertCreditConservation` zlicza odliczenia per `źródło|cel`, porównuje całkowite wykorzystanie źródła z nadpłatą wynikającą z potwierdzonych wpłat i nadwyżki z przelewu grupowego, osobno pilnuje limitu planu w miesiącu docelowym oraz poprawnej kolejności dat. `PaycheckBudgetSyncPatch.apply` wywołuje sprawdzenie przed `reconcileRaw`; błędna paczka kończy się konfliktem finansowym (bez mutacji SQL). Nowa czysta matematyka `PaycheckBudgetCreditMath`; test wykonywalny `tests/task_rules/PaycheckBudgetCreditConservationSmoke.java` uruchamiany przez `tests/check_paycheck_credit_conservation.py` w Android CI.

**Wersja robocza:** Android `0.8.0.64/256` — [GitHub Actions #2010](https://github.com/edwinkarolczyk/Edhome/actions/runs/37841803131) oczekuje w kolejce; #2009 został anulowany przez nowszy przebieg. Desktop `0.7.0.113` bez nowych zmian. Test A20 dopisany do [odbioru 5C](ODBIOR_PAYCHECK_5C_2026-10-08.md). **Przyjęcie na urządzeniach nadal NIEODEBRANE**, Stable `main` bez zmian.

### Zadania do wykonania w etapie 5C

1. Zidentyfikować aktualne ścieżki zapisu i odczytu budżetu Androida i Desktopu; rozdzielić przejściowy full snapshot od docelowego strumienia zmian.
2. Umożliwić edycję wspólnego Budżetu na PC bez utworzenia drugiej niezależnej księgi, bez nadpisywania danych Androida i bez kasowania starego pliku Desktopu.
3. Przesyłać przyrostowo wpisy, wystąpienia miesięczne, odbiorców, nadpłaty oraz historię **w obu kierunkach**; zachować identyfikatory, idempotencję i bezpieczne kasowanie.
4. Rozstrzygać konflikty po zmianach offline na dwóch urządzeniach. Nie stosować bez ostrzeżenia zasady „ostatni zapis zawsze wygrywa”, jeżeli może utracić płatność, korektę albo historię.
5. Chronić kwoty wpłat, nadpłaty, zaległości, ręczne zamknięcia i przeniesienia między miesiącami przed podwójnym naliczeniem.
6. Sprawdzić migrację z poprzednich wersji, backup ZIP → restore i ponowną synchronizację **bez pełnej utraty albo nadpisania danych**.
7. Uruchomić testy kontraktowe, regresję CI, następnie przygotować scenariusz odbioru Android ↔ Desktop na fizycznych urządzeniach (offline, restart, konflikt, ponowne połączenie, restore).

**Nie wolno zamykać 5C wyłącznie po przejściu CI.** Wymagane są wyniki testów użytkownika na urządzeniach i jednoznaczne sprawdzenie spójności danych.

**Karta odbioru 5C:** [ODBIOR_PAYCHECK_5C_2026-10-08.md](ODBIOR_PAYCHECK_5C_2026-10-08.md) — 12 scenariuszy A1–A12, 0 gr różnicy po synchronizacji, test kopii ZIP i konfliktu offline.

## Aktywny temat 2: Projekty — stabilizacja P0 (PRIORYTET NASTĘPNYCH PRAC)

**Stan ogólny:** 🔴 **NIEODEBRANE — naprawy P0 dopiero do wykonania.** Wstrzymać rozbudowę funkcji i P1 do zamknięcia P0. Ustalenie bieżącej rozmowy: najpierw wpisać plan do stałego statusu, **nie rozpoczynać napraw kodu, dopóki użytkownik nie wyda polecenia rozpoczęcia**. 

**Źródła:** [szczegółowy audyt Projektów z 08.10.2026](AUDYT_PROJEKTY_2026-10-08.md), [kontrakt 17 wymagań i 7 scenariuszy odbioru](ROADMAP.md#projekty--kontrakt-działania-i-odbioru-08102026). Przed naprawą zweryfikować aktualny HEAD `beta`, gdyż audyt opisuje wcześniejszy stan kodu. Poniższe statusy to wynik audytu/zgłoszeń, a nie potwierdzenie, że defekty nadal istnieją po późniejszych zmianach innych modułów.

### P0 — lista do wdrożenia w tej kolejności

| ID | Stan | Co należy wykonać | Test / kryterium zamknięcia |
|---|---|---|---|
| **P0-01** | 🔴 Do naprawy | **Android: centralna walidacja wykonania projektowej czynności** — ogólne `Zadania` nie mogą omijać niewykonanych poprzedników, twardych wymagań ani statusu `paused/done` projektu i przodków. Przenieść regułę do wspólnej ścieżki zapisu `DbHelper.completeTask`, zachować stan checkboxa i czytelny komunikat. | Próba ukończenia z ekranu Projekty i Zadania przy niespełnionym poprzedniku, wymaganiu twardym lub wstrzymanym projekcie: odmowa, brak zmiany danych; po usunięciu blokady: sukces. |
| **P0-02** | 🔴 Do naprawy | **Android: bezpieczne Start/Stop** — `completeTask` nie może po cichu wykonać `ProjectStore.stopWork`. Przy aktywnej sesji wymagać osobnego Stop albo jawnego pytania „Stop i zakończ”; zamykać powiadomienie i zachowywać pełną historię czasu; ponowny Start nie resetuje przepracowanego czasu. | Start → próba ukończenia w obu ekranach, stop z aplikacji i powiadomienia, restart, wznowienie, dwie sesje; dokładna suma, brak ukrytego Stop i osieroconego powiadomienia. |
| **P0-03** | 🔴 Do naprawy | **Desktop: wspólne reguły statusu** — ogólny edytor `editRow` oraz przyciski w Projektach mają identycznie blokować wykonanie przy aktywnym timerze, poprzednikach, wymaganiach i statusie wstrzymanego/zakończonego projektu, także nadrzędnego (`desktopProjectWorkBlockReason`). | Zakończenie z każdego miejsca na PC: niedozwolone działania nie zmieniają danych; poprawne wykonanie/Cofnij działa. |
| **P0-04** | 🟠 Do testów E2E / możliwe poprawki | **LAN Android ↔ Desktop** — pełna dwukierunkowa synchronizacja wymagań, zależności, checkboxów, Cofnij, usunięć oraz sesji Start/Stop. Jedna aktywna sesja dla zadania, deterministyczne konflikty offline, brak duplikatów i „powrotów” usuniętych rekordów. Samo „połączono” nie wystarcza. | Test zmian Android→PC i PC→Android, dwa Start offline, konflikt, przerwanie LAN, restart, ponowne połączenie i kontrola identyfikatorów, historii i czasu. Ryzyko jest potwierdzone brakiem odbioru; nie ogłaszać awarii konkretnej ścieżki bez jej odtworzenia. |
| **P0-05** | 🟠 Do testów E2E / możliwe poprawki | **Backup ZIP / restore / migracja** — zachować projekty, podprojekty, wymagania, zależności, rzeczy/pudełka, koszty, czynności, ich wykonanie i wszystkie sesje. Zapewnić atomowość odtwarzania, brak osieroconych wpisów; następnie zweryfikować LAN. | Kopia przed zmianą → odtworzenie w testowej bazie → kontrola powiązań, czasu, historii i sesji → synchronizacja obustronna bez utraty i duplikatów. |
| **P0-06** | 🟡 Poprawka w kodzie; test na urządzeniu nieodebrany | **Crash okna „Projekty → Wymagania”** — zgłoszony w Androidzie 0.8.0.51 (`IllegalStateException`, `MainActivity.showProjectBlockers`, dwa razy `ViewGroup.addView`). Przyczyna: `compactActionRow()` już podpina pasek do `body`, a dialog podpinał go drugi raz. Poprawka zastępuje go lokalnym `new LinearLayout`. | Patch `d1af025`, kontrakt `466247e`, po raz pierwszy przygotowany w 0.8.0.52/244. Trzeba zweryfikować **na aktualnym APK** otwarcie okna z 0, 1 i wieloma wymaganiami, zaznacz/odznacz, Edytuj, Usuń, Zamknij, ponowne wejście; brak `ERROR_UNCAUGHT_main`. Nie utożsamiać zielonego CI z testem fizycznym. |

**P0-01 i P0-02 są krytyczne dla integralności reguł.** Audyt wykazał, że ekran zwykłych Zadań wywoływał `db.completeTask` bez sprawdzenia blokad projektu, a ta metoda mogła automatycznie zatrzymać aktywną sesję. **P0-03** to analogiczna luka w ogólnym edytorze Desktopu. Dla **P0-04/P0-05** najpierw przeprowadzić testy i naprawiać wyłącznie znalezione błędy. **P0-06** jest już poprawione w kodzie, lecz nie ma potwierdzonego odbioru na telefonie.

### Co musi działać po zamknięciu P0

- Hierarchia **projekt → podprojekt → czynności**; brak przechodzenia do `done` z otwartymi zadaniami w podprojektach; poprawne zachowanie `paused`; nazwy projektu i ścieżki czynności.
- Dodawanie, edycja, usuwanie, Wykonane i **Cofnij** (także po przypadkowym zaznaczeniu), z walidacją wszystkich dróg zapisu.
- Zależności: wielokrotny wybór z zaznaczonym aktualnym stanem i jednym **Zapisz**; usuwanie istniejących; blokady, nazwy „Czeka na” i „Odblokuje”, brak pętli, zależności do siebie i między oddzielnymi projektami głównymi.
- Wymagania: kategorie **zakup/materiał, dostawa/oczekiwanie, przygotowanie, zasób, akceptacja i własne**; checkbox zaznacz/odznacz, jawne **Edytuj** i **Usuń** z potwierdzeniem; pola etykieta, rodzaj, opcjonalna data, twarde/miękkie; zmiana daty sama nie oznacza spełnienia; od razu aktualizować blokady i sortowanie.
- Start/Stop i licznik w trwale widocznym powiadomieniu; Stop z powiadomienia; kliknięcie przenosi **do konkretnego projektu i czynności**; historia wielu sesji, pozostały czas, kolory zielony/w-planie, żółty/przekroczenie i Stop, czerwony/przekroczenie i aktywna sesja.
- Kolejność pracy: najpierw **trwające**, potem gotowe odblokowujące inne, potem pozostałe gotowe, zablokowane i wykonane. Czynność nie może pozornie wyglądać na gotową, gdy ma aktywną twardą blokadę.
- Trwałe dane offline, restarty, poprawny backup i wznowienie synchronizacji LAN z PC, bez utraty zmian i bez podwójnego czasu.

### P1 — zarejestrowane, lecz dopiero po P0

| ID | Kolejna poprawka |
|---|---|
| **P1-01** | Desktop: podsumowanie/postęp projektu ma agregować również czynności podprojektów, jak Android. |
| **P1-02** | Desktop: nie wyświetlać „Gotowa do wykonania” przy wstrzymanym/zakończonym projekcie. |
| **P1-03** | Desktop: kolory czasu dokładnie jak kontrakt — zielony, żółty i czerwony; na liście i w podsumowaniu. |
| **P1-04** | Android: domyślne **20 min** dla nowej czynności projektowej również w pełnym formularzu; maksimum 600 min, czytelny `HH:MM` i `0.20 = 20 min`; zachować odrębne reguły zwykłych Zadań. |
| **P1-05** | Desktop: historia wykonania i cofania zgodna z Androidem, bez utraty audytu po synchronizacji. |
| **P1-06** | Sprawdzić dokładność czasu krótkich sesji: obecne `Math.max(1 min)` za kilkusekundową pracę; minimum 20 min dotyczy **planu**, nie faktycznie naliczonego czasu. |
| **P1-07** | Android: zwarta karta czynności z przyciskami wewnątrz karty; bez obciętych etykiet; zachowanie scrolla i wybranej czynności. |
| **P1-08** | Desktop: funkcjonalna zgodność kosztów, rzeczy/pudełek, planera i dostępności z Androidem, bez duplikowania PayCheck. |

**Po P0 także testować planer**: okna użytkownika I zmiana 16:00–21:00, II 08:00–12:00, dzień wolny 09:00–18:00 z wyjątkami; poprzedniki, twarde wymagania, wyłącznie pozostały czas i propozycje terminów do zatwierdzenia (bez automatycznego wykonywania).

### Scenariusze odbioru — wszystkie wymagane przed statusem „gotowe”

1. Projekt A → podprojekt A1 → trzy zadania (zakup → przygotowanie → montaż); dodaj/usuń/przywróć zależność, przetestuj ochronę przed pętlą.
2. Dodaj wymaganie twarde i miękkie; zaznacz, odznacz, edytuj rodzaj/nazwę/datę i usuń; sprawdź zmianę blokad bez awarii okna.
3. Start 20-minutowej czynności, działający licznik i deep link, Stop w powiadomieniu; wznowienie, suma sesji, pozostały czas i kolory po przekroczeniu.
4. Próba wykonania z twardą blokadą i próba ukończenia projektu z niewykonaną czynnością potomną; odmowa we wszystkich ekranach; Cofnij.
5. Usunięcie czynności z zależnościami i sesjami po czytelnym potwierdzeniu; brak osieroconych rekordów i powrotu danych.
6. Backup → restart → restore → Android→Desktop→Android, także po edycji z PC i po konflikcie offline; identyfikatory, statusy i historia identyczne.
7. Brak skakania listy po checkboxie, edycji, zmianie zależności i Stop; praca **offline bez PC** oraz po restarcie telefonu.

**Warunek ukończenia punktu P0:** (a) minimalna poprawka kodu na `beta`; (b) test regresyjny **wykonujący logikę**, nie jedynie szukający fragmentu tekstu; (c) zielony Android/Desktop CI dla właściwego commita; (d) fizyczny test na urządzeniach z wynikiem; (e) aktualizacja tej tabeli z commitami i testem. Po spełnieniu tych wymogów oznaczyć `✅ Odebrane`, nigdy wcześniej.

**Następny krok po poleceniu rozpoczęcia prac:** otworzyć kod `DbHelper.completeTask`, `renderProjectTasks` oraz zwykłe Zadania na aktualnym HEAD `beta` i wdrożyć **P0-01**, następnie **P0-02**, z osobnymi testami/commitami. Budżet miesiąca 5C pozostaje **otwarty, zachowany i nieodebrany** — prace nad nim nie są anulowane. `main`/Stable nienaruszalne bez odrębnej zgody.

## Pozostałe obszary i zaległe kontrole

| Obszar | Uwaga / następny test |
|---|---|
| LAN Android ↔ Desktop | Sprawdzać realnie transfer w obie strony, także po restarcie i bez ręcznego wywoływania; sam status „połączono” nie oznacza poprawnej synchronizacji |
| Magazyn, QR/NFC, remanent | Zachowanie identyfikatorów, historia przenosin, zdjęcia/miniatury, backup i konflikty przy synchronizacji; szczegóły w roadmapie |
| Pojazdy / maszyny | W backlogu: km/motogodziny, interwały serwisu i zadania przypominające; nie nazywać ich gotową funkcją |
| Stable 1.0 | Bez promocji z `beta` na `main` bez zgody i odrębnych testów migracji, podpisu, wydania i odtwarzania |

## Rejestr prac i blokad

| Data | Etap / zmiana | Potwierdzenie |
|---|---|---|
| 2026-10-08 | 5A: struktura SQLite + historia Budżetu; 5B: Desktop — odczyt wspólnego Budżetu | Android Beta `0.8.0.55/247`; Desktop Beta `0.7.0.106`; CI [Android](https://github.com/edwinkarolczyk/Edhome/actions/runs/37764090792) i [Desktop](https://github.com/edwinkarolczyk/Edhome/actions/runs/37764090766) — success |
| 2026-10-08 | 5C i odbiór 25/25 pozostają otwarte | [Roadmapa](ROADMAP.md); brak potwierdzenia testu fizycznego i pełnego dwukierunkowego zapisu |
| 2026-10-08 | Utworzono stały plik statusu projektu | `docs/EDHOME_STATUS.md`, gałąź `beta`; commit widoczny w historii GitHub tego pliku |
| 2026-10-08 | 5C — historia append-only, konflikty i pierwsza edycja wspólnego Budżetu PC | Commity: `3160b783`, `a58d37b1`, `9a9674de`, `4ba83a06`, `2d13d457`, `8caa634d`; nowe testy JUnit/kontrakt; Desktop CI #249 i Android CI #1950 — success; oba instalatory dostępne, test fizyczny i odbiór 5C nadal wymagane. |

| 2026-10-08 | Projekty — zapisano plan P0-01–P0-06, P1-01–P1-08 oraz 7 scenariuszy odbioru, **bez modyfikacji kodu** | [Audyt](AUDYT_PROJEKTY_2026-10-08.md); start od P0-01 po poleceniu użytkownika; PayCheck 5C zachowany. |

| 2026-10-08 | Budżet 5C: per-ID delta, atomowe konflikty, kontrola podwójnych płatności, sidecar i local CAS | [Android #1969](https://github.com/edwinkarolczyk/Edhome/actions/runs/37770627881), [Desktop #265](https://github.com/edwinkarolczyk/Edhome/actions/runs/37770627833) — weryfikacja w toku; test fizyczny nadal oczekuje. |

## Zasady aktualizacji tego pliku przy każdym następnym etapie

1. **Przed zmianą kodu:** odczytaj ten plik, [ROADMAP.md](ROADMAP.md), aktualny HEAD `beta`, wersje Android/Desktop oraz wynik najnowszych CI.
2. **Po wdrożeniu:** popraw datę, wersje, stan etapu, numer commita, konkretny test i wynik. Zapisz, co faktycznie zmieniono; nie oznaczaj planów jako wdrożonych.
3. **Przy błędzie lub ograniczeniu:** zapisz dokładny komunikat, numer nieudanego workflow, co zostało przerwane i jakie dane są zagrożone. Powiedz o blokadzie użytkownikowi w bieżącej rozmowie.
4. **Po akceptacji:** odróżnij „kod wdrożony”, „CI success”, „urządzenie przetestowane” i „funkcja odebrana”. Bez testów nie wolno przechodzić automatycznie do „gotowe”.
5. **Na koniec odpowiedzi użytkownikowi:** wskaż aktualny etap, następny krok i odnośnik do tego pliku; przy większych zmianach utwórz osobny commit aktualizujący status.
6. **Nowa rozmowa ChatGPT:** zacznij od polecenia „Otwórz `docs/EDHOME_STATUS.md` w `edwinkarolczyk/Edhome` na `beta` i kontynuuj od następnego kroku”. Nie trzeba odtwarzać całego poprzedniego czatu.
7. **Bez fikcyjnej automatyzacji:** plik aktualizuje się podczas faktycznie wykonanych prac z dostępem do GitHub, **nie w tle bez uruchomionej sesji/zadania**.

**Źródła szczegółowych decyzji:** [ROADMAP.md](ROADMAP.md), historia commitów i uruchomienia GitHub Actions. W razie sprzeczności najnowszy sprawdzony stan kodu i testów ma pierwszeństwo przed nieaktualnym opisem historycznym, natomiast decyzje o zakresie muszą być zgodne z ustaleniami użytkownika.
