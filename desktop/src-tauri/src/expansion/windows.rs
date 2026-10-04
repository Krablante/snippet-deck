//! Windows input stays native so shortcuts do not depend on the active language
//! and our injected events can be discarded without hiding real user input.
use ::windows::{
    core::PCWSTR,
    Win32::{
        Foundation::{LPARAM, LRESULT, WPARAM},
        System::LibraryLoader::GetModuleHandleW,
        UI::{Input::KeyboardAndMouse::*, WindowsAndMessaging::*},
    },
};
use rdev::{Button, Event, EventType, Key};
use std::{cell::RefCell, mem::size_of, time::SystemTime};

const MARKER: usize = 0x53444543;

#[cfg(test)]
static LISTENER_THREAD: std::sync::atomic::AtomicU32 = std::sync::atomic::AtomicU32::new(0);

struct Listener {
    callback: Box<dyn FnMut(Event)>,
    keys: [u8; 256],
    window: usize,
}

thread_local! {
    static LISTENER: RefCell<Option<Listener>> = const { RefCell::new(None) };
}

pub(super) fn foreground() -> usize {
    unsafe { GetForegroundWindow().0 as usize }
}

pub(super) fn ready(window: usize) -> bool {
    window != 0
        && foreground() == window
        && unsafe {
            [VK_SHIFT, VK_CONTROL, VK_MENU, VK_LWIN, VK_RWIN]
                .iter()
                .all(|key| GetAsyncKeyState(i32::from(key.0)) >= 0)
        }
}

fn virtual_key(key: VIRTUAL_KEY, up: bool, marker: usize) -> INPUT {
    INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: INPUT_0 {
            ki: KEYBDINPUT {
                wVk: key,
                dwFlags: if up {
                    KEYEVENTF_KEYUP
                } else {
                    KEYBD_EVENT_FLAGS(0)
                },
                dwExtraInfo: marker,
                ..Default::default()
            },
        },
    }
}

fn replacement_events(remove: usize, text: &str, paste: bool) -> Vec<INPUT> {
    let mut events = Vec::with_capacity(remove * 2 + if paste { 4 } else { text.len() * 2 });
    for _ in 0..remove {
        events.push(virtual_key(VK_BACK, false, MARKER));
        events.push(virtual_key(VK_BACK, true, MARKER));
    }
    if paste {
        events.push(virtual_key(VK_LCONTROL, false, MARKER));
        events.push(virtual_key(VK_V, false, MARKER));
        events.push(virtual_key(VK_V, true, MARKER));
        events.push(virtual_key(VK_LCONTROL, true, MARKER));
    } else {
        for unit in text.encode_utf16() {
            for flags in [KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP] {
                events.push(INPUT {
                    r#type: INPUT_KEYBOARD,
                    Anonymous: INPUT_0 {
                        ki: KEYBDINPUT {
                            wScan: unit,
                            dwFlags: flags,
                            dwExtraInfo: MARKER,
                            ..Default::default()
                        },
                    },
                });
            }
        }
    }
    events
}

pub(super) fn replace(remove: usize, text: &str, paste: bool) -> Result<(), String> {
    let events = replacement_events(remove, text, paste);
    // One SendInput call prevents physical typing from being interleaved between
    // deletion and insertion. Its result confirms dispatch, not editor contents.
    let sent = unsafe { SendInput(&events, size_of::<INPUT>() as i32) } as usize;
    if sent == events.len() {
        Ok(())
    } else {
        Err(format!(
            "Text insertion failed: Windows accepted {sent}/{} input events",
            events.len()
        ))
    }
}

fn key(code: u32) -> Key {
    match VIRTUAL_KEY(code as u16) {
        VK_BACK => Key::Backspace,
        VK_SPACE => Key::Space,
        VK_LSHIFT => Key::ShiftLeft,
        VK_RSHIFT => Key::ShiftRight,
        VK_LCONTROL => Key::ControlLeft,
        VK_RCONTROL => Key::ControlRight,
        VK_LMENU => Key::Alt,
        VK_RMENU => Key::AltGr,
        VK_LWIN => Key::MetaLeft,
        VK_RWIN => Key::MetaRight,
        _ => Key::Unknown(code),
    }
}

impl Listener {
    fn emit(&mut self, event_type: EventType, name: Option<String>) {
        let window = foreground();
        if window != self.window {
            self.window = window;
            // A foreground change invalidates a partially typed trigger.
            (self.callback)(Event {
                event_type: EventType::ButtonPress(Button::Unknown(0)),
                name: None,
                time: SystemTime::now(),
            });
        }
        (self.callback)(Event {
            event_type,
            name,
            time: SystemTime::now(),
        });
    }

    unsafe fn keyboard(&mut self, data: &KBDLLHOOKSTRUCT, down: bool) {
        let code = data.vkCode as usize;
        if code >= self.keys.len() {
            return;
        }
        if code == VK_CAPITAL.0 as usize && down && self.keys[code] & 0x80 == 0 {
            self.keys[code] ^= 1;
        }
        self.keys[code] = (self.keys[code] & 1) | if down { 0x80 } else { 0 };
        // The low-level hook runs before async state is updated. Track this event
        // ourselves; never attach the listener's input queue to the target app.
        for (combined, left, right) in [
            (VK_SHIFT, VK_LSHIFT, VK_RSHIFT),
            (VK_CONTROL, VK_LCONTROL, VK_RCONTROL),
            (VK_MENU, VK_LMENU, VK_RMENU),
        ] {
            self.keys[combined.0 as usize] =
                self.keys[left.0 as usize] | self.keys[right.0 as usize];
        }
        let kind = if down {
            EventType::KeyPress(key(data.vkCode))
        } else {
            EventType::KeyRelease(key(data.vkCode))
        };
        let name = if down {
            let thread = GetWindowThreadProcessId(GetForegroundWindow(), None);
            let layout = GetKeyboardLayout(thread);
            let mut buffer = [0_u16; 8];
            // Flag 4 leaves the system's dead-key buffer untouched (Windows 10+).
            let count = ToUnicodeEx(
                data.vkCode,
                data.scanCode,
                &self.keys,
                &mut buffer,
                4,
                Some(layout),
            );
            if count > 0 && count as usize <= buffer.len() {
                String::from_utf16(&buffer[..count as usize]).ok()
            } else {
                None
            }
        } else {
            None
        };
        self.emit(kind, name);
    }
}

unsafe extern "system" fn keyboard_hook(code: i32, message: WPARAM, data: LPARAM) -> LRESULT {
    if code == HC_ACTION as i32 {
        let data = &*(data.0 as *const KBDLLHOOKSTRUCT);
        if data.dwExtraInfo != MARKER {
            let down = matches!(message.0 as u32, WM_KEYDOWN | WM_SYSKEYDOWN);
            LISTENER.with(|listener| {
                if let Some(listener) = listener.borrow_mut().as_mut() {
                    listener.keyboard(data, down);
                }
            });
        }
    }
    CallNextHookEx(None, code, message, data)
}

unsafe extern "system" fn mouse_hook(code: i32, message: WPARAM, data: LPARAM) -> LRESULT {
    if code == HC_ACTION as i32 {
        let button = match message.0 as u32 {
            WM_LBUTTONDOWN => Some(Button::Left),
            WM_RBUTTONDOWN => Some(Button::Right),
            WM_MBUTTONDOWN => Some(Button::Middle),
            WM_XBUTTONDOWN => Some(Button::Unknown(0)),
            _ => None,
        };
        if let Some(button) = button {
            LISTENER.with(|listener| {
                if let Some(listener) = listener.borrow_mut().as_mut() {
                    listener.emit(EventType::ButtonPress(button), None);
                }
            });
        }
    }
    CallNextHookEx(None, code, message, data)
}

pub(super) fn listen(callback: impl FnMut(Event) + 'static) -> Result<(), String> {
    unsafe {
        let mut keys = [0; 256];
        let _ = GetKeyboardState(&mut keys);
        for key in [
            VK_LSHIFT,
            VK_RSHIFT,
            VK_LCONTROL,
            VK_RCONTROL,
            VK_LMENU,
            VK_RMENU,
            VK_LWIN,
            VK_RWIN,
        ] {
            keys[key.0 as usize] = if GetAsyncKeyState(i32::from(key.0)) < 0 {
                0x80
            } else {
                0
            };
        }
        LISTENER.with(|listener| {
            *listener.borrow_mut() = Some(Listener {
                callback: Box::new(callback),
                keys,
                window: foreground(),
            })
        });
        let module = GetModuleHandleW(PCWSTR::null()).map_err(|e| e.to_string())?;
        let keyboard =
            SetWindowsHookExW(WH_KEYBOARD_LL, Some(keyboard_hook), Some(module.into()), 0)
                .map_err(|e| e.to_string())?;
        let mouse = match SetWindowsHookExW(WH_MOUSE_LL, Some(mouse_hook), Some(module.into()), 0) {
            Ok(mouse) => mouse,
            Err(error) => {
                let _ = UnhookWindowsHookEx(keyboard);
                return Err(error.to_string());
            }
        };
        let mut message = MSG::default();
        #[cfg(test)]
        LISTENER_THREAD.store(
            ::windows::Win32::System::Threading::GetCurrentThreadId(),
            std::sync::atomic::Ordering::SeqCst,
        );
        let mut result = Ok(());
        loop {
            match GetMessageW(&mut message, None, 0, 0).0 {
                0 => break,
                -1 => {
                    result = Err("Windows input message loop failed".into());
                    break;
                }
                _ => {
                    let _ = TranslateMessage(&message);
                    DispatchMessageW(&message);
                }
            }
        }
        let _ = UnhookWindowsHookEx(mouse);
        let _ = UnhookWindowsHookEx(keyboard);
        LISTENER.with(|listener| listener.borrow_mut().take());
        result
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{expansion::Expander, library::Snippet};
    use ::windows::{core::w, Win32::Foundation::HWND};
    use serde_json::{json, Value};
    use std::{
        net::{TcpListener, TcpStream},
        path::PathBuf,
        process::{Child, Command},
        sync::{atomic::Ordering, Arc},
        thread,
        time::{Duration, Instant},
    };
    use tungstenite::{stream::MaybeTlsStream, Message, WebSocket};

    #[test]
    fn paste_is_one_marked_batch_with_virtual_v_and_no_enter() {
        let events = replacement_events(3, "First\nSecond", true);
        assert_eq!(events.len(), 10);
        let keys: Vec<_> = events
            .iter()
            .map(|event| unsafe { event.Anonymous.ki })
            .collect();
        assert!(keys
            .iter()
            .all(|event| event.dwExtraInfo == MARKER && event.wVk != VK_RETURN));
        assert_eq!(keys[6].wVk, VK_LCONTROL);
        assert_eq!(keys[7].wVk, VK_V);
        assert_eq!(keys[8].wVk, VK_V);
        assert_eq!(keys[9].wVk, VK_LCONTROL);
        assert_eq!(keys[7].dwFlags, KEYBD_EVENT_FLAGS(0));
        assert_eq!(keys[9].dwFlags, KEYEVENTF_KEYUP);
    }

    #[test]
    fn single_line_unicode_keeps_both_utf16_units_and_does_not_use_shortcuts() {
        let events = replacement_events(3, "А😀", false);
        assert_eq!(events.len(), 12);
        let units: Vec<_> = events[6..]
            .iter()
            .map(|event| unsafe { event.Anonymous.ki.wScan })
            .collect();
        assert_eq!(units, vec![0x0410, 0x0410, 0xD83D, 0xD83D, 0xDE00, 0xDE00]);
    }

    struct Browser {
        socket: WebSocket<MaybeTlsStream<TcpStream>>,
        child: Child,
        profile: PathBuf,
        sequence: u64,
    }

    impl Browser {
        fn open() -> Self {
            let port = TcpListener::bind("127.0.0.1:0")
                .unwrap()
                .local_addr()
                .unwrap()
                .port();
            let profile =
                std::env::temp_dir().join(format!("snippetdeck-browser-{}", uuid::Uuid::new_v4()));
            std::fs::create_dir(&profile).unwrap();
            let edge = [
                "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
                "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
            ]
            .into_iter()
            .find(|path| std::path::Path::new(path).exists())
            .expect("Microsoft Edge is required");
            let child = Command::new(edge)
                .args([
                    format!("--remote-debugging-port={port}"),
                    format!("--user-data-dir={}", profile.display()),
                    "--remote-allow-origins=*".into(),
                    "--no-first-run".into(),
                    "--no-default-browser-check".into(),
                    "--disable-features=msEdgeSidebarV2".into(),
                    "--new-window".into(),
                    "about:blank".into(),
                ])
                .spawn()
                .unwrap();
            let deadline = Instant::now() + Duration::from_secs(30);
            let socket = loop {
                if let Ok(mut response) =
                    ureq::get(format!("http://127.0.0.1:{port}/json/list")).call()
                {
                    if let Ok(pages) = response.body_mut().read_json::<Value>() {
                        if let Some(url) = pages
                            .as_array()
                            .unwrap()
                            .iter()
                            .find(|page| page["type"] == "page")
                            .and_then(|page| page["webSocketDebuggerUrl"].as_str())
                        {
                            break tungstenite::connect(url).unwrap().0;
                        }
                    }
                }
                assert!(
                    Instant::now() < deadline,
                    "Edge debugging endpoint did not start"
                );
                thread::sleep(Duration::from_millis(100));
            };
            let mut browser = Self {
                socket,
                child,
                profile,
                sequence: 0,
            };
            if let MaybeTlsStream::Plain(stream) = browser.socket.get_mut() {
                stream
                    .set_read_timeout(Some(Duration::from_secs(10)))
                    .unwrap();
            }
            // Text editors preserve whitespace. An unstyled HTML div normalizes
            // adjacent spaces even during ordinary typing, before expansion runs.
            browser.call("Runtime.evaluate", json!({"expression": "document.title='SnippetDeck input check'; document.body.innerHTML='<textarea id=t style=width:500px;height:200px></textarea><div id=e contenteditable=true style=width:500px;height:200px;border:1px solid;white-space:pre-wrap></div>'; window.submits=0; document.addEventListener('keydown',e=>{if(e.key===\"Enter\"&&!e.shiftKey){window.submits++;e.preventDefault()}})"}));
            browser.call("Page.bringToFront", json!({}));
            browser
        }

        fn call(&mut self, method: &str, params: Value) -> Value {
            self.sequence += 1;
            self.socket
                .send(Message::Text(
                    json!({"id": self.sequence, "method": method, "params": params})
                        .to_string()
                        .into(),
                ))
                .unwrap();
            loop {
                let message = self.socket.read().unwrap();
                if let Message::Text(text) = message {
                    let result: Value = serde_json::from_str(&text).unwrap();
                    if result["id"] == self.sequence {
                        assert!(result.get("error").is_none(), "CDP error: {result}");
                        return result["result"].clone();
                    }
                }
            }
        }

        fn evaluate(&mut self, expression: &str) -> Value {
            let result = self.call(
                "Runtime.evaluate",
                json!({"expression": expression, "returnByValue": true}),
            );
            assert!(
                result.get("exceptionDetails").is_none(),
                "JavaScript error: {result}"
            );
            result["result"]["value"].clone()
        }

        fn field(&mut self, id: &str, before: &str, after: &str) {
            self.evaluate(&format!("(()=>{{let e=document.getElementById({});let before={};let after={};if(e.tagName==='TEXTAREA'){{e.value=before+after;e.focus();e.setSelectionRange(before.length,before.length)}}else{{e.textContent=before+after;e.focus();let r=document.createRange();r.setStart(e.firstChild,before.length);r.collapse(true);let s=getSelection();s.removeAllRanges();s.addRange(r)}}}})()", json!(id), json!(before), json!(after)));
            self.call("Page.bringToFront", json!({}));
            // Programmatic DOM selection changes are invisible to a global hook.
            // Real navigation resets the trigger buffer and returns to this caret.
            physical(&[VK_LEFT, VK_RIGHT]);
        }

        fn text(&mut self, id: &str) -> String {
            self.evaluate(&format!("(()=>{{let e=document.getElementById({});return(e.value??e.innerText).replace(/\\r/g,'')}})()", json!(id))).as_str().unwrap().to_owned()
        }

        fn expect(&mut self, id: &str, expected: &str) {
            let deadline = Instant::now() + Duration::from_secs(5);
            loop {
                let actual = self.text(id);
                if actual == expected {
                    return;
                }
                assert!(
                    Instant::now() < deadline,
                    "{id}: expected {expected:?}, got {actual:?}"
                );
                thread::sleep(Duration::from_millis(10));
            }
        }
    }

    impl Drop for Browser {
        fn drop(&mut self) {
            let _ = self.socket.send(Message::Text(
                json!({"id": 99999, "method": "Browser.close"})
                    .to_string()
                    .into(),
            ));
            let _ = self.child.kill();
            let _ = self.child.wait();
            // Edge may need a moment to release its profile after Browser.close.
            for _ in 0..20 {
                if std::fs::remove_dir_all(&self.profile).is_ok() {
                    break;
                }
                thread::sleep(Duration::from_millis(50));
            }
        }
    }

    struct Agent(Arc<Expander>);
    impl Drop for Agent {
        fn drop(&mut self) {
            unsafe {
                let _ = PostThreadMessageW(
                    LISTENER_THREAD.load(Ordering::SeqCst),
                    WM_QUIT,
                    WPARAM(0),
                    LPARAM(0),
                );
            }
            self.0.insertions.lock().unwrap().take();
        }
    }

    fn physical(keys: &[VIRTUAL_KEY]) {
        let events: Vec<_> = keys
            .iter()
            .flat_map(|&key| [virtual_key(key, false, 0), virtual_key(key, true, 0)])
            .collect();
        assert_eq!(
            unsafe { SendInput(&events, size_of::<INPUT>() as i32) } as usize,
            events.len()
        );
    }

    fn layout(window: HWND, russian: bool) {
        unsafe {
            let layout = LoadKeyboardLayoutW(
                if russian {
                    w!("00000419")
                } else {
                    w!("00000409")
                },
                KLF_ACTIVATE,
            )
            .unwrap();
            PostMessageW(
                Some(window),
                WM_INPUTLANGCHANGEREQUEST,
                WPARAM(0),
                LPARAM(layout.0 as isize),
            )
            .unwrap();
            let thread = GetWindowThreadProcessId(window, None);
            let deadline = Instant::now() + Duration::from_secs(5);
            while GetKeyboardLayout(thread) != layout {
                assert!(
                    Instant::now() < deadline,
                    "Browser input language did not change"
                );
                thread::sleep(Duration::from_millis(20));
            }
        }
    }

    #[test]
    #[ignore = "requires an interactive Windows desktop and Microsoft Edge; exercised in desktop CI"]
    fn windows_browser_replacements_preserve_surrounding_text() {
        let mut browser = Browser::open();
        thread::sleep(Duration::from_millis(200));
        let window = unsafe { GetForegroundWindow() };
        let mut title = [0; 256];
        let len = unsafe { GetWindowTextW(window, &mut title) } as usize;
        assert!(
            String::from_utf16_lossy(&title[..len]).contains("SnippetDeck input check"),
            "Edge did not receive foreground focus"
        );
        let multiline = "Первая строка\nSecond 😀 line";
        let state = Arc::new(Expander::new(vec![
            Snippet {
                trigger: "!test".into(),
                aliases: vec!["уу".into(), "rv".into()],
                expansion: multiline.into(),
                enabled: true,
                created_at: 0,
                updated_at: 0,
            },
            Snippet {
                trigger: "!single".into(),
                aliases: vec!["ss".into()],
                expansion: "Текст 😀".into(),
                enabled: true,
                created_at: 0,
                updated_at: 0,
            },
        ]));
        state.start();
        let _agent = Agent(Arc::clone(&state));
        let deadline = Instant::now() + Duration::from_secs(5);
        while LISTENER_THREAD.load(Ordering::SeqCst) == 0 {
            assert!(Instant::now() < deadline, "Windows listener did not start");
            thread::sleep(Duration::from_millis(10));
        }
        let mut clipboard = arboard::Clipboard::new().unwrap();
        clipboard.set_text("Clipboard before expansion").unwrap();
        for id in ["t", "e"] {
            for russian in [true, false] {
                layout(window, russian);
                browser.field(id, "Before ", " AFTER");
                physical(if russian {
                    &[VK_E, VK_E, VK_SPACE]
                } else {
                    &[VK_R, VK_V, VK_SPACE]
                });
                browser.expect(id, &format!("Before {multiline} AFTER"));
                // Ordinary Backspace removes only the last inserted character.
                physical(&[VK_BACK]);
                browser.expect(
                    id,
                    &format!("Before {} AFTER", multiline.strip_suffix('e').unwrap()),
                );
            }
        }
        layout(window, false);
        browser.field("t", "Before ", " AFTER");
        physical(&[VK_S, VK_S, VK_SPACE]);
        browser.expect("t", "Before Текст 😀 AFTER");
        browser.field("t", "Before ", " AFTER");
        for count in 1..=3 {
            physical(&[VK_R, VK_V, VK_SPACE]);
            browser.expect("t", &format!("Before {} AFTER", multiline.repeat(count)));
        }
        physical(&[VK_X]);
        browser.expect("t", &format!("Before {}x AFTER", multiline.repeat(3)));
        thread::sleep(Duration::from_millis(550));
        assert_eq!(clipboard.get_text().unwrap(), "Clipboard before expansion");
        browser.field("t", "Before ", " AFTER");
        physical(&[VK_R, VK_V, VK_SPACE]);
        browser.expect("t", &format!("Before {multiline} AFTER"));
        clipboard
            .set_text("Copied by the user during restoration")
            .unwrap();
        thread::sleep(Duration::from_millis(550));
        assert_eq!(
            clipboard.get_text().unwrap(),
            "Copied by the user during restoration"
        );
        assert_eq!(browser.evaluate("window.submits"), 0);
        println!("Windows + Edge: Russian/English aliases, textarea/contenteditable, middle-of-field insertion, rapid repeats, ordinary Backspace, Unicode and clipboard ownership passed");
    }
}
