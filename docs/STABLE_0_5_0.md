# EDHOME 0.5.0 Stable — zmiany względem 0.3.4

Źródło funkcjonalne: beta commit 4ebe1dbd6fac71766cb4663396d99381fbf80baf. Zmiany zatwierdzone przez Edwina 22.09.2026.

## Co nowego

- Minutniki pralki, suszarki i zmywarki; powiadomienia i ustawiane godziny ciszy.
- Rotacyjne obowiązki domowników i zapis wykonawcy w historii.
- Intuicyjniejsze układanie kafelków i ich edycja.
- Spiżarnia: skaner, kody EAN, źródła Open Facts na życzenie, kategorie, pełne opakowania, seryjny skan, wyszukiwanie po nazwie i własne kody.
- Zakupy: osobne przyjęcie kupionych produktów na stan spiżarni.
- Rzeczy, pudełka, QR, lokalizacja i wypożyczanie.
- PayCheck: wyłącznie wspólny budżet gospodarstwa, ręczne operacje oraz wspólne cele finansowe.

## Aktualizacja i bezpieczeństwo

- Schemat SQLite v12 → v21; instalować na poprzedniej Stable bez odinstalowania, po eksporcie kopii danych.
- Official Android label: EDHOME, package com.edwinkarolczyk.edhome, versionCode 45, versionName 0.5.0.
- Stable zachowuje PIN. Nie przenosimy niechronionych prywatnych finansów z Bety.
- Build wykorzystuje ten sam trwały certyfikat co Stable 0.3.4; manifest i publiczne APK Beta pozostają bez zmian.
- GitHub Actions sprawdza migracje i podpis, ale nie zastępuje próby aktualizacji i skanera na telefonie.
- Wcześniejszy Stable 0.3.4 nie ma własnego kanału aktualizacji APK poza Google Play: instalacja tego pliku jest ręczna, a systemowy instalator nie wyświetli własnej listy zmian.
