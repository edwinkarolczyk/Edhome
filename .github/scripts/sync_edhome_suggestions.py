#!/usr/bin/env python3
"""Twórz publiczną listę sugestii EDHOME z Issues zatwierdzonych na GitHubie.

Uruchamiane przez workflow na beta (push/workflow_dispatch). Bez tokenu w APK.
Treść Issues traktujemy jako niezaufany tekst, nie kod do wykonania.
"""
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen

REPO = os.environ.get("GITHUB_REPOSITORY", "edwinkarolczyk/Edhome")
TOKEN = os.environ.get("GH_TOKEN", "")
OWNER = "edwinkarolczyk"
TARGET = Path("docs/EDHOME_SUGESTIE.md")
PREFIXES = ("[EDHOME Sugestia] ", "[EDHOME Błąd] ")


def fetch_issues():
    if not TOKEN:
        raise RuntimeError("Brak tokenu GH_TOKEN akcji GitHub")
    results = []
    for page in range(1, 11):
        url = (
            f"https://api.github.com/repos/{REPO}/issues"
            f"?state=all&per_page=100&sort=created&direction=asc&page={page}"
        )
        request = Request(url, headers={
            "Authorization": f"Bearer {TOKEN}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        })
        with urlopen(request, timeout=25) as response:
            rows = json.load(response)
        if not isinstance(rows, list):
            raise RuntimeError("GitHub zwrócił nieprawidłową odpowiedź Issues")
        results.extend(rows)
        if len(rows) < 100:
            break
    return results


def single_line(text, limit=160):
    """Zamień niezaufany tekst na bezpieczną linię Markdown."""
    value = " ".join(str(text or "").replace("\x00", "").split())
    return value[:limit].replace("<", "&lt;").replace(">", "&gt;")


def entry(issue):
    if "pull_request" in issue:
        return ""
    user = issue.get("user") or {}
    if user.get("login", "").casefold() != OWNER.casefold():
        return ""
    title = str(issue.get("title") or "")
    if not any(title.startswith(prefix) for prefix in PREFIXES):
        return ""
    issue_id = issue.get("number")
    if not isinstance(issue_id, int):
        return ""
    kind = "Błąd" if title.startswith("[EDHOME Błąd] ") else "Sugestia"
    summary = single_line(title.partition("] ")[2], 140)
    body = str(issue.get("body") or "")
    body = body.replace("\x00", "").replace("\r\n", "\n").replace("\r", "\n")
    # Treść wyłącznie do odczytu. Nie może otworzyć fence i wstrzyknąć Markdown.
    body = body[:3500].replace("```", "` ` `")
    quote = "\n".join("> " + line if line else ">" for line in body.split("\n"))
    url = f"https://github.com/{REPO}/issues/{issue_id}"
    status = "zamknięte" if issue.get("state") == "closed" else "otwarte"
    created = single_line(str(issue.get("created_at", ""))[:10], 10)
    return (
        f"<!-- edhome-issue:{issue_id} -->\n"
        f"## {kind}: {summary}\n\n"
        f"- [GitHub Issue #{issue_id}]({url})\n"
        f"- Status: {status} | Data: {created}\n\n"
        f"{quote}\n\n"
    )


def main():
    issues = fetch_issues()
    issues.sort(key=lambda issue: int(issue.get("number") or 0))
    blocks = [entry(issue) for issue in issues]
    accepted = [block for block in blocks if block]
    intro = (
        "# EDHOME — sugestie i zgłoszenia\n\n"
        "Źródło: [GitHub Issues](https://github.com/"
        f"{REPO}/issues). Publikacja wymaga konta GitHub i potwierdzenia.\n"
        "Zestawienie aktualizuje się przy kolejnej zmianie w gałęzi `beta`, "
        "a nie natychmiast po zgłoszeniu.\n"
        "Uwzględniane są tylko zgłoszenia właściciela repozytorium z "
        "prefiksem EDHOME Sugestia/Błąd.\n\n---\n\n"
    )
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    output = intro + ("".join(accepted) if accepted
        else "Brak zgłoszeń do zebrania.\n")
    TARGET.write_text(output, encoding="utf-8")
    print(f"Zebrano {len(accepted)} zgłoszeń właściciela (spośród {len(issues)} Issues).")


if __name__ == "__main__":
    main()
