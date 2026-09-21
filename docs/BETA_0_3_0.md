# EDHOME 0.3.0-beta.1 — lokalna lista zakupów

## Pierwszy inkrement etapu 0.3

- **Lista zakupów offline** dostępna z panelu głównego (pod najbliższymi czynnościami) i Spiżarnia → Lista zakupów. Układ dziewięciu przesuwanych kafelków pozostaje bez zmian.
- Dodawanie produktu z opcjonalną ilością: puste pole oznacza **brak określonej ilości**, a nie zero. Liczby dodatnie do trzech miejsc po przecinku z kropką lub przecinkiem, jednostki szt., kg, l. W bazie ułamki są przechowywane jako liczby całkowite tysięcznych jednostki; wpis `1,5 l` zostaje `1500` tysięcznych.
- Zaznaczanie kupione/niekupione i usuwanie z potwierdzeniem, bez duplikatów nazw ignorując wielkość liter. **Zaznaczenie zakupu nie zmienia spiżarni bez oddzielnego zatwierdzenia**.
- SQLite v8: tabela `shopping_items`, migracje z wcześniejszych wersji nie dotykają istniejących czynności, remanentów i zapasów. JSON backup v8 eksportuje listę zakupów; import kopii v2–v7 tworzy pustą listę i zachowuje stare dane.
- Beta pozostaje bez PIN-u, Stable z PIN-em, aktualizator z podpisanymi GitHub Releases i manifestem na `beta`, `main` niezmienione.
- To **pierwsza część większego etapu 0.3**, nie skończone obowiązki, śmieci, minutniki czy synchronizacja Wi-Fi. Wspólna lista na różnych urządzeniach dopiero wraz z synchronizacją w planowanym 0.8.

## Test odbiorczy na Androidzie

1. W starszej Becie wyeksportuj kopię JSON poza aplikację. Uruchom aktualizację 0.2.9 → 0.3.0 bez odinstalowywania.
2. Na panelu głównym wybierz Lista zakupów; dodaj np. `Mleko` — `1,5 l`, `Ryż` — ilość pustą, `Jabłka` — `2 kg`. Powtórzenie `mleko` ma zostać odrzucone.
3. Oznacz produkt jako kupiony i ponownie jako niekupiony; zamknij/otwórz EDHOME. Powinny pozostać nazwa, opcjonalna ilość, jednostka i stan.
4. Sprawdź odrzucanie `0`, liczby ujemnej, ponad trzech cyfr po przecinku i nadmiernej ilości.
5. Potwierdź, że zaznaczenie „kupione” **nie dodaje ani nie odejmuje** ze spiżarni. Usuń pozycję i sprawdź, że inny produkt pozostaje.
6. Wykonaj eksport v8 i testowy import, dodatkowo przetestuj kopię v7: starsze zadania, wykonawcy, grafik, miejsca, historia i remanent muszą pozostać. Nie wykonuj destrukcyjnego importu na jedynej kopii danych.

Testy CI sprawdzają reguły ilości, migracje SQLite, zgodność schematu backupu, kompilację Beta/Stable i certyfikat APK. Test na fizycznym telefonie nadal wymaga potwierdzenia użytkownika.

## Co dalej

Następny pakiet etapu 0.3: śmieci / powiązane przypomnienia oraz godziny czynności i wykrywanie kolizji; nie deklarować tych modułów jako już gotowych.
