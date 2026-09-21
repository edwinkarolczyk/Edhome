# EDHOME 0.4.0-beta.3 — Open Food Facts w spiżarni

Wydanie Beta (gałąź `beta`). Baza SQLite v15, aktualizacja danych v14→v15 bez usuwania zapasów i remanentów. `main` pozostaje bez zmian.

## Odbiór na Androidzie
1. W aplikacji 0.4.0-beta.2 wykonaj eksport kopii zapasowej.
2. Zainstaluj podpisane 0.4.0-beta.3 na obecną Betę — **nie odinstalowuj**.
3. Otwórz Spiżarnię, sprawdź ilości, kartę produktu i historię skanów.
4. Zeskanuj nieznany kod w trybie „Dodaj +1”. Wybierz **Open Food Facts** (internet, wysłanie samego kodu produktu). Jeżeli baza ma nazwę, popraw ją w formularzu, zobacz zdjęcie jeśli dostępne i świadomie potwierdź +1.
5. Zeskanuj ten sam kod drugi raz. Powinien działać z lokalnej kartoteki bez internetu, bez ponownego wyszukiwania.
6. W przypadku braku produktu lub internetu wybierz „Wpisz ręcznie”. Sprawdź wyciąganie, brak zejścia poniżej zera i ponowną próbę po restarcie.
7. Sprawdź eksport/przywrócenie kopii. Backup JSON zapisuje markę i adres zdjęcia, lecz **nie zawiera plików zdjęć**. Po przywróceniu można użyć „Pobierz zdjęcie produktu”, jeśli internet jest dostępny.

## Prywatność i ograniczenia
- Open Food Facts jest opcjonalne. Automatyczne wysyłanie kodów do bazy nie jest włączone.
- Zapytanie HTTPS wysyła kod tylko po świadomym wybraniu „Open Food Facts”; rozpoznane lokalnie kody nie wymagają internetu.
- Źródło nazw i zdjęć: Open Food Facts; nazwy, marki i zdjęcia mogą być niepełne lub błędne. Weryfikuj etykietę. Zdjęcie przechowujemy jako prywatny lokalny cache, pobieramy tylko z images.openfoodfacts.org.
- Dane użytkownika (zapasy, członkowie domu, lokalizacje) nie są przesyłane do Open Food Facts.
- Ilości spiżarni pozostają w sztukach. Jednostki kg/l i automatyczny licznik odjęcia nie są jeszcze implementowane.
- Nie deklarować sukcesu aparatu na fizycznym telefonie bez testu Edwina.

## 0.4.0-beta.4 — wyszukiwanie wielu kategorii

- Rozpoznanie z bazy pokazuje nazwę produktu jako tekst, bez żądania ręcznego wpisywania. Użytkownik potwierdza dopisanie +1. Nazwę istniejącego produktu można nadal zmienić w Spiżarni.
- Ręczne pole nazwy pojawia się dopiero przy braku nazwy w katalogach albo niedostępności wyszukiwania; status sieci jest wyraźnie rozróżniony od braku produktu.
- Wyszukiwanie na życzenie, kolejno: Open Food Facts (żywność), Open Products Facts (m.in. chemia), Open Beauty Facts (kosmetyki) i Open Pet Food Facts (karma). Pierwszy znaleziony produkt z nazwą wystarcza. Zakres danych w katalogach różni się; brak wyniku nie oznacza braku produktu na rynku.
- Zabezpieczenie zdjęć umożliwia lokalne zapamiętywanie obrazów z dopuszczonych serwerów obrazów projektu Open Facts; zapasy pozostają lokalne. Nie zapewniamy pełnego pokrycia katalogów i nazw polskich.

## 0.4.0-beta.5 — kategorie spiżarni

- Filtrowanie żywność i napoje / chemia domowa / kosmetyki i higiena / karma / pozostałe.
- Nowy produkt ręczny: wybór kategorii. Przy istniejącym produkcie: Edytuj pozwala zmienić nazwę i kategorię.
- Po rozpoznaniu w Open Food Facts, Open Beauty Facts lub Open Pet Food Facts domyślna kategoria jest ustawiana automatycznie. Open Products Facts jest katalogiem ogólnym: domyślnie Pozostałe; użytkownik może jawnie wybrać Chemia domowa lub inną kategorię. Nie ma zgadywania po nazwie.
- Zastane produkty otrzymują kategorię Pozostałe. Migrujemy bazę SQLite v15 → v16 bez utraty ID, ilości, kodów kreskowych, historii i zdjęć; kopia JSON ma pole kategorii, starsze kopie ustawiają Pozostałe.
- Jednostki kg/l, zmienne opakowania i seryjne skanowanie nie są jeszcze w tej wersji.

## 0.4.0-beta.6 — pełne opakowania a zawartość

- `pantry.qty` w dalszym ciągu liczy **pełne opakowania**. W obrębie tego wydania jeden produkt ma jedną aktualną specyfikację opakowania: `unit` = `szt.` / `kg` / `l`, `size_milli` = zawartość jednego opakowania × 1000. Np. 3 × 0,5 l = 1,5 l. Skan nadal ±1 **opakowanie**.
- Przy ręcznym utworzeniu oraz pierwszym skanie: wybór jednostki i wielkości opakowania. Dla produktów rozpoznanych w Open Facts nazwę wciąż wypełnia baza, bez ręcznego wpisywania.
- Edycja istniejącej pozycji pozwala zmienić nazwę, kategorię i zawartość opakowania. Widok oraz dialog skanu pokazują liczby opakowań i sumaryczną zawartość. Sztuki nie mogą mieć ułamkowej zawartości; kg/l obsługują do trzech miejsc po przecinku. Przed dodaniem znanej nazwy z inną specyfikacją opakowania aplikacja odrzuca niejawne scalanie.
- SQLite v16 → v17 dodaje `pantry_packages` bez zmiany ID ani `pantry.qty`; starsze towary otrzymują 1 szt. na opakowanie. Backup JSON zawiera specyfikację i potrafi przywrócić starszą kopię z takimi wartościami domyślnymi.
- Ten etap **nie** obsługuje otwartych opakowań, wag cząstkowych, czy mieszanych rozmiarów pod jedną kartą produktu.

## 0.4.0-beta.7 — seryjne skanowanie

- Oddzielne przyciski „Skanuj serię — dodawaj +1” i „Skanuj serię — wyciągaj −1”. Po **świadomym zatwierdzeniu i udanym zapisie** każdej operacji aparat wraca do kolejnego produktu. Back/Anuluj kończy serię i pokazuje liczbę zapisanych ruchów.
- Zdublowany wynik *tego samego wywołania kamery* jest ignorowany. Jeśli kolejny, osobny skan odczyta ten sam kod co poprzednio, przed zwykłym potwierdzeniem produktu pojawia się pytanie „Ten sam kod co poprzednio”: użytkownik wybiera kolejne opakowanie, skan innego kodu bez naliczania albo zakończenie serii.
- Wciąż jeden skan to **jedno całe opakowanie**, bez samoczynnego odejmowania. UUID operacji i zapis ilości wraz z ruchem w transakcji SQLite pozostają bez zmian. Licznik serii rośnie wyłącznie przy COMMITTED, nie przy DUPLICATE_IGNORED.
- Seria pozostaje w pamięci MainActivity, bez automatycznego wznowienia aparatu po restarcie procesu. Brak migracji bazy: SQLite v17, kopia zapasowa zgodna z 0.4.0-beta.6.
- CI sprawdza stany i odporność na powtórny wynik callbacku, ale fizyczne działanie aparatu, uprawnień i reakcji telefonu należy zweryfikować po instalacji.

## 0.4.0-beta.8 — katalogi i diagnostyka produktów

- Wyszukiwanie kodów z EAN/UPC/GTIN uwzględnia powiązane formaty z zerami na początku, ale oryginalny kod zachowujemy w lokalnym magazynie; nie łączymy różnych towarów bez potwierdzenia.
- Zapytania HTTPS honorują maksymalnie trzy przekierowania **wyłącznie** w dozwolonych domenach projektów Open Facts. Błąd sieciowy jednej bazy nie blokuje prób w następnych, a HTTP 404/410 jest wynikiem „brak rekordu”, nie błędem internetu.
- Każdy realnie sprawdzony katalog Open Food Facts / Open Products Facts / Open Beauty Facts / Open Pet Food Facts raportuje „znaleziono / brak rekordu / rekord bez nazwy / problem HTTP”. Raport zawiera liczbę wariantów kodu. Diagnostyka zapisuje status źródła, ale nie kod kreskowy użytkownika.
- Potwierdzanie zmiany stanu pozostaje wymagane; istniejące dane, historia, zdjęcia i baza SQLite v17 pozostają bez zmian. Po zgodzie użytkownika do baz przesyłany jest tylko kod produktu.
- Dostępność i aktualność zewnętrznych baz nie są pod naszą kontrolą. Nie zakładamy, że każda baza zawiera wszystkie polskie produkty lub chemię domową.

## 0.4.0-beta.9 — propozycje produktów po nazwie

- Po nieudanym rozpoznaniu kodu dostępne „Szukaj po nazwie” oraz dotychczasowe „Wpisz ręcznie”. Wpisana fraza (3–80 znaków) jest świadomie wysyłana do czterech katalogów Open Facts; nie jest wysyłana automatycznie przy skanowaniu.
- Każdy katalog zwraca do 6 propozycji. Wynik pokazuje nazwę, markę i bazę; wybór propozycji otwiera dopiero formularz „Potwierdź produkt”. Wcześniej nie aktualizuje stanów, nie zmienia skanowanego kodu i nie łączy go z kodem wyszukanego towaru. Przed zatwierdzeniem należy sprawdzić, czy znaleziony wariant odpowiada rzeczywistemu produktowi.
- Nazwa nie jest wpisywana ręcznie, gdy użytkownik wybierze propozycję; ręczna nazwa pozostaje opcją przy braku trafnego wyniku. Zdjęcie jest pobierane dopiero po wyborze propozycji; jego brak nie blokuje dodania.
- Katalogi mogą nie obsługiwać identycznie API wyszukiwania po nazwie. EDHOME pokazuje status każdego katalogu, nie gwarantuje pełnej dostępności katalogów ani kompletności polskich produktów.
- SQLite pozostaje v17; ilości, kody, historia, zdjęcia i dotychczasowy backup nie wymagają migracji. Stable main bez zmian.

## 0.4.0-beta.10 — powiąż nieznany kod z własnym produktem

- Przy nieznanym kodzie w trybie dodawania dostępne „Moje produkty”: wybierz istniejący produkt z listy i potwierdź przypisanie kodu oraz **+1 pełne opakowanie**. Nie trzeba ręcznie przepisywać nazwy ani pytać baz zewnętrznych. Przy braku produktów pozostaje wyszukiwanie w katalogach.
- Przy wyborze widoczne są nazwa i aktualny stan wraz z jednostką. Operacja wskazuje ID produktu, weryfikuje, że produkt nadal istnieje i że kod nie należy do innego produktu; powiązanie, zmiana stanu i zapis historii następują w jednej transakcji, z ochroną UUID przed ponownym naliczeniem.
- Przypisanie nie zmienia nazwy, kategorii, specyfikacji opakowania ani zdjęć dotychczasowego produktu. Jeśli w katalogach nie było nazwy, można skorzystać z własnego produktu bez dodatkowego internetu.
- SQLite v17: brak migracji i zmian formatu kopii zapasowej. Stable main pozostaje bez zmian.

## 0.4.0-beta.11 — kupione nie oznacza przyjęte

- Zaznaczenie zakupu nie dopisuje zapasu. Przy kupionej pozycji pojawia się przycisk „Przyjmij do spiżarni”: użytkownik wskazuje istniejący produkt oraz liczbę pełnych opakowań i potwierdza osobną operację.
- Unikalny identyfikator pozycji listy zakupów blokuje ponowne naliczenie przyjęcia. Historia `shopping_receipts` zapisuje stan przed/po, liczbę opakowań i nazwę produktu w jednej transakcji SQLite; usunięcie pozycji z listy nie usuwa historii przyjęcia.
- SQLite v18, migracja v17→v18 bez zmiany istniejących danych; kopia JSON zawiera przyjęcia i nadal importuje starsze wersje.
- Kupno to nie księgowanie wydatku. Brak cen i automatycznego PayCheck w tym przyjęciu.

**Pozostały zakres 0.4:** pełne QR rzeczy/pudełek, pożyczki, przypisanie miejsca na zakupy i zakres remanentu. Nie oznaczać 0.4 jako ukończonego na podstawie samego zielonego CI.

## 0.4.0-beta.12 — rzeczy, pudełka, QR i wypożyczenia

- Miejsca → Rzeczy i pudełka. Można dodawać rzeczy i pudełka, ustawiać miejsce albo pudełko nadrzędne, przenosić, przeglądać lokalizację wynikową, pokazać i zeskanować QR.
- QR to typ+trwałe lokalne ID; przeniesienie nie wymaga zmiany etykiety. QR działa na urządzeniu z lokalną kartoteką, a synchronizacja między urządzeniami jest osobnym etapem. Kod QR nie jest hasłem ani kluczem dostępu.
- Pudełka nie mogą być przenoszone do siebie ani swoich pod-pudełek; nie wolno usunąć pudełka z zawartością. Położenie rzeczy w pudełku jest dziedziczone zamiast kopiować lokalizację do każdej rzeczy.
- Wypożyczanie i zwroty rzeczy zapisywane są w historii lokalnej. Pudełka nie są wypożyczane. W czasie wypożyczenia rzecz nie może być przeniesiona/usunięta bez zwrotu.
- SQLite v18→v19, eksport i import JSON zawierają rzeczy oraz historię. Naprawiono też sortowanie `pantry_packages` przy eksporcie: ta tabela ma `pantry_id`, nie `id`.
- Testy automatyczne nie zastępują fizycznego testu skanera i wydruku QR.
