#!/usr/bin/env python3
"""EDHOME Desktop settings/pairing stabilization contract."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(
    encoding="utf-8"
)

start = src.index("private JComponent settings()")
end = src.index("private String desktopDiagnosticsText()", start)
settings = src[start:end]

for marker in (
    'page("Ustawienia • telefon i diagnostyka")',
    'actionButton("Połącz telefon przez QR")',
    'actionButton("Aktualizuj EDHOME Desktop — 1 klik")',
    'actionButton("Zapisz diagnostykę Desktop na Pulpit")',
    'showQrPairing(pairState, qrPair)',
    'oneClickDesktopUpdate(updateDesktop)',
    'hubServer.newestClient()',
    'DesktopHubServer.PORT',
):
    assert marker in settings, f"Missing compact Desktop setting: {marker}"

for obsolete in (
    "Adres telefonu:", "Kod parowania:", "Pobierz ręcznie przez Wi‑Fi",
    "Wczytaj backup JSON", "Pobierz nowe logi z telefonu",
    "Kopiuj diagnostykę EDHOME Desktop", "Zapisz diagnostykę Desktop TXT",
    "Uruchamiaj EDHOME Desktop razem z Windows",
    "Po starcie Windows uruchamiaj zminimalizowany do zasobnika",
    "Automatycznie pobieraj zmiany z telefonu w tle",
    "Automatycznie zapisuj zmiany z PC do telefonu",
):
    assert obsolete not in settings, f"Old Settings clutter still visible: {obsolete}"

for marker in (
    'PREFS.put("phoneIp", pairedHost)',
    'PREFS.put("token", payload.token)',
    'DesktopDiagnosticLog.event("QR_PAIRING_OK"',
    'pullFromPhone(pairedHost, payload.token, null)',
    'LanClient.discover(secret, PORT)',
    'ackDiagnostics(phone.id)',
    'sanitizedPhoneHost()',
    'PHONE_IP_SELF_REJECTED',
    'QR_PAIRING_SELF_IP_REJECTED',
):
    assert marker in src, f"Missing pairing/diagnostics stabilization: {marker}"

for marker in ('hubFirewallReady','localport=45823','localport=45822','DesktopHubServer.PORT'):
    assert marker in src, f"Missing Desktop Hub/firewall contract: {marker}"

print("EDHOME Desktop compact Settings + Hub QR/diagnostics: PASS")
