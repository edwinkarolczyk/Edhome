# EDHOME 0.1.1-beta.1 — ikony oraz pierwszy remanent

> **Wersja testowa; zakres poniżej wymaga weryfikacji na urządzeniu.** Kod na `beta`, `main` niezmieniona. Aplikacja nadal nie jest gotowym produktem gospodarstwa.

## Względem 0.1.0-beta.1

- Podpięto zaakceptowane grafiki **Stable i Beta** jako osobne zasoby poszczególnych wariantów Androida. Wycięto je z zaakceptowanej wspólnej grafiki (same zaokrąglone kafelki, bez podpisów poza ikoną), skompresowano do 128×128 WebP na etap testowy. W repo: `app/src/stable/res/drawable-nodpi/ic_edhome_brand.webp` i `app/src/beta/res/drawable-nodpi/ic_edhome_brand.webp`. Stable jest zielona, Beta pomarańczowa; oba warianty mają także różne `applicationId`. Oryginalne pełnowymiarowe wizualizacje pozostały w rozmowie; adaptacyjne ikony wielorozdzielcze na produkcję są nadal do przygotowania.
- Panel i spiżarnia mają przycisk **„Remanent”**. Lokalny kreator przechodzi po produktach w snapshotcie: Zgadza się / Podaj faktyczną liczbę / Brak na półce (0) / Pomiń. Postęp sesji zapisuje się w SQLite i można ją wznowić po ponownym uruchomieniu. Jest opcja cofnięcia odpowiedzi i anulowania sesji bez korekt.
- Po przejściu wszystkich produktów kreator pokazuje raport: różnice oraz pominięte. **Dopiero osobne zatwierdzenie** zapisuje korekty do spiżarni, w jednej transakcji, z oddzielną historią `audit_corrections`. Jeśli stan choć jednego sprawdzonego produktu zmienił się od startu remanentu, żadne korekty nie są stosowane (konflikt i możliwość anulowania/ponowienia).
- Migracja SQLite z wersji 1 do 2 **bez usuwania** istniejącej tabeli czynności ani produktów. Dodaje `audit_sessions`, `audit_rows`, `audit_corrections`.
- Wariant Beta nadal automatycznie zapisuje lokalne zdarzenia i ma Kopiuj / Eksportuj plik diagnostyczny. **Stable wyłączona diagnostyka na poziomie kompilacji.**

## Ważne ograniczenia

- Remanent obsługuje tylko **całą testową spiżarnię**, produkty w **pełnych sztukach (int)** i ręczne przechodzenie; nie ma aparatu, kodów kreskowych, lokalizacji, kilogramów/litrów, cyklicznych przypomnień, zapisywania fizycznej daty kontroli per obiekt, importu produktu ani synchronizacji.
- Operacje zwykłe +1/−1 nadal są uproszczone; historia korekt obejmuje operacje remanentu, nie wszystkie ruchy magazynowe. Nie używać do rzeczywistych zasobów.
- Zapisany PIN jest tylko testowym zabezpieczeniem lokalnym, nie pełnym systemem kont i bezpieczeństwa.
- Log zdarzeń nie zawiera PIN-u, nazw produktów, kwot, treści powiadomień ani całego logcat Androida.
- **Uwaga na aktualizację:** GitHub Actions używa debugowego podpisu; nowy runner może wytworzyć inny certyfikat niż `0.1.0-beta.1`. Jeśli Android pokaże konflikt podpisu przy instalowaniu na starą aplikację, **nie odinstalowuj starej, jeśli potrzebujesz tamtych danych**. Odinstalowanie usuwa lokalne testowe czynności, spiżarnię i PIN; nie ma jeszcze eksportu danych. Wersja `versionCode=2` i migracja DB nie rozwiązują konfliktu podpisu. Potrzebny trwały klucz podpisu i eksport/migracja przed prawdziwym użytkowaniem. Klucza produkcyjnego nie umieszczamy w repozytorium.
- APK nie jest zagwarantowany, dopóki dany workflow nie zakończy się na zielono i nie udostępni artefaktu.

## Jak sprawdzić remanent na urządzeniu

1. Utwórz 2–3 **testowe** produkty i zmień ich stan przyciskami +1/−1.
2. Otwórz Spiżarnia → Remanent. Dla pierwszego produktu wybierz Zgadza się, dla drugiego wpisz inną liczbę, trzeci pomiń.
3. Wróć do spiżarni przed zakończeniem; otwórz Remanent jeszcze raz i sprawdź zachowany postęp.
4. Dokończ, sprawdź raport i dopiero wówczas zatwierdź. Zweryfikuj nowy stan i diagnostykę `AUDIT_STARTED`, `AUDIT_MATCH`, `AUDIT_COUNTED`, `AUDIT_COMMITTED`.
5. Drugi test: rozpocznij remanent, wróć do spiżarni i zmień stan przyciskiem +1/−1, dokończ remanent. Zatwierdzenie ma pokazać konflikt, bez nadpisania nowszego stanu.

## Powiązane

[Pełna specyfikacja](SPECYFIKACJA_CALOSC.md) • [roadmapa](ROADMAP.md) • [poprzednia beta](BETA_0_1_0.md).

## Potwierdzenie kompilacji i podpisu testowego

GitHub Actions [run 35447241443](https://github.com/edwinkarolczyk/Edhime/actions/runs/35447241443) zakończył się sukcesem, a artefakt `EDHOME-0.1.1-beta.1-apk` zawiera `app-beta-debug.apk`. Wprowadzono cache **testowego** podpisu debug dla przyszłych kompilacji, ale cache nie jest bezterminową gwarancją i nie zastępuje trwałego klucza wydawniczego przechowywanego poza repo. Podpis `0.1.0-beta.1` może być inny, więc jego danych nie należy bezmyślnie usuwać przy konflikcie aktualizacji.
