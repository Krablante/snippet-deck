use crate::library::Snippet;
use chrono::{Datelike, Local};
use enigo::{Direction, Enigo, Key as OutKey, Keyboard, Settings};
use rdev::{Event, EventType, Key};
use std::{
    collections::HashMap,
    path::PathBuf,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex, RwLock,
    },
    thread,
    time::Duration,
};

pub struct Expander {
    snippets: RwLock<Matcher>,
    pub enabled: AtomicBool,
    pub editor_focused: AtomicBool,
    pub dialog_open: AtomicBool,
    pub injecting: AtomicBool,
    pub status: RwLock<String>,
    clipboard: Mutex<Option<arboard::Clipboard>>,
}

impl Expander {
    pub fn new(snippets: Vec<Snippet>) -> Self {
        Self {
            snippets: RwLock::new(Matcher::new(snippets)),
            enabled: AtomicBool::new(true),
            editor_focused: AtomicBool::new(false),
            dialog_open: AtomicBool::new(false),
            injecting: AtomicBool::new(false),
            status: RwLock::new("Starting…".into()),
            clipboard: Mutex::new(None),
        }
    }

    pub fn update(&self, snippets: Vec<Snippet>) {
        *self.snippets.write().unwrap() = Matcher::new(snippets);
    }

    pub fn start(self: &Arc<Self>) {
        let state = Arc::clone(self);
        thread::spawn(move || {
            #[cfg(target_os = "macos")]
            if !mac_accessibility_allowed() {
                *state.status.write().unwrap() =
                    "Allow SnippetDeck in Privacy & Security → Accessibility, then reopen it"
                        .into();
                return;
            }
            #[cfg(target_os = "linux")]
            if std::env::var("XDG_SESSION_TYPE").is_ok_and(|s| s == "wayland") {
                *state.status.write().unwrap() =
                    "Wayland is not supported yet; the library remains available".into();
                return;
            }
            *state.status.write().unwrap() = "Ready · type a trigger, then Space".into();
            let mut input = InputState::default();
            let listener_state = Arc::clone(&state);
            if let Err(error) = rdev::listen(move |event| input.accept(event, &listener_state)) {
                eprintln!("Keyboard listener failed: {error:?}");
                *state.status.write().unwrap() = format!("Input monitoring unavailable: {error:?}");
            }
        });
    }
}

struct Matcher {
    snippets: Vec<Snippet>,
    triggers: HashMap<String, usize>,
}

impl Matcher {
    fn new(snippets: Vec<Snippet>) -> Self {
        let mut triggers = HashMap::new();
        for (index, snippet) in snippets.iter().enumerate().filter(|(_, s)| s.enabled) {
            for trigger in std::iter::once(&snippet.trigger).chain(snippet.aliases.iter()) {
                triggers.insert(trigger.to_lowercase(), index);
            }
        }
        Self { snippets, triggers }
    }
}

#[cfg(target_os = "macos")]
fn mac_accessibility_allowed() -> bool {
    #[link(name = "ApplicationServices", kind = "framework")]
    unsafe extern "C" {
        fn AXIsProcessTrusted() -> bool;
    }
    unsafe { AXIsProcessTrusted() }
}

#[derive(Default)]
struct InputState {
    word: String,
    modifier: u8,
    pending: Option<(String, String)>,
    pending_undo: Option<(String, String)>,
    undo: Option<(String, String)>,
}

impl InputState {
    fn accept(&mut self, event: Event, state: &Arc<Expander>) {
        if state.injecting.load(Ordering::Relaxed) {
            return;
        }
        if !state.enabled.load(Ordering::Relaxed)
            || state.editor_focused.load(Ordering::Relaxed)
            || state.dialog_open.load(Ordering::Relaxed)
        {
            self.word.clear();
            self.pending = None;
            self.pending_undo = None;
            self.undo = None;
            return;
        }
        match event.event_type {
            EventType::ButtonPress(_) => {
                self.word.clear();
                self.pending = None;
                self.pending_undo = None;
                self.undo = None;
            }
            EventType::KeyPress(key) => {
                match key {
                    Key::ControlLeft | Key::ControlRight => self.modifier |= 1,
                    Key::Alt | Key::AltGr => self.modifier |= 2,
                    Key::MetaLeft | Key::MetaRight => self.modifier |= 4,
                    Key::ShiftLeft | Key::ShiftRight => {}
                    Key::Backspace if self.modifier == 0 => {
                        if let Some((trigger, expanded)) = self.undo.take() {
                            // Wait for the user's Backspace to reach the target field.
                            self.pending_undo = Some((trigger, expanded));
                        } else {
                            self.word.pop();
                        }
                    }
                    Key::Space if self.modifier == 0 => {
                        self.pending = self.match_word(state);
                        self.word.clear();
                        self.undo = None;
                    }
                    _ if self.modifier != 0 => {
                        self.word.clear();
                        self.pending = None;
                        self.pending_undo = None;
                        self.undo = None;
                    }
                    _ => {
                        self.undo = None;
                        match event.name.as_deref() {
                            Some(text)
                                if !text.is_empty()
                                    && text
                                        .chars()
                                        .all(|c| !c.is_control() && !c.is_whitespace()) =>
                            {
                                self.word.push_str(text);
                                if self.word.chars().count() > 40 {
                                    self.word.clear();
                                }
                            }
                            _ => {
                                self.word.clear();
                                self.pending = None;
                            }
                        }
                    }
                }
            }
            EventType::KeyRelease(Key::Space) => {
                if let Some((trigger, text)) = self.pending.take() {
                    let expanded = placeholders(&text);
                    if self.inject(state, trigger.chars().count() + 1, &expanded) {
                        self.undo = Some((trigger, expanded));
                    }
                }
            }
            EventType::KeyRelease(Key::Backspace) => {
                if let Some((trigger, expanded)) = self.pending_undo.take() {
                    self.inject(state, expanded.chars().count().saturating_sub(1), &trigger);
                    self.word = trigger;
                }
            }
            EventType::KeyRelease(key) => match key {
                Key::ControlLeft | Key::ControlRight => self.modifier &= !1,
                Key::Alt | Key::AltGr => self.modifier &= !2,
                Key::MetaLeft | Key::MetaRight => self.modifier &= !4,
                _ => {}
            },
            _ => {}
        }
    }

    fn match_word(&self, state: &Expander) -> Option<(String, String)> {
        if self.word.is_empty() {
            return None;
        }
        let matcher = state.snippets.read().unwrap();
        if self.word.eq_ignore_ascii_case("!help") {
            let mut list: Vec<_> = matcher
                .snippets
                .iter()
                .filter(|s| s.enabled)
                .map(|s| {
                    let preview = s.expansion.lines().next().unwrap_or("").trim();
                    let preview = if preview.chars().count() > 72 {
                        format!("{}…", preview.chars().take(71).collect::<String>())
                    } else {
                        preview.to_owned()
                    };
                    format!(
                        "{}{} — {}",
                        s.trigger,
                        if s.aliases.is_empty() {
                            String::new()
                        } else {
                            format!(" ({})", s.aliases.join(", "))
                        },
                        preview
                    )
                })
                .collect();
            list.sort();
            let text = if list.is_empty() {
                "SnippetDeck\n\nNo enabled snippets yet. Add one in the app.".into()
            } else {
                format!("SnippetDeck snippets\n\n{}", list.join("\n"))
            };
            return Some((self.word.clone(), text));
        }
        matcher
            .triggers
            .get(&self.word.to_lowercase())
            .map(|&index| (self.word.clone(), matcher.snippets[index].expansion.clone()))
    }

    fn inject(&self, state: &Arc<Expander>, remove: usize, text: &str) -> bool {
        let multiline = text.contains('\n') || text.contains('\r');
        let saved_clipboard = if multiline {
            match ClipboardSnapshot::capture() {
                Ok(snapshot) => Some(snapshot),
                Err(error) => {
                    *state.status.write().unwrap() = error;
                    return false;
                }
            }
        } else {
            None
        };
        state.injecting.store(true, Ordering::Relaxed);
        let state = Arc::clone(state);
        let text = text.to_owned();
        thread::spawn(move || {
            let result = (|| {
                let mut keyboard = Enigo::new(&Settings {
                    linux_delay: 0,
                    ..Settings::default()
                })
                .map_err(|e| format!("Input unavailable: {e:?}"))?;
                if multiline {
                    let mut clipboard_guard = state.clipboard.lock().unwrap();
                    if clipboard_guard.is_none() {
                        *clipboard_guard = Some(
                            arboard::Clipboard::new()
                                .map_err(|e| format!("Clipboard unavailable: {e}"))?,
                        );
                    }
                    let clipboard = clipboard_guard.as_mut().unwrap();
                    clipboard
                        .set_text(&text)
                        .map_err(|e| format!("Cannot prepare paste: {e}"))?;
                    let paste_result = (|| {
                        erase_trigger(&mut keyboard, remove)?;
                        #[cfg(target_os = "macos")]
                        let modifier = OutKey::Meta;
                        #[cfg(not(target_os = "macos"))]
                        let modifier = OutKey::Control;
                        keyboard
                            .key(modifier, Direction::Press)
                            .map_err(|e| format!("Paste failed: {e:?}"))?;
                        let paste = keyboard.key(OutKey::Unicode('v'), Direction::Click);
                        let release = keyboard.key(modifier, Direction::Release);
                        paste.map_err(|e| format!("Paste failed: {e:?}"))?;
                        release.map_err(|e| format!("Paste failed: {e:?}"))?;
                        Ok::<(), String>(())
                    })();
                    if paste_result.is_ok() {
                        thread::sleep(Duration::from_millis(400));
                    }
                    if clipboard.get_text().is_ok_and(|current| current == text) {
                        if let Some(saved) = &saved_clipboard {
                            saved.restore(clipboard)?;
                        }
                    }
                    paste_result?;
                } else {
                    erase_trigger(&mut keyboard, remove)?;
                    keyboard
                        .text(&text)
                        .map_err(|e| format!("Text insertion failed: {e:?}"))?;
                }
                Ok::<(), String>(())
            })();
            // Let the listener discard the synthetic events before accepting typing again.
            thread::sleep(Duration::from_millis(120));
            state.injecting.store(false, Ordering::Relaxed);
            if let Err(message) = result {
                eprintln!("{message}");
                *state.status.write().unwrap() = message;
            }
        });
        true
    }
}

fn erase_trigger(keyboard: &mut Enigo, remove: usize) -> Result<(), String> {
    for _ in 0..remove {
        keyboard
            .key(OutKey::Backspace, Direction::Click)
            .map_err(|e| format!("Backspace failed: {e:?}"))?;
    }
    Ok(())
}

enum ClipboardSnapshot {
    Empty,
    Text(String),
    Html(String, Option<String>),
    Image(arboard::ImageData<'static>),
    Files(Vec<PathBuf>),
}

impl ClipboardSnapshot {
    fn capture() -> Result<Self, String> {
        let mut clipboard =
            arboard::Clipboard::new().map_err(|e| format!("Clipboard unavailable: {e}"))?;
        let files = clipboard.get().file_list().ok();
        let image = clipboard.get_image().ok();
        let html = clipboard.get().html().ok();
        let text = clipboard.get_text().ok();
        if let Some(files) = files {
            return Ok(Self::Files(files));
        }
        if image.is_some() && html.is_some() {
            return Err("Cannot preserve this clipboard format for multiline expansion".into());
        }
        if let Some(image) = image {
            return Ok(Self::Image(image));
        }
        if let Some(html) = html {
            return Ok(Self::Html(html, text));
        }
        if let Some(text) = text {
            return Ok(Self::Text(text));
        }
        Ok(Self::Empty)
    }

    fn restore(&self, clipboard: &mut arboard::Clipboard) -> Result<(), String> {
        let result = match self {
            Self::Empty => clipboard.clear(),
            Self::Text(text) => clipboard.set_text(text),
            Self::Html(html, text) => clipboard.set_html(html.as_str(), text.as_deref()),
            Self::Image(image) => clipboard.set_image(image.clone()),
            Self::Files(paths) => clipboard.set().file_list(paths),
        };
        result.map_err(|e| format!("Cannot restore clipboard: {e}"))
    }
}

fn placeholders(input: &str) -> String {
    let now = Local::now();
    let mut result = input.to_owned();
    for (key, value) in [
        ("{{date}}", now.format("%Y-%m-%d").to_string()),
        ("{{time}}", now.format("%H:%M:%S").to_string()),
        ("{{datetime}}", now.format("%Y-%m-%d %H:%M:%S").to_string()),
        ("{{day}}", now.format("%a").to_string()),
        ("{{day_long}}", now.format("%A").to_string()),
        ("{{month}}", now.format("%b").to_string()),
        ("{{month_long}}", now.format("%B").to_string()),
        ("{{year}}", now.format("%Y").to_string()),
        ("{{year_short}}", now.format("%y").to_string()),
        ("{{week_num}}", now.iso_week().week().to_string()),
    ] {
        result = result.replace(key, &value);
    }
    let mut output = String::with_capacity(result.len());
    let mut remaining = result.as_str();
    while let Some(start) = remaining.find("{{") {
        output.push_str(&remaining[..start]);
        let after = &remaining[start + 2..];
        let Some(end) = after.find("}}") else {
            output.push_str(&remaining[start..]);
            return output;
        };
        let content = &after[..end];
        let formatted = content
            .strip_prefix("date:")
            .or_else(|| content.strip_prefix("time:"))
            .and_then(|pattern| java_date_pattern(pattern.trim()))
            .map(|pattern| now.format(&pattern).to_string());
        if let Some(formatted) = formatted {
            output.push_str(&formatted);
        } else {
            output.push_str(&remaining[start..start + end + 4]);
        }
        remaining = &after[end + 2..];
    }
    output.push_str(remaining);
    output
}

// Cover the date/time patterns exposed by Android's editor. Unknown patterns stay untouched.
fn java_date_pattern(pattern: &str) -> Option<String> {
    if pattern.is_empty() {
        return None;
    }
    let mut output = String::new();
    let mut chars = pattern.chars().peekable();
    let mut quoted = false;
    while let Some(c) = chars.next() {
        if c == '\'' {
            quoted = !quoted;
            continue;
        }
        if quoted || !c.is_ascii_alphabetic() {
            if c == '%' {
                output.push('%');
            }
            output.push(c);
            continue;
        }
        let mut count = 1;
        while chars.peek() == Some(&c) {
            chars.next();
            count += 1;
        }
        let specifier = match (c, count) {
            ('y', 4) => "%Y",
            ('y', 2) => "%y",
            ('M', 4) => "%B",
            ('M', 3) => "%b",
            ('M', 2) => "%m",
            ('M', 1) => "%-m",
            ('d', 2) => "%d",
            ('d', 1) => "%-d",
            ('E', 4) => "%A",
            ('E', 3) => "%a",
            ('H', 2) => "%H",
            ('H', 1) => "%-H",
            ('h', 2) => "%I",
            ('h', 1) => "%-I",
            ('m', 2) => "%M",
            ('m', 1) => "%-M",
            ('s', 2) => "%S",
            ('s', 1) => "%-S",
            ('a', 1) => "%p",
            _ => return None,
        };
        output.push_str(specifier);
    }
    if quoted {
        None
    } else {
        Some(output)
    }
}
