"""EDHOME: lokalny konwerter tekstowego PDF VeloBank do CSV.
Windows: pip install pdfplumber; python pdf_converter_windows.py
Nie przesyła danych do internetu. Nie wykonuje OCR.
"""
import csv
import hashlib
import re
import tkinter as tk
from datetime import datetime
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

DATE = re.compile(r"(?<!\\d)(\\d{2}\\.\\d{2}\\.\\d{4})(?!\\d)")
MONEY = re.compile(r"(?<!\\d)([-+−]?\\s*\\d{1,3}(?:[ \\u00a0]\\d{3})*[,.]\\d{2})\\s*(?:PLN|zł)?", re.I)
OP = re.compile(r"przelew|transakcj|operacj|wpłat|wypłat|zakup|płatnoś|obciąż", re.I)


def parse(text):
    rows, issues, seen = [], [], set()
    for line in text.splitlines():
        line = " ".join(line.split())
        match = DATE.search(line)
        if not match or not OP.search(line):
            continue
        day = match.group(1)
        try:
            date = datetime.strptime(day, "%d.%m.%Y").date().isoformat()
        except ValueError:
            issues.append((day, "Nieprawidłowa data", line[:160]))
            continue
        body = line.replace(day, "", 1)
        # A second date is typically the posting date, not an amount.
        body = DATE.sub("", body)
        candidates = [m.group(1).replace(" ", "").replace("\u00a0", "").replace("−", "-")
                      for m in MONEY.finditer(body)]
        signed = [v for v in candidates if v.startswith(("+", "-"))]
        if len(signed) == 1:
            chosen = signed[0]
        elif len(candidates) == 1:
            chosen = candidates[0]
        else:
            issues.append((day, "Brak kwoty lub wiele możliwych kwot", line[:160]))
            continue
        lower = body.lower()
        outgoing = bool(re.search(r"przelew na rachunek|wychodząc|zakup|kartą|płatnoś|obciąż|wypłat", lower))
        incoming = bool(re.search(r"przelew z rachunku|przychodząc|wpływ|wpłat|uznan", lower))
        if chosen[0] == "-":
            sign = "-"
        elif chosen[0] == "+":
            sign = "+"
        elif outgoing != incoming:
            sign = "-" if outgoing else "+"
        else:
            issues.append((day, "Nieznany kierunek", line[:160]))
            continue
        number = chosen.lstrip("+-").replace(",", ".")
        try:
            from decimal import Decimal
            grosz = int(Decimal(number) * 100)
            if grosz <= 0:
                raise ValueError("Kwota musi być dodatnia")
        except Exception:
            issues.append((day, "Nieprawidłowa kwota", line[:160]))
            continue
        fingerprint = hashlib.sha256(("velobank-pdf-row\n" + date + "|" + line.lower()).encode()).hexdigest()
        if fingerprint in seen:
            issues.append((day, "Powtórzony wiersz w pliku", line[:160]))
            continue
        seen.add(fingerprint)
        rows.append([date, f"{sign}{grosz//100},{grosz%100:02d}", "velo-pdf-" + fingerprint, line[:300]])
    return rows, issues


def extract(path):
    import pdfplumber
    with pdfplumber.open(path) as pdf:
        if len(pdf.pages) > 300:
            raise ValueError("PDF ma ponad 300 stron")
        return "\n".join(page.extract_text(layout=False) or "" for page in pdf.pages)


class App:
    def __init__(self, root):
        self.root = root
        root.title("EDHOME — Konwerter PDF do CSV")
        root.geometry("940x600")
        self.rows, self.issues = [], []
        toolbar = ttk.Frame(root, padding=10)
        toolbar.pack(fill="x")
        ttk.Button(toolbar, text="Otwórz PDF", command=self.open).pack(side="left")
        ttk.Button(toolbar, text="Zapisz CSV", command=self.save).pack(side="left", padx=10)
        self.info = ttk.Label(toolbar, text="Wszystkie dane pozostają na komputerze.")
        self.info.pack(side="left")
        self.table = ttk.Treeview(root, columns=("data", "kwota", "opis"), show="headings")
        for col, width in (("data", 110), ("kwota", 120), ("opis", 670)):
            self.table.heading(col, text=col.capitalize())
            self.table.column(col, width=width)
        self.table.pack(fill="both", expand=True, padx=10)
        self.errors = tk.Text(root, height=9, wrap="word")
        self.errors.pack(fill="x", padx=10, pady=10)

    def open(self):
        name = filedialog.askopenfilename(filetypes=[("Dokument PDF", "*.pdf")])
        if not name:
            return
        try:
            if Path(name).stat().st_size > 8 * 1024 * 1024:
                raise ValueError("PDF przekracza 8 MB")
            self.rows, self.issues = parse(extract(name))
            self.table.delete(*self.table.get_children())
            for row in self.rows:
                self.table.insert("", "end", values=(row[0], row[1], row[3]))
            self.errors.delete("1.0", "end")
            for date, reason, line in self.issues:
                self.errors.insert("end", f"{date}: {reason}: {line}\n")
            self.info.config(text=f"Poprawne: {len(self.rows)} | Do kontroli: {len(self.issues)}")
        except Exception as exc:
            messagebox.showerror("Błąd PDF", str(exc))

    def save(self):
        if not self.rows:
            messagebox.showwarning("Brak danych", "Nie ma poprawnie rozpoznanych operacji.")
            return
        if self.issues and not messagebox.askyesno(
            "Niepełny odczyt", f"{len(self.issues)} wierszy wymaga kontroli. "
            "CSV będzie zawierał tylko poprawnie rozpoznane operacje. Kontynuować?"
        ):
            return
        path = filedialog.asksaveasfilename(defaultextension=".csv",
                                            initialfile="EDHOME-VeloBank.csv",
                                            filetypes=[("CSV", "*.csv")])
        if not path:
            return
        with open(path, "w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.writer(handle, delimiter=";")
            writer.writerow(["Data", "Kwota", "Id transakcji", "Opis"])
            writer.writerows(self.rows)
        messagebox.showinfo("Zapisano", "CSV gotowy do importu w EDHOME. Sprawdź go z PDF.")


if __name__ == "__main__":
    window = tk.Tk()
    App(window)
    window.mainloop()
