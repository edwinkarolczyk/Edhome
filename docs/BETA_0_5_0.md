# EDHOME — 0.5.0-beta.1 — PayCheck: wspólny budżet

## Funkcje pierwszego inkrementu

- Nowy ekran PayCheck osiągalny z ekranu głównego. W tym wydaniu widoczny jest **wyłącznie wspólny budżet gospodarstwa**.
- Ręczne dodawanie przychodu lub wydatku w PLN, kategoria i opcjonalny opis. Dialog z wyraźnym zatwierdzeniem przed zapisem; anulowanie nic nie księguje.
- Kwoty przechowywane są jako całkowita liczba groszy, bez `float` ani `double`. Zestawienie wspólnego salda i 40 ostatnich transakcji.
- Odporność na ponowne zatwierdzenie tej samej operacji: niepowtarzalny `operation_id` w SQLite. Wpisy są tylko ze źródła ręcznego — lista zakupów, przyjęcia do spiżarni, remanenty i szacowane koszty energii **nie tworzą transakcji**.
- SQLite v19→v20 dodaje `paycheck_transactions`, backup JSON zawiera nowe wpisy i importuje kopie ze starszych wersji.
- Beta działa bez PIN-u. Dlatego w 0.5.0-beta.1 **nie ma pól, ekranów ani kopii osobistych kont i prywatnych transakcji**. Trzeba przed nimi dodać osobną ochronę i test braku wycieku na wspólny tablet.

## Testy odbiorowe

1. Wykonaj eksport kopii z beta.13 i zainstaluj beta.1 na obecną wersję — nie odinstalowuj.
2. Sprawdź swoje produkty, stan opakowań, pudełka, kody i historię przyjęć.
3. PayCheck: dodaj wpłatę 200,00 zł oraz wydatek 12,50 zł. Saldo ma wskazywać 187,50 zł, bez problemu z przecinkiem.
4. Anuluj kolejny wpis — saldo bez zmian. Nieprawidłowe kwoty, ułamki grosza, 0 i liczby ujemne mają być odrzucone.
5. Zaznacz zakup jako kupiony i przyjmij opakowanie do spiżarni — PayCheck ma pozostać bez zmian.
6. Wyeksportuj i przywróć kopię JSON: historia PayCheck, pudełka i zapasy mają pozostać.
7. Nie uznawać CI za fizyczny test na Huawei; osobno sprawdzić czytelność i klawiaturę.

## Po pierwszym wydaniu

Priorytet: prywatne profile z izolacją i ochroną przed dostępem na wspólnym urządzeniu, cele i raty, później uzgadnianie wyciągów oraz bankowych powiadomień. Żadnego automatycznego księgowania bez zatwierdzenia.

## 0.5.0-beta.2 — wspólne cele finansowe

- W PayCheck można utworzyć wspólny cel (nazwa, docelowa kwota PLN), zobaczyć „odłożone / docelowe / pozostało” i ręcznie odłożyć kwotę z potwierdzeniem. Wszystkie kwoty w groszach; cel nie może zostać przekroczony, a UUID zapisu nie pozwala na podwójne naliczenie.
- **Odkładanie jest wyłącznie planem/ewidencją**, nie wydatkiem, nie przelewem, nie zmianą salda wspólnego PayCheck. Nie zakładać, że oznacza rzeczywiste środki na rachunku; prawdziwa płatność jest oddzielną transakcją.
- Beta bez PIN-u nie przechowuje i nie eksportuje kont prywatnych. Cele są tylko wspólne. Przed prywatnymi finansami wymagana osobna kontrola dostępu i ograniczenie widoczności na wspólnym tablecie.
- SQLite v20→v21 dodaje `paycheck_goals` oraz `paycheck_goal_allocations`; backup zawiera obie tabele, odtwarza kopie v20 i starsze. Weryfikacja odrzuca osierocone wpłaty, duplikaty operacji i sumę ponad cel.

Odbiór: cel OC 700 zł, odłożenie 500 zł, potem 200 zł; pozostało 0 zł, saldo wspólne bez zmian. Anulowanie albo ponowny zapis tego samego UUID nie zmienia odłożonej kwoty. Testy CI nie zastępują testu na Huawei.

## 0.5.0-beta.3 — szyfrowany prywatny sejf PayCheck

- Beta nadal uruchamia się bez PIN-u. W PayCheck osobny przycisk „Prywatny sejf PayCheck”; przy pierwszym wejściu użytkownik tworzy **oddzielne hasło 12–64 znaków** i potwierdza je. Prywatny ekran i formularz hasła mają FLAG_SECURE, aby nie trafiać na zrzuty ekranu i podgląd ostatnich aplikacji.
- Dane jednej lokalnej osoby (prywatny przychód, wydatek, kategoria, kwota, opis i data) są szyfrowane AES-256-GCM z losowym IV dla każdego wpisu. Klucz jest wyprowadzony z hasła i losowej soli PBKDF2-HMAC-SHA256 (310 000 iteracji). Hasło i klucz **nie są zapisywane**. W lokalnej oddzielnej bazie SQLite pozostają tylko UUID operacji i ciphertext; powtórna operacja nie nalicza drugi raz.
- Po opuszczeniu aplikacji lub prywatnego ekranu sejf jest blokowany, bufor klucza zerowany, a ekran prywatny usuwany przed zdjęciem FLAG_SECURE. Po pięciu błędnych hasłach blokada na pięć minut. Wspólny budżet pozostaje dostępny bez hasła i nie widzi prywatnych kwot.
- **WAŻNE: prywatne wpisy nie wchodzą do zwykłej kopii EDHOME JSON i nie są przenoszone między telefonami**; Android Auto Backup pozostaje wyłączony. Nie odinstalowywać aplikacji. Utrata hasła lub telefonu = brak możliwości odzyskania prywatnych danych. Bezpieczny eksport i wiele oddzielnych profili pozostają odrębnym etapem; nie podawać fikcyjnych zapewnień o odzyskiwaniu.
- Nie zmienia się główna baza SQLite v21, nie ma migracji wspólnych danych. Lista zakupów, spiżarnia i energia nigdy same nie księgują prywatnych wpisów.

Odbiór: utwórz sejf z hasłem 12+ znaków; zapisz prywatny przychód 200 zł i wydatek 12,50 zł; prywatne saldo 187,50 zł, **wspólne bez zmian**. Zablokuj sejf, spróbuj otworzyć złym hasłem; po wyjściu z aplikacji hasło wymagane ponownie. Zwykły eksport JSON nie może zawierać prywatnej kwoty ani opisu.
