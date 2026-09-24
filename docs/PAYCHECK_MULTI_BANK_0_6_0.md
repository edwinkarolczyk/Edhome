# EDHOME 0.6.0 — PayCheck: wielobankowa skrzynka potwierdzeń

Decyzja Edwina 24.09.2026. **To zakres do wykonania i odbioru w serii 0.6.0; nie opis gotowej funkcji w APK 0.6.0.10.** Aktualny import CSV był jedynie prototypem ręcznego uzgadniania, a nie docelowym UI. Tylko gałąź `beta`; `main` bez zmian. Nie przechodzić do 0.7 bez odbioru zakresu 0.6.

## Interakcja docelowa

- Jeden ekran „Banki i potwierdzenia”, odrębny od formularza „Dodaj wydatek”. Widoczne: przycisk „Dodaj pliki”, „Włącz powiadomienia banków”, liczniki „Dopasowane”, „Do decyzji”, „Nieznane”, „Duplikaty”; informacje o saldzie potwierdzonym i oczekującym.
- Import wielu plików na raz; przy każdym pliku jawny bank/rachunek i rozpoznany format. Można mieszać banki i źródła w jednej lokalnej kolejce. Użytkownik może poprawić mylnie rozpoznany bank **przed** przyjęciem dowodu; wewnętrzny identyfikator źródła nie zależy od dowolnego tekstu etykiety po imporcie.
- Obsługiwane stopniowo: CSV z różnymi separatorami, kodowaniem i nazwami kolumn; PDF tekstowy potwierdzenia/wyciągu; obrazy JPG/PNG dokumentu (OCR lokalny dopiero po kontrolach jakości); udostępnienie pliku do EDHOME z Androida; powiadomienia wybranych aplikacji bankowych wyłącznie po świadomej zgodzie. Import TXT/HTML/MT940/camt.053 po wykryciu formatu i testach, bez deklaracji „wszystkie banki” bez próbek.
- Filtry: bank, konto właściciela (tylko po świadomym przypisaniu), data/okres, zakres kwoty, wpływ/wydatek, źródło CSV/PDF/obraz/powiadomienie, dopasowane/niejednoznaczne/bez pary/duplikat, wyszukiwarka opisu; sortowanie najnowsze/najstarsze/kwota.
- Karty z nazwą banku, datą transakcji/księgowania, kwotą, opisem, pewnością identyfikacji, propozycją odpowiadającego wpisu oczekującego i prostymi akcjami: „Potwierdź parę”, „Wybierz inną”, „Odrzuć”, „Odłóż”. Nie zmuszać do wielopoziomowych alertów dla każdej transakcji.
- Przechować kolejkę **lokalnie**, w wersjonowanym modelu SQLite i kopii danych. Surowe załączniki przechowywać prywatnie; kopie, migracje i odtwarzanie muszą obejmować załączniki albo jasno wskazywać brak. Ekran wspólny i prywatny sejf PayCheck pozostają rozdzielone. Nie wyświetlać prywatnych treści na tablecie „Wspólny”.

## Reguły księgowania i bezpieczeństwa

- Normalizacja bankowych źródeł do jednego modelu: `source_id, account_id?, source_kind, bank_transaction_id?, booking_date, amount_grosz, currency, description, fingerprint, attachment_id?`. Wymagana jawna waluta; PLN obsługiwane przed innymi. Odróżniać autoryzację/oczekujące od księgowania oraz płatność, przelew własny, zwrot, prowizję.
- Dopasowanie proponuje automatycznie po kwocie, znaku, walucie, dacie, unikalnym ID i opcjonalnym zgodnym opisie. Niejednoznaczność = „Do decyzji”; brak dopasowania nie tworzy automatycznie nowej transakcji ani nie zmienia salda. Użyty dowód nie może potwierdzić dwóch wydatków nawet po restarcie/ponownym imporcie/wielu bankach. Równoważne dowody CSV+PDF+powiadomienie tej samej operacji muszą zostać powiązane, nie policzone jako trzy operacje.
- Powiadomienie systemowe nie jest autentykowanym poświadczeniem księgowania. Domyślnie służy do wykrycia i propozycji, nie do bezwarunkowej zmiany salda. W pełni automatyczne księgowanie można włączyć dopiero dla zatwierdzonej konfiguracji i źródła bankowego, którego status rzeczywiście potwierdza zaksięgowanie, z historią i możliwością ręcznej korekty.
- Android NotificationListenerService: opt-in w ustawieniach, jawna lista pakietów bankowych, kontrola dostępu do powiadomień przez użytkownika, możliwość wyłączenia/usunięcia zgody, ograniczenie zbieranych danych, bez logowania treści, numerów kont i tokenów. Bez credential scraping i bez proszenia o hasła bankowe.
- PDF/obraz: wyraźna odmowa automatycznego księgowania przy niewykrytej dacie/kwocie/walucie albo słabej jakości odczytu. Nie nazywać zaimportowanego pliku „potwierdzonym przez bank” bez połączenia z zaufanym źródłem.

## Kolejność realizacji w 0.6.0

1. **Następny inkrement:** czytelny ekran/kolejka, wieloplikowy import z rozpoznawaniem obsługiwanych CSV, filtrowanie, automatyczne jednoznaczne propozycje **bez automatycznego księgowania**; testy duplikatów, różnych banków i migracji.
2. Adaptery PDF/obrazu i udostępnianie z Androida, z kontrolą jakości rozpoznania oraz wydzielonym magazynem załączników i kompletną kopią.
3. Opcjonalny odbiór powiadomień wskazanych aplikacji, deduplikacja między źródłami, jawne ustawienia i regresja prywatności.
4. Dopiero pełne testy na telefonie: stare saldo, PayCheck wspólny/prywatny, wiele banków/plików/formatów, przelewy własne i zwroty, ponowny import, odtworzenie kopii i aktualizacja APK. Edwin akceptuje zamknięcie 0.6.0; późniejsze wersje także tylko `beta`, aż do przetestowanego 1.0.0 na `main`.

Próbki bankowych dokumentów wyłącznie zanonimizowane. Nie publikować rzeczywistych numerów kont, nazwisk, kwot użytkownika, tokenów ani dokumentów finansowych w publicznym repo ani logach CI.
