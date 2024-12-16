# Glacial Discord Bot

## Usage

### Staff manual

Google doc staff manual (coming later)

### Slash Commands
Slash commands are staff-only

`/importsignups csv=[file]` - Imports the signups from the jotform csv you can export. This will assign people the "player" role, create team roles, and save teams in a database

`/parsematchdump csv=[file]` - Creates a human-readable output of a match-dump csv created with the match-dumper plugin. Ensure the checkbox is checked to save points too.

### Chat Commands
Chat commands are just chat messages with an exclamation mark instead of a slash, you can put multiple commands in a message, each on their own line

#### eventsexport  
`!eventsexport` - creates csv export for admin of trackmania.events
#### forcewin
`!forcewin <division> <round-number> <@win-team> <@lose-team> <score> [confirm]` - forcibly reports a result in #results
#### matchchannel
`!mc create <division> <round-number> <team-1-@> <team-2-@>` - Manually creates a match channel for a specific matchup in a specific round
`!mc message <message-id-or-link>` - Sets the message content for the pinned message to appear in every match channel
#### refreshteams
`!refreshteams` - refreshes the message in #teams
#### win
`!win <score>` - for team leads to report their team winning the match (to be used in their match channel) - score can be "2-1", "2-0", "ff"

## Run Configuration
**required environment variables & docs:**
```properties
N/A
```