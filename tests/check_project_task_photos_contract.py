#!/usr/bin/env python3
"""Projekty: dwa zdjęcia czynności, osobny podgląd i bezpieczne edycje Android/Desktop."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/edwinkarolczyk/edhome"
main = (src / "MainActivity.java").read_text(encoding="utf-8")
viewer = (src / "ProjectTaskPhotoDialog.java").read_text(encoding="utf-8")
project = (src / "ProjectStore.java").read_text(encoding="utf-8")
backup = (src / "DataBackup.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(
    encoding="utf-8"
)

# Zwinięta karta nadal ma dwie miniaturki, lecz nie pokazuje przycisków pracy.
cards = main.split("private void renderProjectTasks(", 1)[1].split(
    "private void showProjectDependentsDialog(", 1
)[0]
assert "projectTaskThumbnailView(taskId,slot)" in cards
assert "for(int slot=1;slot<=2;slot++)" in cards
assert 'final String expandedKey="project_expanded_task_"+project.id;' in cards
assert "details.setVisibility(expanded?View.VISIBLE:View.GONE)" in cards
assert 'compactAction(work,"Czas",()->showProjectWorkHistory(taskId,taskName))' in cards
assert 'check.setEnabled(done||!completionBlocked);' in cards
assert "bindProjectTimeStatus(projectTime,box,taskId,minutes,activeStarted)" in cards
assert "setOnClickListener(v->toggle.run())" in cards

# Kliknięcie zdjęcia NIE rozpoczyna wymiany ani nie wykonuje czynności.
thumb = main.split("private View projectTaskThumbnailView(",1)[1].split(
    "private void editProject(",1
)[0]
assert "if(bitmap==null)chooseProjectTaskPhotoSource(taskId,slot);" in thumb
assert "else showProjectTaskPhoto(taskId,slot);" in thumb
assert "confirmDeleteProjectTaskPhoto(taskId,slot);" in thumb
assert "selectProjectTaskThumbnail(taskId,slot)" not in thumb
assert "ProjectTaskPhotoDialog.show(this,first,second,selectedSlot" in main
assert 'new String[]{"Zrób zdjęcie aparatem","Wybierz z galerii"}' in main
assert "FileProvider.getUriForFile(this," in main
assert "TAKE_PROJECT_TASK_THUMBNAIL" in main
assert "PROJECT_TASK_CAMERA_PERMISSION" in main
assert "StorageThumbs.compress(getContentResolver(),photo)" in main
assert "ProjectStore.setTaskThumbnail(" in main

# Ekran podglądu nie ma akcji START/STOP, obsługuje gesty i świadome usunięcie.
for marker in (
    "Theme_Black_NoTitleBar_Fullscreen",
    "ScaleGestureDetector",
    "MotionEvent.ACTION_POINTER_DOWN",
    "MotionEvent.ACTION_MOVE",
    "MotionEvent.ACTION_UP",
    "Math.abs(dx)>threshold",
    "switcher.accept(dx<0?1:-1)",
    "actions.replace(slot)",
    "actions.remove(slot)",
    'button(activity,"Zamknij")',
):
    assert marker in viewer, marker
assert "startWork(" not in viewer
assert "completeTask(" not in viewer

# Już istniejąca baza zdjęć jest podłączona do ZIP i modelu PC.
for marker in ('task_thumbnail_1_base64','task_thumbnail_2_base64'):
    assert marker in project and marker in backup and marker in desktop, marker
assert "static void setTaskThumbnail(" in project
assert "desktopProjectTaskThumbnailButton(task,1)" in desktop
assert "desktopProjectTaskThumbnailButton(task,2)" in desktop
assert "showDesktopProjectTaskPhoto(task,slot)" in desktop
assert "zoom[0]*1.25" in desktop
assert "JOptionPane.showConfirmDialog(dialog," in desktop
assert "chooseDesktopProjectTaskThumbnail(task,slot)" in desktop
print("PASS: Projekty — dwa zdjęcia, podgląd, gesty, aparat/galeria, Desktop i czas bez regresji")
