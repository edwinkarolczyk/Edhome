# EDHOME 0.1.7-beta.1 — właściwy ekran Aktualizacje

Wersja `0.1.7-beta.1`, `versionCode 8` (nowsza od podpisanej 0.1.6, kod 7).

## Zmiany

- Widok Aktualizacje został przebudowany, a nie tylko popup instalatora.
- Karty statusu i wersji z zaokrąglonymi rogami oraz **poziomo przesuwana lista kwadratowych kafli 150×150 dp**: sprawdzenie aktualizacji, ręczny wybór APK, gotowe pobranie, kopia danych i opcje zaawansowane.
- Adres HTTPS, składnia manifestu, SHA-256, wyłączanie kanału i szczegóły techniczne są w osobnym ekranie **Opcje zaawansowane**.
- Status nie twierdzi, że automatyczne aktualizacje działają, jeśli nie ustawiono dostępnego źródła pobierania. W takim przypadku ręczna instalacja pozostaje dostępna.
- Obowiązkowe okno **Aktualizuj** bez **Później** pozostaje dla Beta, ale wyświetla się dopiero po pobraniu i weryfikacji nowego APK. Stable korzysta z Play i ma **Później**.

## Odbiór

1. Zrób kopię danych w 0.1.6 i zainstaluj podpisane 0.1.7 bez odinstalowywania.
2. Otwórz Aktualizacje; sprawdź karty, zaokrąglenia i poziome przesuwanie kafli.
3. W Opcjach zaawansowanych można nadal odczytać/ustawić prawdziwy manifest HTTPS, jeśli zostanie opublikowany.
4. Przy pustym źródle nie powinno być fałszywego komunikatu o działającej automatyce.
5. Czynności, kalendarz, spiżarnia, PIN i kopia danych mają pozostać zachowane.

**Ograniczenie:** samo przebudowanie UI nie uruchamia hostingu aktualizacji. Do pełnej automatyki nadal konieczny jest odpowiedni HTTPS feed z manifestem i podpisanym APK. Prywatny artefakt GitHub Actions nie jest takim kanałem.
