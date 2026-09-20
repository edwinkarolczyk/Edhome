# EDHOME 0.2.3-beta.1 — pakiet rozwojowy

Gałąź: `beta`; `versionCode=13`, `versionName=0.2.3-beta.1`.
`main` nie jest celem żadnego z commitów tego pakietu.

## Wprowadzone w kodzie

- Panel główny: dziewięć kafelków nadal 3 × 3, zaokrąglone; przytrzymaj kafelek i przeciągnij na inny. Kolejność zostaje w prywatnych preferencjach urządzenia po restarcie i w eksportowanej kopii JSON. Przewijanie strony pozostaje pionowe. Ekran Aktualizacje zachowuje swoją własną siatkę 3 × 3 i nie podlega przemieszczaniu.
- Formularz czynności: czytelniejsze etykiety/pola, zaokrąglone okno, pole odstępu N widoczne tylko dla reguł `co N`, istniejące walidacje i opcjonalne miejsce.
- Kalendarz: miesiąc, tydzień, dzień i agenda na kolejne 30 dni. Zapis nadal korzysta z tej samej tabeli czynności i wspólnej historii.
- Przypomnienia: dobrowolny przełącznik w Ustawieniach, lokalny nieprecyzyjny alarm raz dziennie około 09:00 dla czynności terminowych lub zaległych; odtworzenie harmonogramu po restarcie, zmianie zegara/strefy i aktualizacji. Android 13+ prosi o zgodę na powiadomienia. Alarm może zostać opóźniony przez Androida; po wymuszeniu zatrzymania aplikacji dostarczanie alarmów nie jest gwarantowane. Na powiadomieniu nie ma tytułów prywatnych zadań.
- CI: sprawdza migracje bazy SQLite 1→4, 2→4 i 3→4 względem nowo utworzonej bazy; kontroluje schemat 7 tabel wymienionych w kopii danych. To test kontraktu schematu, **nie** pełny test eksport/import wykonywany na urządzeniu.
- Kanał aktualizacji: mechanizm zatwierdzonego binarnego publikowania i podpisu pozostaje w workflow. **Nie deklarujemy działania pobierania publicznego APK**, dopóki istnieją publiczne repozytorium, manifest i sekret publikacji.

## Test na telefonie (dopiero po potwierdzeniu buildu)

1. Przed zmianą APK wejdź w Kopia danych, wyeksportuj JSON poza aplikację; nie odinstalowuj starej wersji.
2. Zainstaluj APK z GitHub Actions ze stałym podpisem na podpisanej EDHOME Beta z poprzedniej wersji. Jeżeli Android odrzuca podpis, przerwij; nie kasuj danych.
3. Panel: dziewięć kwadratowych kafelków 3 × 3. Przytrzymaj „Kalendarz” i przeciągnij na miejsce „Czynności”, zamknij i otwórz ponownie. Kolejność ma zostać. Spróbuj zwykłego pionowego przewijania.
4. Czynności: dodaj jednorazową bez miejsca, dodaj „co 3 tygodnie” z miejscem, edytuj, ukończ i sprawdź historię oraz kolejny termin.
5. Kalendarz: przejdź kolejno miesiąc → tydzień → dzień → agenda. Sprawdź oba kierunki nawigacji i dodawanie zadania dla wybranego dnia.
6. Ustawienia: włącz Przypomnienia i udziel zgody systemowej. Termin na dziś lub zaległy powinien dać nieprecyzyjne powiadomienie około następnej 09:00; sprawdź też restart urządzenia. Nie oczekuj alarmu co do minuty.
7. Zrób eksport JSON, uruchom import na kopii testowej, porównaj miejsca, czynności, historię i remanent. Nie testuj destrukcyjnie jedynej kopii danych.
8. Aktualizacje: publiczne EDHOME-Updates musi udostępniać bez logowania manifest i APK. Manifest musi mieć `versionCode` większy niż zainstalowany. Samo wydanie 0.2.3 na telefonie z już zainstalowaną 0.2.3 nie pobierze tej samej wersji. Pobranie, podpis i zachowanie danych wymagają rzeczywistego testu na Androidzie.

## Warunek uruchomienia automatycznego pobierania

Utworzyć **publiczne** `edwinkarolczyk/EDHOME-Updates`, ograniczyć fine-grained token wyłącznie do niego (Contents read/write), zapisać token jako **sekret** `EDHOME_PUBLIC_CHANNEL_TOKEN` w prywatnym repo, uruchomić workflow `EDHOME Beta APK` na `beta` z nowym numerem wydania. Nie umieszczać tokenu, kodu, kluczy ani danych w publicznym repo. Pełna instrukcja: [KANAL_AKTUALIZACJI_BETA.md](KANAL_AKTUALIZACJI_BETA.md).

Stan testu urządzenia i uruchomienia publicznego kanału: **niepotwierdzony**.
