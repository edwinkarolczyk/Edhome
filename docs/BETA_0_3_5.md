# EDHOME 0.3.5-beta.1 — minutniki urządzeń

**Zakres:** pralka, suszarka i zmywarka. To lokalne minutniki, nie automatyka sterująca urządzeniami, nie integracja z SUPLA i nie nowe obowiązki cykliczne.

- Ekran `Minutniki urządzeń` dostępny z panelu głównego oraz Czynności. Układ dziewięciu kafelków głównych bez zmiany. Urządzenie, nazwa/program (opcjonalna, maks. 80 znaków), czas 1–1440 minut; szybkie ustawienia 30/45/60/90. Każdy minutnik ma własny termin zapisany jako epoch milisekund.
- Minutnik pozostaje w bazie po zamknięciu i ponownym uruchomieniu aplikacji. W ekranie widać szacowany czas pozostały i termin ukończenia; `Odśwież` przelicza według zegara telefonu. Po terminie pojawia się `Zakończony — do potwierdzenia`; użytkownik potwierdza lub może anulować trwający minutnik. Zakończenie nie tworzy automatycznie nowej czynności.
- **Oddzielne powiadomienia opt-in**, nie zmieniają włączenia przypomnień zadań. Bez uprawnienia i bez włączonych powiadomień sam zapis i status minutnika działają. System Android może opóźnić doręczenie (Doze/bateria) — **nie obiecujemy alarmu co do minuty**. Cisza 22:00–07:00. Potwierdzenie/anulowanie odwołuje alarm i usuwa jego powiadomienie. Rejestracja ponownie po BOOT_COMPLETED, aktualizacji pakietu i zmianie czasu/strefy. Stare zdarzenia alarmowe sprawdzane pod kątem ID, końca i stanu rekordu.
- SQLite **v11 → v12**: dodanie `device_timers` i indeksu stanu/terminu bez zmian istniejących rekordów miejsc, zadań, spiżarni i historii. JSON v12 eksportuje/importuje minutniki i ich opcjonalny przełącznik powiadomień; kopie v2–v11 wczytują pustą listę minutników. Dane JSON walidowane przed transakcyjnym przywróceniem.
- Beta bez PIN, Stable z PIN. `main` nietknięty, trwały podpis APK i obecny manifest aktualizacji.

## Odbiór na telefonie

1. Wyeksportuj kopię przed instalacją i zainstaluj na poprzedniej Becie bez odinstalowania. Sprawdź, że Miejsca, Czynności i wygląd zostały.
2. Uruchom minutnik pralki na 1 min i suszarki na 30 min. Wyłącz powiadomienia — minutnik ma działać w UI, ale bez komunikatów.
3. Włącz powiadomienia, zaakceptuj zgodę systemu. Sprawdź powiadomienie po upływie minuty, dotknij go, potwierdź ukończenie w EDHOME. Na Androidzie z ograniczeniami baterii powiadomienie może się spóźnić.
4. Zatrzymaj trwający minutnik i upewnij się, że nie pojawi się późniejsze powiadomienie. Sprawdź status po zamknięciu/otwarciu aplikacji i po ponownym uruchomieniu telefonu.
5. Eksport/import kopii na **testowej instalacji**: v11 → puste minutniki; v12 → stan, nazwa, czas, historia. Potwierdź brak PIN-u w Becie.

Następny inkrement: **0.3.6 — rotacyjne obowiązki**, o ile 0.3.5 przejdzie test fizycznego telefonu.
