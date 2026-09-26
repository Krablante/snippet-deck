use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use chrono::Utc;
use flate2::read::GzDecoder;
use serde::{Deserialize, Serialize};
use std::{collections::HashSet, fs, io::Read, path::Path};

const MAX_BYTES: usize = 2_000_000;
const MAX_SNIPPETS: usize = 10_000;
const FORMAT: &str = "snippetdeck-backup";

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Snippet {
    pub trigger: String,
    pub expansion: String,
    #[serde(default)]
    pub aliases: Vec<String>,
    #[serde(default = "enabled", alias = "isEnabled")]
    pub enabled: bool,
    #[serde(default)]
    pub created_at: i64,
    #[serde(default)]
    pub updated_at: i64,
}

fn enabled() -> bool {
    true
}
fn now() -> i64 {
    Utc::now().timestamp_millis()
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Backup<'a> {
    format: &'static str,
    schema_version: u8,
    exported_at: i64,
    snippets: &'a [Snippet],
}

#[derive(Clone, Default)]
pub struct Library {
    pub snippets: Vec<Snippet>,
}

impl Library {
    pub fn load(path: &Path) -> Result<Self, String> {
        if !path.exists() {
            return Ok(Self::default());
        }
        if fs::metadata(path).map_err(|e| e.to_string())?.len() > MAX_BYTES as u64 {
            return Err("Library exceeds the backup limit (2 MB)".into());
        }
        let bytes = fs::read(path).map_err(|e| format!("Cannot read library: {e}"))?;
        Self::decode(std::str::from_utf8(&bytes).map_err(|_| "Library is not UTF-8")?)
    }

    pub fn decode(input: &str) -> Result<Self, String> {
        if input.len() > MAX_BYTES * 2 {
            return Err("Backup is too large".into());
        }
        let json = if input.trim().starts_with("SNIPPETDECK_BACKUP_V1")
            || input.trim().starts_with("SNIPPETDECK_BACKUP_V2")
        {
            let (_, encoded) = input
                .trim()
                .split_once('\n')
                .ok_or("Backup payload is missing")?;
            let bytes = URL_SAFE_NO_PAD
                .decode(
                    encoded
                        .chars()
                        .filter(|c| !c.is_whitespace())
                        .collect::<String>(),
                )
                .map_err(|_| "Invalid backup text")?;
            let gzip = GzDecoder::new(&bytes[..]);
            let mut output = Vec::new();
            gzip.take((MAX_BYTES + 1) as u64)
                .read_to_end(&mut output)
                .map_err(|_| "Cannot decompress backup")?;
            String::from_utf8(output).map_err(|_| "Backup is not UTF-8")?
        } else {
            input.trim().to_owned()
        };
        if json.len() > MAX_BYTES {
            return Err("Backup is too large".into());
        }
        let root: serde_json::Value =
            serde_json::from_str(&json).map_err(|_| "Invalid JSON backup")?;
        let records = if root.is_array() {
            root
        } else {
            if root
                .get("format")
                .and_then(|v| v.as_str())
                .is_some_and(|v| v != FORMAT)
            {
                return Err("Unknown backup format".into());
            }
            if root
                .get("schemaVersion")
                .and_then(|v| v.as_u64())
                .is_some_and(|v| v > 2)
            {
                return Err("Backup needs a newer SnippetDeck".into());
            }
            root.get("snippets")
                .cloned()
                .ok_or("Backup has no snippets")?
        };
        let snippets: Vec<Snippet> =
            serde_json::from_value(records).map_err(|_| "Invalid snippets")?;
        let mut result = Self { snippets };
        result.validate()?;
        Ok(result)
    }

    pub fn encode(&self) -> Result<String, String> {
        let json = serde_json::to_string_pretty(&Backup {
            format: FORMAT,
            schema_version: 2,
            exported_at: now(),
            snippets: &self.snippets,
        })
        .map_err(|e| e.to_string())?;
        if json.len() > MAX_BYTES {
            return Err("Library exceeds the backup limit (2 MB)".into());
        }
        Ok(json)
    }

    pub fn save(&self, path: &Path) -> Result<(), String> {
        let data = self.encode()?;
        let parent = path.parent().ok_or("Invalid library path")?;
        fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        let tmp = path.with_extension("json.tmp");
        fs::write(&tmp, data).map_err(|e| e.to_string())?;
        fs::rename(&tmp, path).map_err(|e| format!("Cannot save library: {e}"))
    }

    pub fn put(&mut self, previous: Option<&str>, mut snippet: Snippet) -> Result<(), String> {
        snippet.trigger = normalize_primary(&snippet.trigger);
        snippet.aliases = snippet
            .aliases
            .iter()
            .map(|s| s.trim().to_owned())
            .filter(|s| !s.is_empty())
            .collect();
        let current = previous.and_then(|t| {
            self.snippets
                .iter()
                .position(|s| s.trigger.eq_ignore_ascii_case(t))
        });
        if previous.is_some() && current.is_none() {
            return Err("Snippet no longer exists".into());
        }
        let time = now();
        snippet.created_at = current.map(|i| self.snippets[i].created_at).unwrap_or(time);
        snippet.updated_at = time;
        let mut next = self.clone();
        if let Some(i) = current {
            next.snippets[i] = snippet;
        } else {
            next.snippets.push(snippet);
        }
        next.validate()?;
        *self = next;
        Ok(())
    }

    pub fn remove(&mut self, trigger: &str) -> Result<(), String> {
        let len = self.snippets.len();
        self.snippets
            .retain(|s| !s.trigger.eq_ignore_ascii_case(trigger));
        if self.snippets.len() == len {
            return Err("Snippet no longer exists".into());
        }
        Ok(())
    }

    fn validate(&mut self) -> Result<(), String> {
        if self.snippets.len() > MAX_SNIPPETS {
            return Err("Too many snippets".into());
        }
        let mut seen = HashSet::new();
        for snippet in &mut self.snippets {
            snippet.trigger = normalize_primary(&snippet.trigger);
            if snippet.expansion.trim().is_empty() {
                return Err("Expansion cannot be empty".into());
            }
            if snippet.created_at == 0 {
                snippet.created_at = now();
            }
            if snippet.updated_at == 0 {
                snippet.updated_at = now();
            }
            for trigger in std::iter::once(&snippet.trigger).chain(snippet.aliases.iter()) {
                if trigger.is_empty()
                    || trigger.chars().count() > 40
                    || trigger.chars().any(char::is_whitespace)
                {
                    return Err(format!("Invalid trigger: {trigger}"));
                }
                if trigger.eq_ignore_ascii_case("!help") {
                    return Err("!help is reserved".into());
                }
                if !seen.insert(trigger.to_lowercase()) {
                    return Err(format!("Duplicate trigger: {trigger}"));
                }
            }
        }
        self.encode()?;
        Ok(())
    }
}

fn normalize_primary(value: &str) -> String {
    let value = value.trim();
    if value.is_empty() || value.starts_with('!') {
        value.to_owned()
    } else {
        format!("!{value}")
    }
}
