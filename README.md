# Clan Sync & Loot Tracker

A RuneLite plugin that sends **your own** PvM results to a clan website **you choose**:
drops, kill counts, kill times, personal bests and raid times. The clan Owner and Deputy Owners can also
sync the clan roster. It works with any website that implements the small HTTP API described below
([API for clan websites](#api-for-clan-websites)). Nothing in the plugin points at a particular website: there is
no default address, everything is set in the plugin settings.

**Nothing is recorded or sent until you enter a website address and your personal code, and turn an upload on.**
Every upload and the clan tasks feature are off by default.

## Setup

1. Ask your clan for the website's plugin API address and for your **personal code**.
2. In the plugin settings:
   - **Website API URL** - your clan website's plugin API, e.g. `https://example.com/api/`. Only `https://` addresses are accepted.
   - **Personal code** - the code from step 1. It belongs to your own character.
   - **Clan name** (optional) - only record while you are in this clan.
   - **Upload drops and kill times** - turn on to send your PvM data.
   - **Upload clan roster** - only for the clan Owner and Deputy Owners.

## What is sent

Every request goes only to the Website API URL you entered, over HTTPS, with your personal code
in the `Auth-Member` header. Like any web request, the website also sees your IP address.

If the website refuses your code (wrong, revoked, or you are no longer in the clan), the plugin stops
recording and sending at once and shows "Paused" in its panel. Nothing more is sent with that code;
entering a new code resumes and delivers what was already waiting. If the code belongs to a different
character than the one you are logged in with, the plugin stops recording and sending for that character.

**Upload drops and kill times** (`<url>/loot`), only about your own character:
- your RuneScape name and the time each record was made;
- drops: the NPC / activity / chest name, its combat level, your kill count, the item ids, quantities and
  Grand Exchange prices; PvP loot is sent as "PvP" without the other player's name;
- kill times: the boss or activity, kill count, time, personal best, whether it was a new personal best,
  the raid team size (exact or the game's range such as "11-15 players"), the Tombs of Amascut raid level and
  whether the time was shown with precise timing;
- raid runs (Chambers of Xeric, Theatre of Blood, Tombs of Amascut, Fractured Archive): room / wave times,
  total and challenge times, points, team size, and the names of your raid party members as shown in the
  game's party list. Players merely seen on screen are never sent.

**Upload clan roster** (`<url>/clan-sync`), clan Owner and Deputy Owners only:
- clan name, every member's name, rank, rank title and join date, and what changed since the last upload;
- the collection log layout and item names from the game cache;
- whether the upload was automatic or started with the **Sync clan members** button.

The roster is sent automatically at most once a day; the website may skip it when another leader already
synced today. The **Sync clan members** button in the side panel always syncs right away. The roster part of
the side panel (sync status, last sync, member count, roster changes and the button) is shown only while the
game says you are the clan Owner or a Deputy Owner; everyone else sees only loot status and clan tasks.

**Show clan tasks** (`<url>/pvm/...`, off by default), only if your clan website offers clan tasks:
- about every 90 seconds while you are logged in, the plugin asks the website for your current clan task
  (sending only your personal code) and shows it in the side panel with its progress, difficulty and a wilderness flag;
- **Done**, **Skip / Re-roll** and **Take a new task** send that choice (the task id and a random request id)
  to the website, only after you confirm it (Cancel is the default button). Skip / Re-roll uses one re-roll (it is refused when you have none left);
- a kill count message or a drop that counts for your task moves the shown progress at once (nothing extra is
  sent); after that kill or drop has been uploaded, the task is checked again early, at most once per 30 seconds,
  and the website's count then replaces the local one;
- a "Clan task complete!" popup appears when your kills or drops reach the target, when the website says so,
  or when you press Done.
  A finished task is never closed for you: you press Done, and only then you get the next task;
- **Show task overlay** (on by default) shows a small movable box on the game screen with the task, a progress
  bar ("12/50 (24%)") and "Target reached - press Done";
- **Wilderness tasks** (off by default) and **Slayer boss tasks** (on by default) are saved on the website
  (`<url>/pvm/prefs`) and used for your next tasks, also those you get through other clan tools. Your current
  task is not changed. The website's saved values are shown in the settings (for example after you changed them
  elsewhere).

If the website does not offer clan tasks, the section stays hidden.

The plugin does **not** look up other players on the hiscores or any third-party service and does not send
your account settings, inventory, bank, location or chat.

Unsent data waits in the RuneLite plugin data folder and is retried every few minutes.

## API for clan websites

Any clan can run its own website for this plugin. The user enters one base URL (for example
`https://example.com/api/`); the plugin adds a path segment for each call. A real website that implements
this API is [pvm-l1etuva.lt](https://pvm-l1etuva.lt) (the PvM L1etuva clan, where the plugin started).

**Every request**
- HTTPS only (plain `http://` is accepted only for `localhost`, `127.0.0.1`, `*.test` and `*.localhost` while developing).
- Headers: `Auth-Member: <personal code>` (issued by the website to one member's character),
  `User-Agent: RuneLite-ClanSyncLootTracker/<version>`, `Content-Type: application/json; charset=utf-8` for POST.
- Answer with JSON `{"status": <int>, "message": "<text>", "data": {...}}`. `status` 200-299 means accepted.
  A `status` (or HTTP code) of 401/403 with a JSON body means the code is refused: the plugin pauses that code and
  sends nothing more with it. `data.reason` may be `invalid`, `revoked`, `inactive` or `rsn_mismatch`
  (the code belongs to another character). Other errors are retried later.

**`POST <base>/loot`** (Upload drops and kill times)
```json
{
  "schemaVersion": 2,
  "records":   [{"uid": "...", "player": "Name", "recordedAt": "2026-11-01T12:00:00Z", "type": "NPC",
                 "source": "Abyssal Sire", "combatLevel": 350, "killCount": 120,
                 "items": [{"id": 13265, "quantity": 1, "price": 1500000}]}],
  "killTimes": [{"uid": "...", "player": "Name", "recordedAt": "...", "source": "Vorkath", "killCount": 50,
                 "timeMs": 75000, "personalBestMs": 70000, "newPersonalBest": false,
                 "teamSize": 1, "teamSizeLabel": "Solo", "timePrecise": true, "raidLevel": null}],
  "raidRuns":  [{"uid": "...", "player": "Name", "recordedAt": "...", "raid": "Theatre of Blood", "killCount": 10,
                 "teamSize": 4, "team": ["Name", "Friend"], "totalTimeMs": 1500000, "rooms": [...]}],
  "itemNames": {"13265": "Abyssal dagger"},
  "itemTradeable": {"13265": true}
}
```
`uid` values are unique per record, so a website can safely ignore duplicates of a batch that is retried.
`type` is one of the `LootSourceType` values (`NPC`, `PLAYER` = PvP, sent with source "PvP" and no other player's name,
`EVENT`, `PICKPOCKET`, `CONTAINER`, ...). Records may also carry `amount`, `containerItemId` and `parentUid`
(items found inside an opened container).

**`POST <base>/clan-sync`** (Upload clan roster, clan Owner and Deputy Owners only)
```json
{
  "schemaVersion": 1, "syncType": "full", "trigger": "manual | auto",
  "clanName": "...", "collectedBy": "Name", "collectedAt": "...", "memberCount": 120,
  "members": [{"name": "Name", "rank": 100, "title": "Owner", "joinDate": "..."}],
  "changes": {"previousCollectedAt": "...", "joined": [], "left": [], "rankChanges": []},
  "itemNames": {...}, "collectionLogLayout": {...}
}
```
The website may answer `data.skipped = true` for an `auto` sync it does not need (for example already synced today).

**Clan tasks (optional)** - all under `<base>/pvm/`:
- `GET current` - must answer `data.feature = "clan_tasks"`; otherwise the plugin hides the clan tasks section.
  `data` also holds `joined`, `task` (`assignmentId`, `title`, `boss`, `count`, `difficulty`, `wilderness`,
  `points`, `progress {current, target}`, `reached`), `rerolls`, `points`, `completed`, `wildernessEnabled`,
  `slayerTasksEnabled`.
- `POST done`, `POST reroll`, `POST take` with `{"assignmentId": 123, "requestId": "<random id>"}`
  (no `assignmentId` for `take`); answer with the new state in `data.state` (and the finished task in `data.completed`).
- `POST prefs` with `{"wilderness": false, "slayerTasks": true}`.

The website decides what a task is and when it is complete; the plugin only shows it and sends the member's choice.

## Lietuviškai

Įskiepis siunčia **tik jūsų paties** dropus, KC, nužudymo ir reidų laikus į klano svetainę, kurią pats nurodote
nustatymuose (numatytos svetainės nėra). Klano Owner ir Deputy Owner gali sinchronizuoti narių sąrašą.
Kol neįvesite svetainės adreso, asmeninio kodo ir neįjungsite siuntimo, niekas nesiunčiama.
Kodą ir adresą gausite iš savo klano svetainės.

## License

BSD 2-Clause, see [LICENSE](LICENSE).
