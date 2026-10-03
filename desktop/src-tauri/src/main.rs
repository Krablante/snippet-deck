#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod expansion;
mod google;
mod library;
mod sync;
mod update;

use expansion::Expander;
use library::{trigger_key, Library, Snippet};
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::{
    fs,
    path::PathBuf,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
};
use sync::{same_library, LocalState};
use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    Emitter, Manager,
};
use tauri_plugin_autostart::ManagerExt;

struct AppState {
    library: Mutex<Library>,
    library_error: Mutex<Option<String>>,
    import: Mutex<Option<Library>>,
    path: PathBuf,
    active_path: PathBuf,
    sync_path: PathBuf,
    sync_disabled_path: PathBuf,
    sync_lock: Mutex<()>,
    sync_active: AtomicBool,
    access: Mutex<Option<google::Access>>,
    available_update: Mutex<Option<update::Release>>,
    update_lock: Mutex<()>,
    install_lock: Mutex<()>,
    expander: Arc<Expander>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Snapshot {
    snippets: Vec<Snippet>,
    library_error: Option<String>,
    active: bool,
    start_at_login: bool,
    status: String,
    sync_connected: bool,
    sync_history: bool,
    update_available: Option<update::Release>,
}

#[cfg(test)]
#[test]
fn snapshot_uses_the_field_names_read_by_the_editor() {
    let snapshot = Snapshot {
        snippets: Vec::new(),
        library_error: None,
        active: true,
        start_at_login: true,
        status: String::new(),
        sync_connected: true,
        sync_history: true,
        update_available: None,
    };
    let value = serde_json::to_value(snapshot).unwrap();
    assert_eq!(value["startAtLogin"], true);
    assert_eq!(value["syncConnected"], true);
    assert_eq!(value["syncHistory"], true);
    assert!(value["updateAvailable"].is_null());
}

#[tauri::command]
fn snapshot(app: tauri::AppHandle, state: tauri::State<'_, AppState>) -> Snapshot {
    Snapshot {
        snippets: state.library.lock().unwrap().snippets.clone(),
        library_error: state.library_error.lock().unwrap().clone(),
        active: state.expander.enabled.load(Ordering::Relaxed),
        start_at_login: app.autolaunch().is_enabled().unwrap_or(false),
        status: state.expander.status.read().unwrap().clone(),
        sync_connected: state.sync_active.load(Ordering::Relaxed),
        sync_history: state.sync_path.exists(),
        update_available: state.available_update.lock().unwrap().clone(),
    }
}

#[tauri::command]
async fn check_for_updates(app: tauri::AppHandle) -> Result<Option<update::Release>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<AppState>();
        let _guard = state.update_lock.lock().unwrap();
        let found = update::check(&app.package_info().version.to_string())?;
        *state.available_update.lock().unwrap() = found.clone();
        Ok(found)
    })
    .await
    .map_err(|e| format!("Update check stopped: {e}"))?
}

#[tauri::command]
async fn install_update(app: tauri::AppHandle) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<AppState>();
        let _guard = state.install_lock.lock().unwrap();
        let release = state
            .available_update
            .lock()
            .unwrap()
            .clone()
            .ok_or("Check for updates before downloading")?;
        let cache = app
            .path()
            .app_cache_dir()
            .map_err(|e| e.to_string())?
            .join("updates");
        let path = update::download(&release, &cache, |received| {
            let percent = (received * 100 / release.size) as u8;
            let _ = app.emit("snippetdeck-update-progress", percent);
        })?;
        update::open_installer(&path)?;
        #[cfg(target_os = "windows")]
        app.exit(0);
        Ok(path.display().to_string())
    })
    .await
    .map_err(|e| format!("Update installation stopped: {e}"))?
}

fn save_change(
    state: &AppState,
    change: impl FnOnce(&mut Library) -> Result<(), String>,
) -> Result<(), String> {
    let mut current = state.library.lock().unwrap();
    if state.library_error.lock().unwrap().is_some() {
        return Err("The saved library could not be read. Import a backup to recover it.".into());
    }
    let mut next = current.clone();
    change(&mut next)?;
    next.save(&state.path)?;
    state.expander.update(next.snippets.clone());
    *current = next;
    Ok(())
}

#[tauri::command]
fn save_snippet(
    state: tauri::State<'_, AppState>,
    previous: Option<String>,
    snippet: Snippet,
    expected: Option<Snippet>,
) -> Result<(), String> {
    save_change(&state, |library| {
        if let Some(trigger) = &previous {
            let current = library
                .snippets
                .iter()
                .find(|s| trigger_key(&s.trigger) == trigger_key(trigger));
            if expected.is_none() || current != expected.as_ref() {
                return Err("Snippet changed while editing. Reopen it before saving.".into());
            }
        }
        library.put(previous.as_deref(), snippet)
    })
}

#[tauri::command]
fn remove_snippet(
    state: tauri::State<'_, AppState>,
    trigger: String,
    expected: Snippet,
) -> Result<(), String> {
    save_change(&state, |library| {
        let current = library
            .snippets
            .iter()
            .find(|s| trigger_key(&s.trigger) == trigger_key(&trigger));
        if current != Some(&expected) {
            return Err("Snippet changed. Reopen it before deleting.".into());
        }
        library.remove(&trigger)
    })
}

#[tauri::command]
fn set_active(state: tauri::State<'_, AppState>, active: bool) -> Result<(), String> {
    if active && state.library_error.lock().unwrap().is_some() {
        return Err("Import a backup before enabling text expansion".into());
    }
    state.persist_active(active)?;
    state.expander.enabled.store(active, Ordering::Relaxed);
    Ok(())
}

#[tauri::command]
fn set_start_at_login(app: tauri::AppHandle, enabled: bool) -> Result<(), String> {
    if enabled {
        app.autolaunch().enable()
    } else {
        app.autolaunch().disable()
    }
    .map_err(|e| format!("Cannot change launch at login: {e}"))
}

impl AppState {
    fn persist_active(&self, active: bool) -> Result<(), String> {
        fs::create_dir_all(self.active_path.parent().ok_or("Invalid settings path")?)
            .map_err(|e| e.to_string())?;
        fs::write(&self.active_path, if active { "on" } else { "off" })
            .map_err(|e| format!("Cannot save settings: {e}"))
    }
}

#[tauri::command]
fn choose_import(
    app: tauri::AppHandle,
    state: tauri::State<'_, AppState>,
) -> Result<Option<usize>, String> {
    state.expander.dialog_open.store(true, Ordering::Relaxed);
    let chosen = choose_backup(&app);
    state.expander.dialog_open.store(false, Ordering::Relaxed);
    let path = match chosen? {
        Some(path) => path,
        None => return Ok(None),
    };
    if fs::metadata(&path).map_err(|e| e.to_string())?.len() > 4_000_000 {
        return Err("Backup exceeds the size limit".into());
    }
    let text = fs::read_to_string(path).map_err(|e| e.to_string())?;
    let library = Library::decode(&text)?;
    let count = library.snippets.len();
    *state.import.lock().unwrap() = Some(library);
    Ok(Some(count))
}

#[tauri::command]
fn finish_import(state: tauri::State<'_, AppState>) -> Result<(), String> {
    let library = state
        .import
        .lock()
        .unwrap()
        .take()
        .ok_or("Choose a backup first")?;
    // A confirmed import is also the recovery path for an unreadable working file.
    let mut current = state.library.lock().unwrap();
    library.save(&state.path)?;
    state.expander.update(library.snippets.clone());
    *current = library;
    *state.library_error.lock().unwrap() = None;
    state.expander.enabled.store(
        !fs::read_to_string(&state.active_path).is_ok_and(|value| value == "off"),
        Ordering::Relaxed,
    );
    Ok(())
}

#[derive(Serialize)]
struct SyncResult {
    count: usize,
    conflicts: Vec<String>,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum SyncChoice {
    Merge,
    ThisDevice,
    OtherDevice,
}

fn sync_internal(
    app: &tauri::AppHandle,
    interactive: bool,
    choice: SyncChoice,
) -> Result<SyncResult, String> {
    let state = app.state::<AppState>();
    let _guard = state.sync_lock.lock().unwrap();
    if state.library_error.lock().unwrap().is_some() {
        return Err("Import a backup before syncing an unreadable library".into());
    }
    if !interactive && !state.sync_active.load(Ordering::Relaxed) {
        return Err("Google Drive is disconnected".into());
    }
    let token = {
        let mut access = state.access.lock().unwrap();
        if access
            .as_ref()
            .is_none_or(|a| a.expires <= std::time::Instant::now())
        {
            let saved = access
                .as_ref()
                .map(|a| a.refresh.clone())
                .or_else(google::saved_refresh);
            let new_access = match saved {
                Some(refresh) => google::refresh(&refresh).or_else(|e| {
                    if interactive {
                        google::authorize()
                    } else {
                        Err(e)
                    }
                })?,
                None if interactive => google::authorize()?,
                None => return Err("Connect Google Drive first".into()),
            };
            if let Ok(entry) = google::credential() {
                let _ = entry.set_password(&new_access.refresh);
            }
            *access = Some(new_access);
        }
        let access = access.as_ref().ok_or("Google Drive is not connected")?;
        access.token.clone()
    };
    let drive = google::Drive::new(token);
    let account = drive.account_id()?;
    let old_state = LocalState::load(&state.sync_path)?;
    if old_state.as_ref().is_some_and(|s| s.account_id != account) {
        return Err("This device was connected to a different Google account".into());
    }
    let library = state.library.lock().unwrap().clone();
    let mut local = old_state
        .as_ref()
        .cloned()
        .unwrap_or_else(|| LocalState::fresh(account));
    if choice == SyncChoice::Merge {
        local.collect_changes(&library)?;
    }
    let files = drive.list()?;
    let names: std::collections::HashSet<_> = files.iter().map(|file| &file.name).collect();
    if names.len() != files.len() {
        return Err("Duplicate device files in Google Drive".into());
    }
    let name = format!("snippetdeck-sync-v1-{}.json", local.replica.device_id);
    let own: Vec<_> = files.iter().filter(|file| file.name == name).collect();
    if own.len() > 1 {
        return Err("Duplicate device files in Google Drive".into());
    }
    let others: Vec<_> = files.iter().filter(|file| file.name != name).collect();
    if choice == SyncChoice::OtherDevice && others.len() != 1 {
        return Err(
            "Use the preferred device to resolve a conflict across multiple devices".into(),
        );
    }
    let mut preferred = None;
    let mut seen = local.seen_files.clone();
    seen.retain(|id, _| others.iter().any(|file| &file.id == id));
    let merge = local.replica.merge_from(
        others
            .iter()
            .filter(|file| choice != SyncChoice::Merge || file.needs_download(&local.seen_files))
            .map(|file| {
                let replica = drive.read(file)?;
                if let Some(version) = &file.version {
                    seen.insert(file.id.clone(), version.clone());
                }
                if choice == SyncChoice::OtherDevice {
                    preferred = Some(replica.clone());
                }
                Ok(replica)
            }),
        choice == SyncChoice::Merge,
    )?;
    if choice == SyncChoice::ThisDevice {
        local.replica = merge.replica;
        local.keep_local(&library);
    } else if choice == SyncChoice::OtherDevice {
        let chosen = preferred
            .ok_or("The other device is unavailable")?
            .merge(&[], true)?
            .library
            .ok_or("The other device has unresolved conflicts")?;
        if !same_library(&library, &chosen) {
            save_change(&state, |current| {
                if !same_library(current, &library) {
                    return Err("Library changed while syncing; retry".into());
                }
                *current = chosen.clone();
                Ok(())
            })?;
        }
        local.replica = merge.replica;
        local.keep_local(&chosen);
    } else {
        if let Some(remote) = &merge.library {
            if !same_library(&library, remote) {
                save_change(&state, |current| {
                    if !same_library(current, &library) {
                        return Err("Library changed while syncing; retry".into());
                    }
                    *current = remote.clone();
                    Ok(())
                })?;
            }
            local.baseline = remote.snippets.clone();
        } else {
            local.baseline = library.snippets.clone();
        }
        local.replica = merge.replica;
    }
    local.file_id = own.first().map(|file| file.id.clone());
    local.seen_files = seen;
    if old_state.as_ref() != Some(&local) {
        local.save(&state.sync_path)?;
    }
    if choice != SyncChoice::Merge || merge.conflicts.is_empty() {
        let hash = Sha256::digest(serde_json::to_vec(&local.replica).map_err(|e| e.to_string())?);
        let hash: String = hash.iter().map(|byte| format!("{byte:02x}")).collect();
        if local.file_id.is_none() || local.last_uploaded_hash.as_ref() != Some(&hash) {
            local.file_id = Some(drive.write(&local.replica, local.file_id.as_deref())?);
            local.last_uploaded_hash = Some(hash);
            local.save(&state.sync_path)?;
        }
    }
    if state.sync_disabled_path.exists() {
        fs::remove_file(&state.sync_disabled_path)
            .map_err(|e| format!("Cannot enable sync: {e}"))?;
    }
    state.sync_active.store(true, Ordering::Relaxed);
    Ok(SyncResult {
        count: local.baseline.len(),
        conflicts: if choice != SyncChoice::Merge {
            Vec::new()
        } else {
            merge.conflicts
        },
    })
}

#[tauri::command]
async fn sync_now(
    app: tauri::AppHandle,
    interactive: bool,
    keep_local: bool,
    use_other: bool,
) -> Result<SyncResult, String> {
    if keep_local && use_other {
        return Err("Choose one library to keep".into());
    }
    let choice = if keep_local {
        SyncChoice::ThisDevice
    } else if use_other {
        SyncChoice::OtherDevice
    } else {
        SyncChoice::Merge
    };
    tauri::async_runtime::spawn_blocking(move || sync_internal(&app, interactive, choice))
        .await
        .map_err(|e| format!("Cannot finish sync: {e}"))?
}

#[tauri::command]
async fn disconnect_sync(app: tauri::AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<AppState>();
        let _guard = state.sync_lock.lock().unwrap();
        state.access.lock().unwrap().take();
        google::forget_refresh();
        fs::write(&state.sync_disabled_path, "off")
            .map_err(|e| format!("Cannot disable sync: {e}"))?;
        state.sync_active.store(false, Ordering::Relaxed);
        Ok(())
    })
    .await
    .map_err(|e| format!("Cannot disconnect: {e}"))?
}

#[tauri::command]
async fn reset_sync(app: tauri::AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<AppState>();
        let _guard = state.sync_lock.lock().unwrap();
        fs::write(&state.sync_disabled_path, "off").map_err(|e| e.to_string())?;
        if state.sync_path.exists() {
            fs::remove_file(&state.sync_path)
                .map_err(|e| format!("Cannot reset sync history: {e}"))?;
        }
        state.access.lock().unwrap().take();
        google::forget_refresh();
        state.sync_active.store(false, Ordering::Relaxed);
        Ok(())
    })
    .await
    .map_err(|e| format!("Cannot reset sync: {e}"))?
}

#[tauri::command]
fn export_file(app: tauri::AppHandle, state: tauri::State<'_, AppState>) -> Result<bool, String> {
    if state.library_error.lock().unwrap().is_some() {
        return Err("The saved library could not be read; it remains on disk for recovery".into());
    }
    state.expander.dialog_open.store(true, Ordering::Relaxed);
    let chosen = choose_export(&app);
    state.expander.dialog_open.store(false, Ordering::Relaxed);
    let path = match chosen? {
        Some(path) => path,
        None => return Ok(false),
    };
    state.library.lock().unwrap().save(&path)?;
    Ok(true)
}

#[cfg(not(target_os = "linux"))]
fn choose_backup(_: &tauri::AppHandle) -> Result<Option<PathBuf>, String> {
    Ok(rfd::FileDialog::new()
        .add_filter("SnippetDeck backup", &["json", "txt"])
        .pick_file())
}

#[cfg(not(target_os = "linux"))]
fn choose_export(_: &tauri::AppHandle) -> Result<Option<PathBuf>, String> {
    Ok(rfd::FileDialog::new()
        .set_file_name("snippetdeck-backup.json")
        .add_filter("JSON backup", &["json"])
        .save_file())
}

#[cfg(target_os = "linux")]
fn choose_backup(app: &tauri::AppHandle) -> Result<Option<PathBuf>, String> {
    gtk_dialog(app, false)
}

#[cfg(target_os = "linux")]
fn choose_export(app: &tauri::AppHandle) -> Result<Option<PathBuf>, String> {
    gtk_dialog(app, true)
}

#[cfg(target_os = "linux")]
fn gtk_dialog(app: &tauri::AppHandle, saving: bool) -> Result<Option<PathBuf>, String> {
    use gtk::prelude::*;
    let (sender, receiver) = std::sync::mpsc::sync_channel(1);
    app.run_on_main_thread(move || {
        let dialog = gtk::FileChooserDialog::new(
            Some(if saving {
                "Export backup"
            } else {
                "Import backup"
            }),
            None::<&gtk::Window>,
            if saving {
                gtk::FileChooserAction::Save
            } else {
                gtk::FileChooserAction::Open
            },
        );
        dialog.add_buttons(&[
            ("Cancel", gtk::ResponseType::Cancel),
            (
                if saving { "Export" } else { "Import" },
                gtk::ResponseType::Accept,
            ),
        ]);
        if saving {
            dialog.set_current_name("snippetdeck-backup.json");
            dialog.set_do_overwrite_confirmation(true);
        }
        let filter = gtk::FileFilter::new();
        filter.set_name(Some("SnippetDeck backup"));
        filter.add_pattern("*.json");
        if !saving {
            filter.add_pattern("*.txt");
        }
        dialog.add_filter(filter);
        let path = if dialog.run() == gtk::ResponseType::Accept {
            dialog.filename()
        } else {
            None
        };
        dialog.close();
        let _ = sender.send(path);
    })
    .map_err(|e| format!("Cannot open file dialog: {e}"))?;
    receiver
        .recv()
        .map_err(|_| "File dialog closed unexpectedly".into())
}

fn open_editor(app: &tauri::AppHandle) {
    let state = app.state::<AppState>();
    state.expander.editor_focused.store(true, Ordering::Relaxed);
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.set_focus();
    } else {
        if let Err(error) = tauri::WebviewWindowBuilder::new(
            app,
            "main",
            tauri::WebviewUrl::App("index.html".into()),
        )
        .title("SnippetDeck")
        .inner_size(900.0, 720.0)
        .min_inner_size(390.0, 520.0)
        .build()
        {
            eprintln!("Cannot open editor: {error}");
            state
                .expander
                .editor_focused
                .store(false, Ordering::Relaxed);
        }
    }
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            open_editor(app)
        }))
        .plugin(tauri_plugin_autostart::init(
            tauri_plugin_autostart::MacosLauncher::LaunchAgent,
            Some(vec!["--background"]),
        ))
        .invoke_handler(tauri::generate_handler![
            snapshot,
            save_snippet,
            remove_snippet,
            set_active,
            set_start_at_login,
            choose_import,
            finish_import,
            export_file,
            sync_now,
            disconnect_sync,
            reset_sync,
            check_for_updates,
            install_update
        ])
        .setup(|app| {
            let path = app.path().app_data_dir()?.join("library.json");
            let active_path = path.with_file_name("enabled");
            let sync_path = path.with_file_name("sync.json");
            let sync_disabled_path = path.with_file_name("sync-disabled");
            let (library, library_error) = match Library::load(&path) {
                Ok(library) => (library, None),
                Err(error) => (Library::default(), Some(error)),
            };
            let expander = Arc::new(Expander::new(library.snippets.clone()));
            if library_error.is_some()
                || fs::read_to_string(&active_path).is_ok_and(|value| value == "off")
            {
                expander.enabled.store(false, Ordering::Relaxed);
            }
            app.manage(AppState {
                library: Mutex::new(library),
                library_error: Mutex::new(library_error),
                import: Mutex::new(None),
                path,
                active_path,
                sync_active: AtomicBool::new(sync_path.exists() && !sync_disabled_path.exists()),
                sync_path,
                sync_disabled_path,
                sync_lock: Mutex::new(()),
                access: Mutex::new(None),
                available_update: Mutex::new(None),
                update_lock: Mutex::new(()),
                install_lock: Mutex::new(()),
                expander: expander.clone(),
            });
            expander.start();
            let open = MenuItem::with_id(app, "open", "Open library", true, None::<&str>)?;
            let toggle = MenuItem::with_id(app, "toggle", "Pause / resume", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "Quit SnippetDeck", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&open, &toggle, &quit])?;
            TrayIconBuilder::new()
                .icon(tauri::image::Image::from_bytes(include_bytes!(
                    "../icons/icon.png"
                ))?)
                .menu(&menu)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "open" => open_editor(app),
                    "toggle" => {
                        let state = app.state::<AppState>();
                        let enabled = !state.expander.enabled.load(Ordering::Relaxed);
                        if state.library_error.lock().unwrap().is_none()
                            && state.persist_active(enabled).is_ok()
                        {
                            state.expander.enabled.store(enabled, Ordering::Relaxed);
                        }
                    }
                    "quit" => app.exit(0),
                    _ => {}
                })
                .build(app)?;
            let update_app = app.handle().clone();
            std::thread::spawn(move || {
                let state = update_app.state::<AppState>();
                let _guard = state.update_lock.lock().unwrap();
                if let Ok(Some(release)) =
                    update::check(&update_app.package_info().version.to_string())
                {
                    *state.available_update.lock().unwrap() = Some(release.clone());
                    let _ = update_app.emit("snippetdeck-update-available", release);
                }
            });
            if !std::env::args().any(|arg| arg == "--background") {
                open_editor(app.handle());
            }
            Ok(())
        })
        .on_window_event(|window, event| match event {
            tauri::WindowEvent::Focused(focused) => window
                .state::<AppState>()
                .expander
                .editor_focused
                .store(*focused, Ordering::Relaxed),
            tauri::WindowEvent::Destroyed => window
                .state::<AppState>()
                .expander
                .editor_focused
                .store(false, Ordering::Relaxed),
            _ => {}
        })
        .build(tauri::generate_context!())
        .expect("Cannot start SnippetDeck")
        .run(|_, event| {
            if let tauri::RunEvent::ExitRequested { code, api, .. } = event {
                if code.is_none() {
                    api.prevent_exit();
                }
            }
        });
}
