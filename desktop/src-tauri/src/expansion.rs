use crate::library::{trigger_key, Snippet};
use chrono::{Datelike, Local};
#[cfg(not(target_os = "windows"))]
use enigo::{Direction, Enigo, Key as OutKey, Keyboard, Settings};
use rdev::{Event, EventType, Key};
use std::{
    collections::HashMap,
    path::PathBuf,
    sync::{
        atomic::{AtomicBool, AtomicU64, Ordering},
        mpsc::{self, Receiver, RecvTimeoutError, Sender},
        Arc, Mutex, RwLock,
    },
    thread,
    time::Duration,
};

#[cfg(target_os = "windows")]
#[path = "expansion/windows.rs"]
mod windows;

pub struct Expander {
    snippets: RwLock<Matcher>,
    pub enabled: AtomicBool,
    pub editor_focused: AtomicBool,
    pub dialog_open: AtomicBool,
    pub injecting: AtomicBool,
    pub status: RwLock<String>,
    input_revision: AtomicU64,
    insertions: Mutex<Option<Sender<Insertion>>>,
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
            input_revision: AtomicU64::new(0),
            insertions: Mutex::new(None),
        }
    }

    pub fn update(&self, snippets: Vec<Snippet>) {
        *self.snippets.write().unwrap() = Matcher::new(snippets);
    }

    pub fn start(self: &Arc<Self>) {
        let (sender, receiver) = mpsc::channel();
        *self.insertions.lock().unwrap() = Some(sender);
        let worker_state = Arc::clone(self);
        thread::spawn(move || insertion_worker(worker_state, receiver));
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
            #[cfg(target_os = "windows")]
            let result = windows::listen(move |event| input.accept(event, &listener_state));
            #[cfg(not(target_os = "windows"))]
            let result = rdev::listen(move |event| input.accept(event, &listener_state));
            if let Err(error) = result {
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
                triggers.insert(trigger_key(trigger), index);
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
    word_overflow: bool,
    modifier: u8,
    pending: Option<(String, String)>,
}

impl InputState {
    fn accept(&mut self, event: Event, state: &Arc<Expander>) {
        // Windows filters our marked events in its hook, before text translation.
        // Real typing remains visible while a clipboard restore is pending.
        #[cfg(not(target_os = "windows"))]
        if state.injecting.load(Ordering::Relaxed) {
            // Mouse clicks are physical: insertion never synthesizes them.
            if matches!(event.event_type, EventType::ButtonPress(_)) {
                self.word.clear();
                self.word_overflow = false;
                self.pending = None;
            }
            return;
        }
        if matches!(
            event.event_type,
            EventType::KeyPress(_) | EventType::ButtonPress(_)
        ) || matches!(event.event_type, EventType::KeyRelease(key) if modifier_bit(key) != 0 || matches!(key, Key::ShiftLeft | Key::ShiftRight))
        {
            state.input_revision.fetch_add(1, Ordering::SeqCst);
        }
        // Keep physical modifier state even while the editor or pause suppresses expansion.
        match event.event_type {
            EventType::KeyPress(key) => self.modifier |= modifier_bit(key),
            EventType::KeyRelease(key) => self.modifier &= !modifier_bit(key),
            _ => {}
        }
        if !state.enabled.load(Ordering::Relaxed)
            || state.editor_focused.load(Ordering::Relaxed)
            || state.dialog_open.load(Ordering::Relaxed)
        {
            self.word.clear();
            self.word_overflow = false;
            self.pending = None;
            return;
        }
        match event.event_type {
            EventType::ButtonPress(_) => {
                self.word.clear();
                self.word_overflow = false;
                self.pending = None;
            }
            EventType::KeyPress(key) => {
                if !matches!(key, Key::ShiftLeft | Key::ShiftRight) {
                    self.pending = None;
                }
                match key {
                    Key::ShiftLeft | Key::ShiftRight => {}
                    Key::Backspace if self.modifier == 0 => {
                        self.word.pop();
                    }
                    Key::Space if self.modifier == 0 => {
                        self.pending = self.match_word(state);
                        self.word.clear();
                        self.word_overflow = false;
                    }
                    _ if self.modifier != 0 => {
                        self.word.clear();
                        self.word_overflow = false;
                        self.pending = None;
                    }
                    _ => match event.name.as_deref() {
                        Some(text)
                            if !text.is_empty()
                                && text.chars().all(|c| !c.is_control() && !c.is_whitespace()) =>
                        {
                            if self.word_overflow {
                                return;
                            }
                            self.word.push_str(text);
                            if self.word.chars().count() > 40 {
                                self.word.clear();
                                self.word_overflow = true;
                            }
                        }
                        _ => {
                            self.word.clear();
                            self.word_overflow = false;
                            self.pending = None;
                        }
                    },
                }
            }
            EventType::KeyRelease(Key::Space) => {
                if let Some((trigger, text)) = self.pending.take() {
                    let expanded = placeholders(&text);
                    self.inject(state, trigger.chars().count() + 1, &expanded);
                }
            }
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
            .get(&trigger_key(&self.word))
            .map(|&index| (self.word.clone(), matcher.snippets[index].expansion.clone()))
    }

    fn inject(&self, state: &Arc<Expander>, remove: usize, text: &str) {
        let insertion = Insertion {
            remove,
            text: text.to_owned(),
            revision: state.input_revision.load(Ordering::SeqCst),
            #[cfg(target_os = "windows")]
            window: windows::foreground(),
        };
        if state
            .insertions
            .lock()
            .unwrap()
            .as_ref()
            .is_none_or(|sender| sender.send(insertion).is_err())
        {
            *state.status.write().unwrap() = "Input worker unavailable".into();
        }
    }
}

struct Insertion {
    remove: usize,
    text: String,
    revision: u64,
    #[cfg(target_os = "windows")]
    window: usize,
}

impl Insertion {
    fn current(&self, state: &Expander) -> bool {
        state.input_revision.load(Ordering::SeqCst) == self.revision
            && state.enabled.load(Ordering::Relaxed)
            && !state.editor_focused.load(Ordering::Relaxed)
            && !state.dialog_open.load(Ordering::Relaxed)
            && {
                #[cfg(target_os = "windows")]
                {
                    windows::ready(self.window)
                }
                #[cfg(not(target_os = "windows"))]
                {
                    true
                }
            }
    }
}

fn needs_paste(text: &str) -> bool {
    // Native Tab/Enter events can move focus or submit the target editor.
    text.contains(['\n', '\r', '\t'])
}

fn insertion_worker(state: Arc<Expander>, receiver: Receiver<Insertion>) {
    let mut clipboard: Option<arboard::Clipboard> = None;
    let mut saved: Option<ClipboardSnapshot> = None;
    let mut pasted = String::new();
    loop {
        let request = if saved.is_some() {
            match receiver.recv_timeout(Duration::from_millis(400)) {
                Ok(request) => Some(request),
                Err(RecvTimeoutError::Timeout) => None,
                Err(RecvTimeoutError::Disconnected) => break,
            }
        } else {
            match receiver.recv() {
                Ok(request) => Some(request),
                Err(_) => break,
            }
        };
        let Some(request) = request else {
            if let (Some(clipboard), Some(snapshot)) = (clipboard.as_mut(), saved.take()) {
                if clipboard.get_text().is_ok_and(|current| current == pasted) {
                    if let Err(message) = snapshot.restore(clipboard) {
                        *state.status.write().unwrap() = message;
                    }
                }
            }
            continue;
        };
        if !request.current(&state) {
            continue;
        }
        let result = (|| {
            let paste = needs_paste(&request.text);
            if paste {
                if clipboard.is_none() {
                    clipboard = Some(
                        arboard::Clipboard::new()
                            .map_err(|e| format!("Clipboard unavailable: {e}"))?,
                    );
                }
                let clipboard = clipboard.as_mut().unwrap();
                // Consecutive pastes share the original snapshot. A user's intervening
                // copy becomes the new baseline and must never be overwritten later.
                if saved.is_none() || !clipboard.get_text().is_ok_and(|current| current == pasted) {
                    saved = Some(ClipboardSnapshot::capture(clipboard)?);
                }
                clipboard
                    .set_text(&request.text)
                    .map_err(|e| format!("Cannot prepare paste: {e}"))?;
                pasted.clone_from(&request.text);
            }
            // Clipboard access may take time. Never erase a range after new typing,
            // a click, a shortcut or a foreground-window change made it stale.
            if !request.current(&state) {
                return Ok(());
            }
            #[cfg(target_os = "windows")]
            windows::replace(request.remove, &request.text, paste)?;
            #[cfg(not(target_os = "windows"))]
            {
                state.injecting.store(true, Ordering::Relaxed);
                let result = native_replace(request.remove, &request.text, paste);
                // rdev on these platforms has no per-event marker in its public API.
                thread::sleep(Duration::from_millis(120));
                state.injecting.store(false, Ordering::Relaxed);
                result?;
            }
            Ok::<(), String>(())
        })();
        if let Err(message) = result {
            eprintln!("{message}");
            *state.status.write().unwrap() = message;
        }
    }
    if let (Some(clipboard), Some(snapshot)) = (clipboard.as_mut(), saved.take()) {
        if clipboard.get_text().is_ok_and(|current| current == pasted) {
            let _ = snapshot.restore(clipboard);
        }
    }
}

#[cfg(not(target_os = "windows"))]
fn native_replace(remove: usize, text: &str, paste: bool) -> Result<(), String> {
    let mut keyboard = Enigo::new(&Settings {
        linux_delay: 0,
        ..Settings::default()
    })
    .map_err(|e| format!("Input unavailable: {e:?}"))?;
    erase_trigger(&mut keyboard, remove)?;
    if paste {
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
    } else {
        keyboard
            .text(text)
            .map_err(|e| format!("Text insertion failed: {e:?}"))?;
    }
    Ok(())
}

fn modifier_bit(key: Key) -> u8 {
    match key {
        Key::ControlLeft => 1,
        Key::ControlRight => 2,
        Key::Alt => 4,
        Key::AltGr => 8,
        Key::MetaLeft => 16,
        Key::MetaRight => 32,
        _ => 0,
    }
}

#[cfg(not(target_os = "windows"))]
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
    fn capture(clipboard: &mut arboard::Clipboard) -> Result<Self, String> {
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
            if chars.peek() == Some(&'\'') {
                chars.next();
                output.push('\'');
            } else {
                quoted = !quoted;
            }
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn custom_patterns_keep_literal_apostrophes_and_unknown_tokens() {
        assert_eq!(java_date_pattern("yyyy 'o''clock'").unwrap(), "%Y o'clock");
        assert!(java_date_pattern("unsupported").is_none());
        assert_eq!(placeholders("{{date:unsupported}}"), "{{date:unsupported}}");
    }

    fn event(kind: EventType, text: Option<&str>) -> Event {
        Event {
            time: std::time::SystemTime::now(),
            event_type: kind,
            name: text.map(str::to_owned),
        }
    }

    #[test]
    fn oversized_words_do_not_match_their_tail() {
        let state = Arc::new(Expander::new(Vec::new()));
        let mut input = InputState::default();
        for _ in 0..41 {
            input.accept(event(EventType::KeyPress(Key::KeyA), Some("a")), &state);
        }
        for c in ["!", "h", "e", "l", "p"] {
            input.accept(event(EventType::KeyPress(Key::KeyA), Some(c)), &state);
        }
        input.accept(event(EventType::KeyPress(Key::Space), Some(" ")), &state);
        assert!(input.pending.is_none());
        assert!(!input.word_overflow);
    }

    #[test]
    fn modifiers_are_tracked_while_editor_suppresses_expansion() {
        let state = Arc::new(Expander::new(Vec::new()));
        let mut input = InputState::default();
        input.accept(event(EventType::KeyPress(Key::ControlLeft), None), &state);
        input.accept(event(EventType::KeyPress(Key::ControlRight), None), &state);
        input.accept(event(EventType::KeyRelease(Key::ControlLeft), None), &state);
        assert_ne!(input.modifier, 0);
        state.editor_focused.store(true, Ordering::Relaxed);
        input.accept(
            event(EventType::KeyRelease(Key::ControlRight), None),
            &state,
        );
        state.editor_focused.store(false, Ordering::Relaxed);
        assert_eq!(input.modifier, 0);
        input.accept(event(EventType::KeyPress(Key::KeyA), Some("a")), &state);
        assert_eq!(input.word, "a");
    }

    #[test]
    fn typing_before_space_release_cancels_pending_expansion() {
        let state = Arc::new(Expander::new(Vec::new()));
        let mut input = InputState {
            word: "!help".into(),
            ..InputState::default()
        };
        input.accept(event(EventType::KeyPress(Key::Space), Some(" ")), &state);
        assert!(input.pending.is_some());
        input.accept(event(EventType::KeyPress(Key::KeyA), Some("a")), &state);
        assert!(input.pending.is_none());
    }

    #[test]
    fn clicking_invalidates_a_pending_trigger_in_the_old_field() {
        let state = Arc::new(Expander::new(Vec::new()));
        state.injecting.store(true, Ordering::Relaxed);
        let mut input = InputState {
            pending: Some(("!old".into(), "Expanded".into())),
            ..InputState::default()
        };
        input.accept(
            event(EventType::ButtonPress(rdev::Button::Left), None),
            &state,
        );
        assert!(input.pending.is_none());
    }

    #[test]
    fn aliases_delete_only_the_typed_range_and_backspace_never_schedules_undo() {
        let state = Arc::new(Expander::new(vec![Snippet {
            trigger: "!long-trigger".into(),
            aliases: vec!["уу".into()],
            expansion: "First\nSecond".into(),
            enabled: true,
            created_at: 0,
            updated_at: 0,
        }]));
        let (sender, receiver) = mpsc::channel();
        *state.insertions.lock().unwrap() = Some(sender);
        let mut input = InputState::default();
        for c in ["у", "у"] {
            input.accept(event(EventType::KeyPress(Key::KeyA), Some(c)), &state);
        }
        input.accept(event(EventType::KeyPress(Key::Space), Some(" ")), &state);
        input.accept(event(EventType::KeyRelease(Key::Space), None), &state);
        let request = receiver.try_recv().unwrap();
        assert_eq!(request.remove, 3);
        assert_eq!(request.text, "First\nSecond");
        input.accept(event(EventType::KeyPress(Key::Backspace), None), &state);
        input.accept(event(EventType::KeyRelease(Key::Backspace), None), &state);
        assert!(receiver.try_recv().is_err());
        assert_ne!(
            state.input_revision.load(Ordering::SeqCst),
            request.revision
        );
    }

    #[test]
    fn native_control_characters_are_pasted_without_focus_or_submit_keys() {
        for text in ["a\nb", "a\rb", "a\tb"] {
            assert!(needs_paste(text));
        }
        assert!(!needs_paste("А😀"));
    }

    #[cfg(target_os = "windows")]
    #[test]
    fn physical_typing_and_modifiers_are_visible_during_windows_insertion() {
        let state = Arc::new(Expander::new(Vec::new()));
        state.injecting.store(true, Ordering::Relaxed);
        let mut input = InputState::default();
        input.accept(event(EventType::KeyPress(Key::KeyA), Some("у")), &state);
        assert_eq!(input.word, "у");
        input.accept(event(EventType::KeyPress(Key::ControlLeft), None), &state);
        assert_ne!(input.modifier, 0);
        input.accept(event(EventType::KeyRelease(Key::ControlLeft), None), &state);
        assert_eq!(input.modifier, 0);
    }
}
