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
