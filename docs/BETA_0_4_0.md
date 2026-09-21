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
