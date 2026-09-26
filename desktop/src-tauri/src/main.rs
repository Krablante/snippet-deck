#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod expansion;
mod library;

use expansion::Expander;
use library::{Library, Snippet};
use serde::Serialize;
use std::{
    fs,
    path::PathBuf,
    sync::{atomic::Ordering, Arc, Mutex},
};
use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    Manager,
};
use tauri_plugin_autostart::ManagerExt;

struct AppState {
    library: Mutex<Library>,
    import: Mutex<Option<Library>>,
    path: PathBuf,
    active_path: PathBuf,
    expander: Arc<Expander>,
}

#[derive(Serialize)]
struct Snapshot {
    snippets: Vec<Snippet>,
    active: bool,
    start_at_login: bool,
    status: String,
}

#[tauri::command]
fn snapshot(app: tauri::AppHandle, state: tauri::State<'_, AppState>) -> Snapshot {
    Snapshot {
        snippets: state.library.lock().unwrap().snippets.clone(),
        active: state.expander.enabled.load(Ordering::Relaxed),
        start_at_login: app.autolaunch().is_enabled().unwrap_or(false),
        status: state.expander.status.read().unwrap().clone(),
    }
}

fn save_change(
    state: &AppState,
    change: impl FnOnce(&mut Library) -> Result<(), String>,
) -> Result<(), String> {
    let mut current = state.library.lock().unwrap();
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
) -> Result<(), String> {
    save_change(&state, |library| library.put(previous.as_deref(), snippet))
}

#[tauri::command]
fn remove_snippet(state: tauri::State<'_, AppState>, trigger: String) -> Result<(), String> {
    save_change(&state, |library| library.remove(&trigger))
}

#[tauri::command]
fn set_active(state: tauri::State<'_, AppState>, active: bool) -> Result<(), String> {
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
    save_change(&state, |current| {
        *current = library;
        Ok(())
    })
}

#[tauri::command]
fn export_file(app: tauri::AppHandle, state: tauri::State<'_, AppState>) -> Result<bool, String> {
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
            export_file
        ])
        .setup(|app| {
            let path = app.path().app_data_dir()?.join("library.json");
            let active_path = path.with_file_name("enabled");
            let library = Library::load(&path).map_err(std::io::Error::other)?;
            let expander = Arc::new(Expander::new(library.snippets.clone()));
            if fs::read_to_string(&active_path).is_ok_and(|value| value == "off") {
                expander.enabled.store(false, Ordering::Relaxed);
            }
            app.manage(AppState {
                library: Mutex::new(library),
                import: Mutex::new(None),
                path,
                active_path,
                expander: expander.clone(),
            });
            expander.start();
            let open = MenuItem::with_id(app, "open", "Open library", true, None::<&str>)?;
            let toggle = MenuItem::with_id(app, "toggle", "Pause / resume", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "Quit SnippetDeck", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&open, &toggle, &quit])?;
            TrayIconBuilder::new()
                .icon(tauri::image::Image::from_bytes(include_bytes!(
                    "../../../design/app_icons/play-store/ic_launcher-playstore.png"
                ))?)
                .menu(&menu)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "open" => open_editor(app),
                    "toggle" => {
                        let state = app.state::<AppState>();
                        let enabled = !state.expander.enabled.load(Ordering::Relaxed);
                        if state.persist_active(enabled).is_ok() {
                            state.expander.enabled.store(enabled, Ordering::Relaxed);
                        }
                    }
                    "quit" => app.exit(0),
                    _ => {}
                })
                .build(app)?;
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
