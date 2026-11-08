# Clan Sync & Loot Tracker

A RuneLite plugin that sends **your own** PvM results to a clan website **you choose**:
drops, kill counts, kill times, personal bests and raid times. The clan Owner and Deputy Owners can also
sync the clan roster. Created by Arc1s, originally built for the PvM L1etuva clan; it works with any website
that implements the same API. Nothing in the plugin points at a particular website: everything is set in the
plugin settings.

**Nothing is recorded or sent until you enter a website address and your personal code, and turn an upload on.**
Both uploads are off by default.

## Setup

1. Ask your clan for the website's plugin API address and for your **personal code**.
2. In the plugin settings:
   - **Website API URL** - your clan website's plugin API, e.g. `https://example.com/api/plugin`. Only `https://` addresses are accepted.
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
  to the website, only after you confirm it (Cancel is the default button). Skip / Re-roll works like the
  Discord skip and uses one re-roll (it is refused when you have none left);
- a kill count message or a drop that counts for your task moves the shown progress at once (nothing extra is
  sent); after that kill or drop has been uploaded, the task is checked again early, at most once per 30 seconds,
  and the website's count then replaces the local one;
- a "Clan task complete!" popup appears when your kills or drops reach the target, when the website says so,
  or when you press Done.
  A finished task is never closed for you: you press Done, and only then you get the next task;
- **Show task overlay** (on by default) shows a small movable box on the game screen with the task, a progress
  bar ("12/50 (24%)") and "Target reached - press Done";
- **Wilderness tasks** (off by default) and **Slayer boss tasks** (on by default) are saved on the website
  (`<url>/pvm/prefs`) and used for your next tasks, also those you get on Discord. Your current task is not
  changed. The website's saved values are shown in the settings (for example after `/pvm_wildy` on Discord).

If the website does not offer clan tasks, the section stays hidden.

The plugin does **not** look up other players on the hiscores or any third-party service and does not send
your account settings, inventory, bank, location or chat.

Unsent data waits in the RuneLite plugin data folder and is retried every few minutes.

## License

BSD 2-Clause, see [LICENSE](LICENSE).
