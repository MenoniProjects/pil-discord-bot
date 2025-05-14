# PIL Discord Bot

## Purpose
Menoni webservices management bot.
- read and manipulate data
- link & nick approvals
- various Menoni Hub server utilities

## Docker Setup

Ensure `maven`, `git`, and `docker` are installed.

Recommended to use WSL or Linux.  
Create a `docker-compose.yml` file or combine it with another projects:

```yaml
# Common properties for bots
x-common-env: &common-env
  MENONI_WS_HOST: http://localhost:8888
  DISCORD_FORCE-UPDATE-COMMANDS: false

# Common properties for bots requiring a database connection
x-common-db: &common-db
  SPRING_DATASOURCE_HOST: localhost
  SPRING_DATASOURCE_USERNAME: username
  SPRING_DATASOURCE_PASSWORD: password

services:

  # bot service, one per instance
  pil:
    image: ghcr.io/menoniprojects/pil-discord:dev
    restart: unless-stopped
    depends_on:
      - db
    environment:
      <<: [*common-env, *common-db] # import common & DB properties
      DISCORD_GUILD-ID: 1234567890 # guild/discord server id
      DISCORD_TOKEN: ASDF12345.67890.qwerty-zxcv # discord bot API token
      MENONI_WS_NAME: user # menoni-ws service account username
      MENONI_WS_TOKEN: token # menoni-ws service account token
      DISCORD_ADMIN-CHANNEL-ID: 1234567890 # The admin channel-id for sending alerts
      DISCORD_ADMIN-ROLE-ID: 1234567890 # The admin role-id for permission checks
      DISCORD_CMD-CHANNEL-ID: 1234567890 # The cmd channel-id for limiting many admin-only commands to
      DISCORD_STAFF-ROLE-ID: 1234567890 # The staff role-id for limiting permissions
      DISCORD_BOT-LOGS-CHANNEL-ID: 1234567890 # The channel-id for bot logs
      DISCORD_CASTER-ROLE-ID: 1234567890 # The caster role-id, auto added to any match channel
      DISCORD_BOT-HOIST-DIVIDER-ROLE-ID: 1234567890 # The role-id of the role below all the teams (used to put new teams above)
      DISCORD_TEAMS-DIVIDER-ROLE-ID: 1234567890 # The role-id of the role above all the teams (used to put teams under, sorted by name)
      DISCORD_PLAYER-ROLE-ID: 1234567890 # The player role-id for any registered player
      DISCORD_TEAM-LEAD-ROLE-ID: 1234567890 # The team-lead role-id for any team captain
      DISCORD_TEAMS-CHANNEL-ID: 1234567890 # The channel-id of the channel where all teams are listed (per division if applies)
      PIL_SIGNUPS_SHEET_KEY: aSdF0238h1023f0bn1023nf # The unique-id portion of the google spreadsheet URL for signups, to auto-import
      DISCORD_TEAM-MEMBER-DIV-ROLE-1: 1234567890 # The role-id of div-1 members
      DISCORD_TEAM-MEMBER-DIV-ROLE-2: 1234567890 # The role-id of div-2 members... (can define up to div-9)
      DISCORD_TEAM-CAPTAIN-DIV-ROLE-1: 1234567890 # The role-id of div-1 captains
      DISCORD_TEAM-CAPTAIN-DIV-ROLE-2: 1234567890 # The role-id of div-2 captains... (can define up to div-9)
      DISCORD_MATCHES-CATEGORY-ID-1: 1234567890 # the channel-category-id for div-1 matches
      DISCORD_MATCHES-CATEGORY-ID-2: 1234567890 # the channel-category-id for div-2 matches (can define up to div-9)

  # mysql database, can be re-used for multiple bots
  # change user and passwords if wanted
  db:
    image: mysql:8.4.2
    restart: unless-stopped
    environment:
      MYSQL_USER: 'admin'
      MYSQL_PASSWORD: 'admin'
      MYSQL_ROOT_PASSWORD: 'admin-root'
    ports:
      - '3306:3306'
    expose:
      - '3306'
    volumes:
      - my-db:/var/lib/mysql

# persistent volume (directory) for mysql database
volumes:
  my-db:
```

In terminal in the directory of the `yaml` file use the following commands to run the apps:

**Updating:**
```bash
# pull all latest versions of any defined service
docker-compose pull
# pull bot update specifically
docker-compose pull pil
```

**Starting:**
```bash
# start all services
docker-compose up -d
# start specific service
docker-compose up -d pil
```

**Checking logs:**
```bash
# Check and attach to logs, include last 250 lines, stay attached
docker-compose logs -f --tail 250 pil
# Detach with Ctrl+C [will only detach from log, does not exit the app]
```

**Stopping:**
```bash
# Close all services
docker-compose stop
# Close specific service
docker-compose stop pil
```

## Usage

### Docs

Staff manual (coming later)  
[Commands Tech Detail](https://docs.google.com/document/d/1fd4pl2B0EYz2-DiRT7aMxPn9LmaPdl2GDOrdMGwlwCw/edit?tab=t.0)

### Slash Commands
Slash commands are staff-only

`/importsignups csv=[file]` - Imports the signups from the jotform csv you can export. This will assign people the "player" role, create team roles, and save teams in a database

`/parsematchdump csv=[file]` - Creates a human-readable output of a match-dump csv created with the match-dumper plugin. Ensure the checkbox is checked to save points too.

### Chat Commands
Chat commands are just chat messages with an exclamation mark instead of a slash, you can put multiple commands in a message, each on their own line

#### endround
`!endround <round-number>` - archives channels, creates CSV with data and creates a round results message draft if it is a league match 
#### eventsexport  
`!eventsexport` - creates csv export for admin of trackmania.events
#### forcewin
`!forcewin <division> <round-number> <@win-team> <@lose-team> <score> [confirm]` - forcibly reports a result in #results
#### matchchannel
`!mc create <division> <round-number> <team-1-@> <team-2-@>` - Manually creates a match channel for a specific matchup in a specific round  
`!mc message <message-id-or-link>` - Sets the message content for the pinned message to appear in every match channel
#### refreshteams
`!refreshteams` - refreshes the message in #teams
#### team
`!team list` - list teams and details  
`!team div <division> <team> [more-teams...]` - Set one or more team divisions  
`!team div remove <team> [more-teams...]` - Unset one or more team divisions  
`!team emote <team> <emote>` - set a team's emote  
`!team emote <team> delete` - unset a team's emote  
`!team delete <team>` - delete a team
#### win
`!win <score>` - for team leads to report their team winning the match (to be used in their match channel) - score can be "2-1", "2-0", "ff"

## Run Configuration
**required environment variables & docs:**
```properties
N/A
```