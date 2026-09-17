# ship-it — ukentlig event-oppsummering til Discord

Henter events og poster en oppsummering i en Discord-kanal én gang i uka.

## Kom i gang

```bash
mvn -q package          # bygger target/eventbot-0.1.0.jar
mvn -q exec:java -Dexec.args="--dry-run"   # se oppsummeringen i terminalen
```

`--dry-run` trenger ingen Discord-oppsett, så det er raskeste vei til å se om
formatet er som du vil ha det.

## Maven-oppsett

Har maskinen din en `~/.m2/settings.xml` som speiler alt til et internt
firma-repository, ville den fanget opp dette bygget også. Prosjektet bruker
derfor sin egen `settings.xml` rett mot Maven Central, aktivert automatisk av
`.mvn/maven.config`.

IntelliJ plukker opp `.mvn/maven.config` automatisk, så importen skal virke
uten videre oppsett. Havner den likevel på en intern mirror: **Settings → Build
Tools → Maven → User settings file** → pek på `settings.xml` i prosjektrota.

## Discord-oppsett

1. Lag en app på https://discord.com/developers/applications → **Bot** → kopier token.
2. **OAuth2 → URL Generator**: scope `bot`, permissions `Send Messages` +
   `Embed Links`. Åpne URL-en og inviter boten til serveren.
3. Lag en `.env` i prosjektrota (git-ignorert):

```dotenv
# Bot-token fra Developer Portal -> Bot -> Reset Token
DISCORD_TOKEN=

# Høyreklikk kanalen -> Copy Channel ID
# (krever Developer Mode: Innstillinger -> Advanced -> Developer Mode)
DISCORD_CHANNEL_ID=

# Valgfritt. Standard: mandag 09:00, norsk tid.
POST_DAY=MONDAY
POST_TIME=09:00
```

```bash
mvn -q exec:java -Dexec.args="--now"   # post én gang, nå
mvn -q exec:java                       # kjør videre, post mandag 09:00
```

## Datakilder

| Kilde | Endepunkt | Nøkkel |
|---|---|---|
| Oslo Omvendt | `GET /api/events?year=<N>&week=<N>` | ingen |

Legg til en ny kilde:

1. Implementer `EventSource` i `sources/` — map til `model.Event`.
2. Registrer den i `sources`-lista øverst i `Main.kt`.

Oppsummeringen er kildeuavhengig, så den trenger ingen endring. En kilde som
feiler logges og hoppes over; resten postes som vanlig.

Oslo Omvendt tilbyr også en MCP-server på `https://www.osloomvendt.no/api/mcp`
hvis du heller vil spørre dataene med en AI-assistent.
