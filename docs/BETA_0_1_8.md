# EDHOME 0.1.8-beta.1 — większy pakiet domowego zarządzania

Gałąź: `beta`; `versionCode=9`; podpis tej samej linii certyfikatu co 0.1.4–0.1.7. Zapis źródeł nie jest równoznaczny z testem fizycznego telefonu.

## Panel i wygląd
- Panel główny z kartą liczby otwartych i zaległych czynności oraz najbliższymi terminami.
- Kwadratowe kafle 150 dp przesuwane poziomo do Czynności, Kalendarza, Spiżarni, Remanentu, Aktualizacji, Kopii i Ustawień; brak osobnego ekranu demonstracyjnego skanera w głównych akcjach.
- Karty z większym zaokrągleniem, prawidłowy kontrast ikon systemowych przy motywie jasnym.

## Czynności i kalendarz
- Filtry przewijane w bok: Wszystkie, Dzisiaj, Zaległe, Nadchodzące, Wykonane.
- Trzy zwięzłe akcje na karcie: Edytuj / Historia / Usuń.
- Globalna historia wykonania również dla czynności usuniętych z bieżącej listy.
- Kalendarz pokazuje rzeczywistą liczbę potrzebnych tygodni zamiast zawsze sześciu; karta wybranej daty i przejście do zaległych.
- Bez zmian schematu czynności i bez migracji SQLite (nadal v3). Wykonania cykliczne i terminy nadal zachowują dotychczasowe zasady.

## Spiżarnia
- Wyszukiwanie produktów po nazwie; nadal offline.
- Dodanie tej samej nazwy (bez rozróżniania wielkości liter) zwiększa liczbę zamiast dodawać duplikat.
- Przyciski +1 / -1, ustawianie dokładnej liczby sztuk (0–100 mln), zmiana nazwy i usuwanie z potwierdzeniem.
- Usunięcie produktu zablokowane w trakcie otwartego remanentu; zmiana ilości jest możliwa i dalsze zatwierdzenie remanentu nadal sprawdza konflikt względem snapshotu.
- W tym wydaniu nie udajemy obsługi kg/l, zdjęć ani skanera — pozostają na później.

## Ochrona danych
- Dodatkowa walidacja ilości i referencji remanentów przed przywracaniem kopii JSON. Błędne rekordy mają być odrzucone **przed** transakcją nadpisującą tabele.
- W dalszym ciągu wspieramy poprzedni backup JSON v2 i bieżący v3; PIN i sekrety nie są eksportowane.
- Rozszerzone testy regresji czystych reguł dat do 17 przypadków.

## Aktualizacje
- Beta po zweryfikowanym pobraniu proponuje tylko „Aktualizuj”; Stable ma także „Później”. Systemowy instalator nadal wymaga zgody Androida.
- Automatyczne pobieranie wymaga zewnętrznego HTTPS manifestu i APK. **Nie uruchomiono hostingu ani nie ujawniono prywatnego repozytorium.**

## Test na telefonie
1. Wyeksportuj kopię z 0.1.7, zainstaluj 0.1.8 na istniejącą instalację. Sprawdź zachowanie PIN, czynności i spiżarni.
2. Na Panelu przesuń kafelki w bok; przetestuj jasny i ciemny motyw.
3. Dodaj zadanie z terminem dziś oraz w przeszłości. Sprawdź Dzisiaj / Zaległe i przejście z kalendarza; wykonaj i obejrzyj historię.
4. Dodaj dwa razy ten sam produkt różną wielkością liter: ilość powinna wzrosnąć; zmień nazwę, ustaw ilość ręcznie, wyszukaj produkt.
5. Rozpocznij remanent, spróbuj usunąć produkt (zablokowane), zmień ilość i sprawdź wykrycie konfliktu.
6. Eksportuj i przywróć testową kopię. Jeśli coś nie działa, wyeksportuj diagnostykę; nie przesyłaj klucza podpisu.
