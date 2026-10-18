#!/usr/bin/env python3
"""Kontrakt niebieskiego pulsowania kart Projektów: Android + Desktop Beta."""
from pathlib import Path

android = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
pulse = Path("app/src/main/java/com/edwinkarolczyk/edhome/ProjectTaskPulse.java").read_text(encoding="utf-8")
desktop = Path("desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")

# Android: tylko karta aktywnej sesji. Zdarzenie Stop z powiadomienia
# odtwarza zwykły kolor i aktualizuje akcję raz, bez 1-sekundowego render().
assert 'bindProjectTimeStatus(projectTime,box,taskId,minutes,activeStarted);' in android
block = android.split('private void bindProjectTimeStatus(',1)[1].split(
    'private void showProjectWorkHistory(',1)[0]
for expected in (
    'if(activeStarted==null)', 'final GradientDrawable pulseBackground',
    'final int blueSurface=ProjectTaskPulse.blueSurface(surface,skin.light);',
    'pulseBackground.setColor(ProjectTaskPulse.color(',
    'view.postDelayed(refresh[0],1000L);',
    'if(started==null)', 'pulseBackground.setColor(ordinarySurface);',
    'if("projects".equals(screen)&&selectedProjectId>0)render();',
    '!view.isAttachedToWindow()||!card.isAttachedToWindow()',
):
    assert expected in block, "Android active card pulse: "+expected
assert block.count('view.postDelayed(refresh[0],1000L);')==1
assert 'boolean isBlue(long tick)' in pulse
assert 'return (tick & 1L) == 0L;' in pulse
assert 'light ? 0.35f : 0.46f' in pulse

# Desktop: ten sam 1-sekundowy Timer Swing co licznik, nie drugi wątek.
assert 'RoundedPanel card=new RoundedPanel(APP_SURFACE,16);' in desktop
assert 'bindDesktopProjectClock(clock,card,taskId,duration);' in desktop
desktop_clock = desktop.split('private void bindDesktopProjectClock(',1)[1].split(
    'private int desktopOpenDependencyCount(',1)[0]
for expected in (
    'card.setFillColor(APP_TASK_RUNNING_BLUE);',
    'new javax.swing.Timer(1000,null)',
    'blue[0]=!blue[0];',
    'card.setFillColor(blue[0]?APP_TASK_RUNNING_BLUE:APP_SURFACE);',
    'if (desktopActiveProjectWorkSession(taskId)==null)',
    'card.setFillColor(APP_SURFACE);',
    'timer.stop();',
    'if (!clock.isShowing() || !"Projekty".equals(current))',
):
    assert expected in desktop_clock, "Desktop project pulse: "+expected
assert 'void setFillColor(Color color)' in desktop
assert 'private Color fill;' in desktop
print("Project active-task blue pulse Android/Desktop 1 s, reset after Stop: PASS")
