# EDHOME 0.3.3-beta.2 — UI Refresh (tylko wygląd i interakcje)

## Zakres

- **Cztery style** przełączane w Ustawieniach: Neonowy (ciemny granat i mięta), Naturalny (leśny zielony), Pastelowy (jasny, kremowy) i Szklany (ciemny błękit i delikatne obramowania). Stare motywy i kopie z nazwami Grafitowy, Leśny, Jasny lub Trener 2 pozostają akceptowane; ostatnie dwa ciemne dawne warianty korzystają teraz z odświeżonego Neonowego.
- Jedna architektura informacji we wszystkich stylach: dziewięć dotychczasowych kafelków 3×3 z przewijaniem całego panelu w pionie, plan domu, najbliższe czynności i trzy skróty. **Bez zmiany działania modułów.**
- Karty, przyciski i kafelki mają zaokrąglenia, kontrast, miękkie obramowania i dotykowe podświetlenie. Kafelki mają spójne, natywnie rysowane ikony i uchwyt `⋮⋮`. Dom na karcie podsumowania jest lokalną ilustracją, nie zasobem pobieranym.
- **Dotknij kafelka** → otwórz moduł. **Przytrzymaj** → menu `Edytuj kafelek` / `Przesuń kafelek`. **Przeciągnij uchwyt** → zmień kolejność. W trybie układania można też przytrzymać cały kafelek i przeciągnąć. W trakcie ruchu podświetlony jest cel; na krawędzi ekranu przewijanie ułatwia przenoszenie pomiędzy rzędami. `Zakończ układanie` kończy tryb.
- Edycja zmienia **wyłącznie podpis (1–24 znaki) i barwę**: domyślna, miętowa, niebieska, bursztynowa, fioletowa. `Przywróć wygląd` cofa lokalne zmiany. Identyfikator i działanie modułu są stałe; dziewięciu kafelków systemowych nie usuwamy.
- Ustawienia wizualne, nazwy i kolory kafelków zapisują się w SharedPreferences, przechodzą przez zwykłą aktualizację na podpisanym APK i przez kopię JSON (`settings.homeTileAppearance`). Stare JSON bez tego pola zachowują kompatybilność.
- **SQLite bez zmiany: v10**; nie ma żadnych nowych tabel, zmiany terminów zadań, przypomnień, PIN-u ani uprawnień. Beta pozostaje bez PIN, Stable z PIN.

## Test odbioru na Androidzie

1. Wyeksportuj kopię JSON na zewnątrz aplikacji. Zaktualizuj z 0.3.2-beta.2 do 0.3.3 bez odinstalowania.
2. Porównaj wygląd głównego ekranu w każdym z czterech stylów: 9 widocznych kafelków, możliwość przewijania, brak nakładania się tekstu i przycisków na małym wyświetlaczu. Sprawdź czytelność trybu Pastelowy przy dużej czcionce systemowej.
3. Krótko dotknij `Kalendarz` → otwiera się Kalendarz. Wróć i przytrzymaj `Kalendarz` → menu edycji/przesuwania. Zmień podpis i kolor, zamknij/uruchom EDHOME → wybór zostaje, dotknięcie nadal otwiera Kalendarz.
4. Przytrzymaj kafelek → Przesuń, przeciągnij go za uchwyt w inne miejsce (również do innego wiersza), przewiń ekran w trakcie przeciągania. `Zakończ układanie`, zrestartuj aplikację → kolejność zostaje. Sprawdź, że karta planu i przyciski na dole nadal są klikalne.
5. Użyj `Przywróć wygląd`, sprawdź nazwę i kolor domyślny. Przełącz motyw, uruchom ponownie, sprawdź trwałość wyboru.
6. Testowo zaimportuj kopię ze starym motywem i nową kopię ze spersonalizowanymi kafelkami na **oddzielnej/testowej instalacji**; historia, zadania i przypomnienia są niezmienione. Beta uruchamia się bez PIN.
7. Potwierdź, że aktualizacje z manifestu nadal wykrywają nowe APK i zachowują dane. Build i testy CI nie zastępują powyższego testu ekranu i gestów na telefonie.

## Kolejność dalszego rozwoju

Następna wersja funkcjonalna **0.3.4-beta.1**: lokalne minutniki pralki/suszarki/zmywarki. Wygląd 0.3.3 to osobny inkrement, nie deklaracja gotowości minutników czy synchronizacji Wi-Fi.

## Korekta UI — 0.3.3-beta.3

- Dwa dodatkowe **ciemne motywy EDHOME**: `WMM` (grafit/turkus) oraz `Trener 2` (czerń/czerwień); razem sześć stylów. Są inspirowane tamtymi aplikacjami, ale **nie zmieniają** ich ustawień ani nie gwarantują dosłownej zgodności bez ich zrzutów wzorcowych.
- Motyw jest ustawieniem **lokalnej instalacji EDHOME na danym urządzeniu**, nie przypisaniem do domownika; także podpisy, kolory, ikony i kolejność kafelków są lokalne. Import kopii JSON odtwarza te ustawienia na instalacji docelowej. Brak synchronizacji motywu między telefonami przed planowanym etapem Wi-Fi.
- `Edytuj kafelek`: wybór ikony z lokalnego katalogu z podglądem (9 dotychczasowych oraz miniatury pralki, suszarki i zmywarki); wciąż jest to tylko wygląd, nie uruchamia jeszcze minutnika. Ikona zachowuje się po restarcie i w kopii JSON.
- W trakcie przeciągania inne kafelki **płynnie rozsuwają się na przewidywane miejsca**; widoczna luka i opis pozycji wskazują, gdzie trafi przenoszony kafelek. Zapis kolejności dopiero po upuszczeniu; anulowanie przywraca wygląd bez zapisu.
- SQLite v10, Beta bez PIN-u, Stable z PIN-em. Weryfikacja animacji i wszystkich motywów wymaga testu na prawdziwym telefonie.
