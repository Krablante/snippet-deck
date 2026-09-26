use crate::library::{Library, Snippet};
use serde::{Deserialize, Serialize};
use std::{
    collections::{BTreeMap, BTreeSet},
    fs,
    path::Path,
};

pub type Clock = BTreeMap<String, u64>;

#[derive(Clone, PartialEq, Serialize, Deserialize)]
pub struct Version {
    pub clock: Clock,
    pub value: Option<Snippet>, // None is a deletion, not a missing record.
}

#[derive(Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Replica {
    pub format: String,
    pub schema_version: u8,
    pub device_id: String,
    pub sequence: u64,
    pub entries: BTreeMap<String, Vec<Version>>,
}

#[derive(Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalState {
    pub account_id: String,
    pub replica: Replica,
    pub baseline: Vec<Snippet>,
    pub file_id: Option<String>,
    #[serde(default)]
    pub last_uploaded_hash: Option<String>,
}

pub struct Merge {
    pub replica: Replica,
    pub library: Option<Library>,
    pub conflicts: Vec<String>,
}

fn key(trigger: &str) -> String {
    trigger.to_lowercase()
}

fn same(a: &Option<Snippet>, b: &Option<Snippet>) -> bool {
    match (a, b) {
        (None, None) => true,
        (Some(a), Some(b)) => {
            a.trigger == b.trigger
                && a.expansion == b.expansion
                && a.aliases == b.aliases
                && a.enabled == b.enabled
        }
        _ => false,
    }
}

fn dominates(a: &Clock, b: &Clock) -> bool {
    a.keys()
        .chain(b.keys())
        .all(|id| a.get(id).unwrap_or(&0) >= b.get(id).unwrap_or(&0))
        && a.keys()
            .chain(b.keys())
            .any(|id| a.get(id).unwrap_or(&0) > b.get(id).unwrap_or(&0))
}

fn joined(versions: &[Version]) -> Clock {
    let mut clock = Clock::new();
    for version in versions {
        for (id, tick) in &version.clock {
            let value = clock.entry(id.clone()).or_insert(0);
            *value = (*value).max(*tick);
        }
    }
    clock
}

fn maximal(versions: Vec<Version>) -> Result<Vec<Version>, String> {
    let mut winners: Vec<Version> = Vec::new();
    for candidate in versions {
        if candidate.clock.is_empty() || candidate.clock.values().any(|v| *v == 0) {
            return Err("Invalid sync version".into());
        }
        if let Some(equal) = winners.iter().find(|v| v.clock == candidate.clock) {
            if !same(&equal.value, &candidate.value) {
                return Err("Inconsistent sync version".into());
            }
            continue;
        }
        if winners
            .iter()
            .any(|v| dominates(&v.clock, &candidate.clock))
        {
            continue;
        }
        winners.retain(|v| !dominates(&candidate.clock, &v.clock));
        winners.push(candidate);
    }
    let mut index = 0;
    while index < winners.len() {
        if let Some(other) =
            ((index + 1)..winners.len()).find(|i| same(&winners[index].value, &winners[*i].value))
        {
            let b = winners.remove(other);
            let a = &mut winners[index];
            a.clock = joined(&[a.clone(), b.clone()]);
            if b.value
                .as_ref()
                .is_some_and(|s| s.updated_at > a.value.as_ref().map_or(0, |s| s.updated_at))
            {
                a.value = b.value;
            }
            let merged_clock = a.clock.clone();
            winners.retain(|v| v.clock == merged_clock || !dominates(&merged_clock, &v.clock));
            index = 0;
        } else {
            index += 1;
        }
    }
    Ok(winners)
}

impl LocalState {
    pub fn fresh(account_id: String) -> Self {
        Self {
            account_id,
            replica: Replica {
                format: "snippetdeck-sync".into(),
                schema_version: 1,
                device_id: uuid::Uuid::new_v4().to_string(),
                sequence: 0,
                entries: BTreeMap::new(),
            },
            baseline: Vec::new(),
            file_id: None,
            last_uploaded_hash: None,
        }
    }

    pub fn load(path: &Path) -> Result<Option<Self>, String> {
        if !path.exists() {
            return Ok(None);
        }
        if fs::metadata(path).map_err(|e| e.to_string())?.len() > 12_000_000 {
            return Err("Local sync state is too large".into());
        }
        let state: Self = serde_json::from_slice(&fs::read(path).map_err(|e| e.to_string())?)
            .map_err(|_| "Local sync state is invalid")?;
        state.replica.validate()?;
        Ok(Some(state))
    }

    pub fn save(&self, path: &Path) -> Result<(), String> {
        let data = serde_json::to_vec(self).map_err(|e| e.to_string())?;
        if data.len() > 12_000_000 {
            return Err("Local sync state is too large".into());
        }
        let parent = path.parent().ok_or("Invalid sync path")?;
        fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        let tmp = path.with_extension("json.tmp");
        fs::write(&tmp, data).map_err(|e| e.to_string())?;
        fs::rename(tmp, path).map_err(|e| format!("Cannot save sync state: {e}"))
    }

    pub fn collect_changes(&mut self, library: &Library) -> Result<(), String> {
        let current: BTreeMap<String, Snippet> = library
            .snippets
            .iter()
            .map(|s| (key(&s.trigger), s.clone()))
            .collect();
        let baseline: BTreeMap<String, Snippet> = self
            .baseline
            .iter()
            .map(|s| (key(&s.trigger), s.clone()))
            .collect();
        for trigger in current
            .keys()
            .chain(baseline.keys())
            .collect::<BTreeSet<_>>()
        {
            let value = current.get(trigger).cloned();
            let before = baseline.get(trigger).cloned();
            if same(&value, &before) {
                continue;
            }
            let known = self
                .replica
                .entries
                .get(trigger)
                .cloned()
                .unwrap_or_default();
            if known.len() > 1 {
                return Err(format!("Resolve the conflict for {trigger} before syncing"));
            }
            self.replica.sequence += 1;
            let mut clock = joined(&known);
            clock.insert(self.replica.device_id.clone(), self.replica.sequence);
            self.replica
                .entries
                .insert((*trigger).clone(), vec![Version { clock, value }]);
        }
        self.baseline = library.snippets.clone();
        Ok(())
    }

    pub fn keep_local(&mut self, library: &Library) {
        let current: BTreeMap<String, Snippet> = library
            .snippets
            .iter()
            .map(|s| (key(&s.trigger), s.clone()))
            .collect();
        let keys: BTreeSet<String> = self
            .replica
            .entries
            .keys()
            .chain(current.keys())
            .cloned()
            .collect();
        for trigger in keys {
            let mut clock = joined(
                self.replica
                    .entries
                    .get(&trigger)
                    .map(Vec::as_slice)
                    .unwrap_or(&[]),
            );
            self.replica.sequence += 1;
            clock.insert(self.replica.device_id.clone(), self.replica.sequence);
            self.replica.entries.insert(
                trigger.clone(),
                vec![Version {
                    clock,
                    value: current.get(&trigger).cloned(),
                }],
            );
        }
        self.baseline = library.snippets.clone();
    }
}

impl Replica {
    pub fn validate(&self) -> Result<(), String> {
        if self.format != "snippetdeck-sync"
            || self.schema_version != 1
            || uuid::Uuid::parse_str(&self.device_id).is_err()
            || self.entries.len() > 20_000
        {
            return Err("Invalid cloud library".into());
        }
        for (trigger, versions) in &self.entries {
            if key(trigger) != *trigger
                || versions.is_empty()
                || versions.len() > 50
                || versions.iter().any(|v| {
                    v.clock.is_empty()
                        || v.clock.len() > 50
                        || v.clock.values().any(|tick| *tick == 0)
                        || v.value
                            .as_ref()
                            .is_some_and(|s| key(&s.trigger) != *trigger)
                })
            {
                return Err("Invalid cloud record".into());
            }
        }
        Ok(())
    }

    pub fn merge(mut self, others: &[Replica], validate: bool) -> Result<Merge, String> {
        for replica in others {
            replica.validate()?;
            for (trigger, versions) in &replica.entries {
                self.entries
                    .entry(trigger.clone())
                    .or_default()
                    .extend(versions.clone());
            }
        }
        let mut conflicts = Vec::new();
        let mut snippets = Vec::new();
        for (trigger, versions) in &mut self.entries {
            *versions = maximal(std::mem::take(versions))?;
            if versions.len() != 1 {
                conflicts.push(trigger.clone());
            } else if let Some(snippet) = &versions[0].value {
                snippets.push(snippet.clone());
            }
        }
        let library = if conflicts.is_empty() {
            let library = Library { snippets };
            if validate {
                Library::decode(&library.encode()?)?;
            }
            Some(library)
        } else {
            None
        };
        Ok(Merge {
            replica: self,
            library,
            conflicts,
        })
    }
}

pub fn same_library(a: &Library, b: &Library) -> bool {
    let first: BTreeMap<String, &Snippet> =
        a.snippets.iter().map(|s| (key(&s.trigger), s)).collect();
    let second: BTreeMap<String, &Snippet> =
        b.snippets.iter().map(|s| (key(&s.trigger), s)).collect();
    first == second
}

#[cfg(test)]
mod tests {
    use super::*;

    fn snippet(trigger: &str, expansion: &str) -> Snippet {
        Snippet {
            trigger: trigger.into(),
            expansion: expansion.into(),
            aliases: Vec::new(),
            enabled: true,
            created_at: 1,
            updated_at: 1,
        }
    }

    #[test]
    fn concurrent_edit_and_delete_keep_both_versions() {
        let original = snippet("!shared", "Original");
        let mut a = LocalState::fresh("account".into());
        a.collect_changes(&Library {
            snippets: vec![original.clone()],
        })
        .unwrap();
        let mut b = LocalState::fresh("account".into());
        let received = b.replica.clone().merge(&[a.replica.clone()], true).unwrap();
        b.replica = received.replica;
        b.baseline = vec![original.clone()];
        a.collect_changes(&Library {
            snippets: vec![snippet("!shared", "Changed")],
        })
        .unwrap();
        b.collect_changes(&Library::default()).unwrap();
        let merged = a.replica.merge(&[b.replica], true).unwrap();
        assert_eq!(merged.conflicts, vec!["!shared"]);
        assert!(merged.library.is_none());
        assert_eq!(merged.replica.entries["!shared"].len(), 2);
    }

    #[test]
    fn old_replica_cannot_restore_observed_deletion() {
        let mut state = LocalState::fresh("account".into());
        state
            .collect_changes(&Library {
                snippets: vec![snippet("!shared", "Original")],
            })
            .unwrap();
        let offline = state.replica.clone();
        state.collect_changes(&Library::default()).unwrap();
        let merged = offline.merge(&[state.replica], true).unwrap();
        assert!(merged.conflicts.is_empty());
        assert!(merged.library.unwrap().snippets.is_empty());
        assert!(merged.replica.entries["!shared"][0].value.is_none());
    }

    #[test]
    fn independent_additions_merge_without_conflict() {
        let mut phone = LocalState::fresh("account".into());
        phone
            .collect_changes(&Library {
                snippets: vec![snippet("!phone", "From phone")],
            })
            .unwrap();
        let mut laptop = LocalState::fresh("account".into());
        laptop
            .collect_changes(&Library {
                snippets: vec![snippet("!laptop", "From laptop")],
            })
            .unwrap();

        let merged = phone.replica.merge(&[laptop.replica], true).unwrap();
        assert!(merged.conflicts.is_empty());
        let names: BTreeSet<_> = merged
            .library
            .unwrap()
            .snippets
            .into_iter()
            .map(|s| s.trigger)
            .collect();
        assert_eq!(
            names,
            BTreeSet::from(["!phone".to_owned(), "!laptop".to_owned()])
        );
    }

    #[test]
    fn a_full_library_merges_with_linear_record_storage() {
        let library = Library {
            snippets: (0..10_000)
                .map(|n| snippet(&format!("!item{n}"), "Value"))
                .collect(),
        };
        let mut state = LocalState::fresh("account".into());
        state.collect_changes(&library).unwrap();

        let merged = state.replica.merge(&[], true).unwrap();

        assert_eq!(merged.library.unwrap().snippets.len(), 10_000);
        assert!(serde_json::to_vec(&merged.replica).unwrap().len() < 6_000_000);
    }

    #[test]
    fn reads_android_deletion_without_an_explicit_null_field() {
        let id = uuid::Uuid::new_v4().to_string();
        let json = format!(
            r#"{{"format":"snippetdeck-sync","schemaVersion":1,"deviceId":"{id}","sequence":1,"entries":{{"!gone":[{{"clock":{{"{id}":1}}}}]}}}}"#
        );
        let replica: Replica = serde_json::from_str(&json).unwrap();
        replica.validate().unwrap();
        assert!(replica.entries["!gone"][0].value.is_none());
    }

    #[test]
    fn conflicting_aliases_fail_before_replacing_the_local_library() {
        let mut first = LocalState::fresh("account".into());
        let mut a = snippet("!one", "One");
        a.aliases = vec!["shared".into()];
        first
            .collect_changes(&Library { snippets: vec![a] })
            .unwrap();
        let mut second = LocalState::fresh("account".into());
        let mut b = snippet("!two", "Two");
        b.aliases = vec!["shared".into()];
        second
            .collect_changes(&Library { snippets: vec![b] })
            .unwrap();

        assert!(first.replica.merge(&[second.replica], true).is_err());
    }
}
