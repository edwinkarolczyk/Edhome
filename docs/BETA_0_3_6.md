# EDHOME 0.3.6-beta.1 — rotacyjne obowiązki domowników

## Zakres

- Rotacja działa wyłącznie dla **czynności powtarzalnych**. Kolejność ustala użytkownik, np. `Edwin → Marek → Dawid → Sebastian`.
- Każda czynność nadal ma jednego **aktualnego wykonawcę**. Musi on należeć do rotacji. Po oznaczeniu terminu jako wykonanego następny termin tej samej czynności automatycznie otrzymuje kolejną osobę z listy; po ostatniej wraca pierwsza.
- Formularz czynności pozwala dodać osoby do rotacji, zmieniać kolejność strzałkami, usuwać osoby i całkowicie wyłączyć rotację. Rotacja z jedną osobą jest odrzucana — wtedy należy użyć zwykłego stałego wykonawcy.
- Karta czynności pokazuje aktualnego wykonawcę i pełną kolejność rotacji. **Historia zapisuje wykonawcę konkretnego wykonanego terminu** jako ID i nazwę-snapshot, więc późniejsza zmiana nazwy/usunięcie domownika nie fałszuje starej historii.
- Usunięcie domownika czyści jego grafik i przypisania. Jeżeli był w rotacji, lista jest porządkowana ponownie. Przy mniej niż dwóch pozostałych osobach rotacja zostaje wyłączona; jedna pozostała osoba może zostać zwykłym wykonawcą.
- Usunięcie czynności usuwa tylko konfigurację jej rotacji. Historia wykonań pozostaje zgodnie z dotychczasową zasadą.

## Dane i kopia

- SQLite **v12 → v13** dodaje `task_rotation_members(task_id, member_id, position)` i dwa pola snapshotu wykonawcy w `task_history`.
- Migracja nie zmienia istniejących zadań ani minutników. Stare zadania po aktualizacji mają rotację wyłączoną.
- JSON v13 eksportuje/importuje kolejność rotacji oraz snapshot wykonawcy w historii. Kopie v2–v12 są nadal akceptowane i dostają pustą rotację.
- Import odrzuca rotację do nieistniejącego zadania/domownika, rotację czynności jednorazowej, duplikaty osób/pozycji, przerwaną numerację oraz aktualnego wykonawcę spoza rotacji.
- Tabela rotacji używa klucza złożonego i **nie ma sztucznego pola `id`**; eksport sortuje ją deterministycznie po `task_id, position`.

## Odbiór na telefonie

1. Dodaj czterech domowników. Utwórz powtarzalną czynność, np. „Wynieść śmieci”, ustaw rotację A → B → C i aktualnego wykonawcę A.
2. Oznacz czynność jako wykonaną. Następny termin powinien pozostać otwarty, mieć nową datę i wykonawcę B. Następne wykonania: C, potem znowu A.
3. Otwórz historię. Każdy wpis ma odpowiadać osobie, która wykonywała dany termin — nie aktualnemu wykonawcy kolejnego terminu.
4. Spróbuj włączyć rotację w zadaniu jednorazowym albo zapisać jedną osobę w rotacji — aplikacja ma odmówić czytelnym komunikatem.
5. Usuń jednego domownika z rotacji trzech osób. Pozostali mają zostać w prawidłowej kolejności; jeżeli zostanie jedna osoba, rotacja ma się wyłączyć.
6. Wyeksportuj kopię JSON, przywróć ją na instalacji testowej i sprawdź kolejność rotacji oraz historię wykonawców.
7. Po aktualizacji z 0.3.5-beta.1 sprawdź minutniki, Miejsca, układ 9 kafelków i motywy — 0.3.6 nie może ich zmienić.

CI sprawdza regułę kolejności, migracje v1–v12→v13, kopię danych, istniejące moduły, kompilację Beta/Stable i podpis APK. Fizyczny test interakcji nadal pozostaje osobnym kryterium odbioru.
