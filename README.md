# Scrutti++ 2.0

Bukkit plugin for Great Phermesia server features. Use `/info` in game for the player-facing menu.

## Stack

- Server API: Paper API `1.21.4-R0.1-SNAPSHOT`
- Java: `21`
- Build tool: Maven

## Included Systems

- Information menu through `/help` and `/info`
- Group chat
- Item blacklist
- Religion system
- Cosmetics
- Builder gadgets, hidden doors, no-update block placement, and parkour tools
- RTP + random warp (Essentials integration via reflection)
- Staff/social utilities, punishments, warp requests, and helper tools

## Implemented Commands

- `/gc`
- `/help`
- `/info`
- `/itemblock`
- `/religion`
- `/cosmetics`
- `/staffreligion`
- `/gadgets`
- `/brush`
- `/freeze`
- `/unfreeze`
- `/door`
- `/rtp`
- `/rtprandom`
- `/rtpwarp`
- `/sc`
- `/map` (`/servermap` alias)
- `/tpblock`
- `/tpblocklist`
- `/playtop`
- `/scrutv`
- `/clearchat`
- `/staffinfo`
- `/requestwarp`
- `/warprequests`
- `/punish <playername>`
- `/puns <playername>`

Command preprocess hooks are also implemented for Skript parity:

- `/ignore`, `/unignore` -> syncs teleport-block map
- `/tpa`, `/tp`, `/teleport` -> blocked target checks
- `/playtime` -> appends average-hours-per-day stats line

## Build

```bash
mvn clean package
```

If `mvn` is not recognized on Windows, install Maven first:

```powershell
winget install Apache.Maven
```

If Maven uses Java 17, set Java 21 before building:

```powershell
$env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.3\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
mvn clean package
```

Compiled jar output:

- `target/scrutti.jar`

## Run Locally

1. Build the plugin.
2. Copy jar into your Paper server `plugins` folder.
3. Restart server.

## Migration Notes

This plugin stores runtime data in `config.yml` under feature-specific sections:

- `groupchat-data`
- `itemblock-data`
- `religion-data`
- `religion-players`
- `social-data`
- `gadgets`

Old split data files are migrated on startup when found:

- `groupchat.yml`
- `itemblock.yml`
- `religion.yml`
- `social.yml`
- `gadgets.yml`
- `gadget-audit.yml`
