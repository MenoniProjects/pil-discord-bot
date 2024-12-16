# Glacial Discord Bot

## Usage

### Staff manual

[Google doc staff manual](https://docs.google.com/document/d/e/2PACX-1vT_Reispf_RQ9biE-46WEy5O0S7BiQ70XHkVF2zfxE7u7PIE_L2eCpT3cIaF4trfOz6PVpz27Y3sYEH/pub)

### Slash Commands
Slash commands are staff-only

`/importsignups csv=[file]` - Imports the signups from the jotform csv you can export. This will assign people the "player" role, create team roles, and save teams in a database

`/parsematchdump csv=[file]` - Creates a human-readable output of a match-dump csv created with the match-dumper plugin. Ensure the checkbox is checked to save points too.

### Chat Commands
Chat commands are just chat messages with an exclamation mark instead of a slash, you can put multiple commands in a message, each on their own line

#### eventsexport  
`!eventsexport` - creates csv export for admin of trackmania.events
#### forcewin
`!forcewin <round-number> <@win-team> <@lose-team>` - forcibly reports a result in ⁠results
#### matchchannel
`!mc <round-number> <team-1-@> <team-2-@>` - Manually creates a match channel for a specific matchup in a specific round
#### refreshteams
`!refreshteams` - refreshes the message in ⁠teams
#### startround
`!startround <round-number>` - starts a new round, creating all required channels for the round
#### win
`!win` - for team leads to report their team winning the match (to be used in their match channel)

## Run Configuration
**required environment variables & docs:**
```properties
# Channel id where `/importsignups` and `/parsematchdump` are allowed
DISCORD_ADMIN-CHANNEL-ID=<ADMIN-CHANNEL-ID>
# Used to give admins access to match channels
DISCORD_ADMIN-ROLE-ID=1268574252005724170
# Used to give casters access to match channels
DISCORD_CASTER-ROLE-ID=1280609645349572639
# Channel in which staff-only !commands are allowed [eventsexport, forcewin, matchchannel, refreshteams, startround]
DISCORD_CMD-CHANNEL-ID=<ADMIN-CMD-CHANNEL-ID>
# The discord server id
DISCORD_GUILD-ID=1268572005720915988
# The channel category id for primary bracket matches
DISCORD_MATCHES-CATEGORY-ID=1279678232236527666
# The channel category id for secondary bracket matches
DISCORD_MATCHES-SECONDARY-CATEGORY-ID=1281317582749827084
# The colored discord member role id
DISCORD_MEMBER-ROLE-ID=1268674519426404434
# The player role id
DISCORD_PLAYER-ROLE-ID=1268674544214741065
# The channel id where match result messages should be posted
DISCORD_RESULTS-CHANNEL-ID=1268576853392625684
# Used to give staff access to match channels
DISCORD_STAFF-ROLE-ID=1268664947265765437
# The team captain role id
DISCORD_TEAM-LEAD-ROLE-ID=1280485367794896960
# The channel where all teams are listed and auto-updated after an import
DISCORD_TEAMS-CHANNEL-ID=1268573478714478614
# The discord bot token
DISCORD_TOKEN=<SECRET-TOKEN>
# The brackets google sheet key for the edit url
GLACIAL_SHEET_KEY=1DzaT8NwMXDdNpjtc9K7oyoaW1jEiGNtZfv3qkiz2jkQ
# The brackets google sheet tab ids for primary, secondary and play-ins brackets
GLACIAL_SHEET_TABS={BRACKET_PRIMARY:"1555569176",BRACKET_SECONDARY:"490909712",PLAY_INS:"1461136228"}
# MySQL database host
SPRING_DATASOURCE_HOST=<DATABASE-HOST>
# MySQL database password
SPRING_DATASOURCE_PASSWORD=<DATABASE-PASSWORD>
# MySQL database username
SPRING_DATASOURCE_USERNAME=<DATABASE-USER>
# The brackets google sheet key for the published url
GLACIAL_SHEET_PUBLIC-KEY=2PACX-1vRUj7z7QLvwSW0Na5uMvcs0nIvFMyYCZ64M7WG55iU1eKsDwJFuHedVu7oyYaecZbPCIkGBpUswIzQW
```