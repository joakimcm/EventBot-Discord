# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Bygging og kjøring

```bash
mvn package                              # fat jar i target/eventbot-0.1.0.jar
mvn exec:java -Dexec.args="--dry-run"    # oppsummering til terminalen, ingen Discord
mvn exec:java -Dexec.args="--now"        # post én gang til Discord, avslutt
mvn exec:java                            # kjør videre, post på fast ukedag
```

`--dry-run` krever verken token eller nettverk mot Discord, og er raskeste vei
til å se effekten av endringer i formateringen.

Det finnes ingen tester i prosjektet ennå. `OsloOmvendtSource.parse(body)` er
skilt fra `fetch(week)` nettopp for å kunne testes mot lagret JSON uten HTTP —
bruk den sømmen hvis du legger til tester.

## Maven går rett mot Maven Central

Utviklingsmaskinen kan ha en `~/.m2/settings.xml` som speiler **alt** til et
internt firma-repository (`<mirrorOf>*</mirrorOf>`). Dette er et privat
prosjekt og skal ikke være avhengig av slik infrastruktur — den krever som
regel VPN, og hører uansett ikke hjemme her.

Prosjektet har derfor sin egen `settings.xml` mot `repo1.maven.org`, aktivert av
`.mvn/maven.config` (`-s settings.xml`). Ikke fjern den fila, og ikke løs
problemet ved å legge interne repoer i `pom.xml` — en `<mirrorOf>*</mirrorOf>`
overstyrer likevel `<repositories>` deklarert i POM-en.

IntelliJ leser `.mvn/maven.config` selv og trenger normalt ingen konfigurasjon.
Skulle importen likevel havne på en intern mirror, overstyr *Settings → Build
Tools → Maven → User settings file* til prosjektets `settings.xml`.

## Arkitektur

Poenget med strukturen er at **flere datakilder** skal kunne legges til uten at
formateringen endres:

```
sources/EventSource     interface: fetch(Week) -> List<Event>
  └─ OsloOmvendtSource  første implementasjon
model/Event             kildeuavhengig domenemodell
Digest.kt               formatering, vet ingenting om kilder
Main.kt                 registrerer kilder, scheduler, feilhåndtering
```

Legg til en kilde ved å implementere `EventSource`, mappe til `model.Event`, og
registrere den i `sources`-lista øverst i `Main.kt`. `Digest.kt` skal ikke
trenge endring. En kilde som kaster blir logget og hoppet over i `buildDigest()`
— resten postes som vanlig, slik at én død kilde ikke stopper ukas melding.

## Ting som lett blir feil

**Bruk MCP-endepunktet, ikke REST-et.** Oslo Omvendt har to åpne endepunkter
uten nøkkel, og de er uenige om klokkeslett:

- `POST /api/mcp` (`search_events`) — samsvarer med deres egne eventsider. Dette
  er kilden vi bruker.
- `GET /api/events?year=&week=` — forskyver events som er lagret med eksplisitt
  tidssone-offset 2 timer bakover. Verifisert 17.09.2026 mot arrangørenes sider:
  6 av 27 events i uke 38 var feil. Kalles nå kun for `highlight`-flagget, som
  MCP-svaret ikke har.

**`highlight` finnes ikke lenger i API-et.** Per 14.10.2026 har `/api/events`
samme felter som MCP-svaret, uten `highlight`. Flagget finnes fortsatt på
forsiden deres (Next.js-payload), men ikke i noe API. `highlightedTitles()` får
derfor tomt sett uten å kaste — `asBoolean(false)` gjør et manglende felt til
`false` — og ✨ uteblir stille. Koden står urørt i tilfelle feltet kommer
tilbake.

**`startDate` har to formater i samme felt.** `2026-09-18T21:00:00+02:00` er et
ekte offset og leses som det står. `2026-09-15T21:00:00.000Z` har en dekorativ
`Z` — tallet er allerede lokal Oslo-tid. `parseTimestamp()` i
`OsloOmvendtSource` håndterer begge; ikke erstatt den med `Instant.parse`.

Oslo Omvendts data er heller ikke alltid riktig mot arrangøren (Ultimas «Lost
Signals» står som 21:00 hos dem, 19:00 hos Ultima; Annette K & Frantzvaag på Blå
står som 19:00–23:00, men starter 23:00). Vi speiler guiden deres, og det er så
nøyaktig vi kan bli uten en egen kilde per arrangør.

**Derfor vises ikke klokkeslett.** `SHOW_TIME` i `Digest.kt` er `false` som
standard — et tidspunkt som er feil er verre enn ingen. All formatering står
urørt i `line()`; blir dataene bedre, settes miljøvariabelen `SHOW_TIME=true`
og ingenting annet trengs. Ikke slett koden.

**Uka postes som én melding med embeds, ikke som ren tekst.** En vanlig melding
tar maks 2000 tegn, og en travel uke er over 4000. Embeds gir 4096 tegn per
beskrivelse og 6000 til sammen per melding, og Discord lager ikke
link-previews fra URL-er i embeds. `digest()` returnerer en `WeeklyDigest`
(tittel + `chunks`), og `DiscordPublisher` gjør hver chunk til én embed på samme
melding, med tittel bare på den første.

`chunk()` fyller så få embeds som mulig og deler aldri en dag. Sprenger uka 4096
tegn, starter neste dag i en ny embed — det er forventet, ikke en feil. Uke 42
var ca. 4150 tegn, og søndag fikk sin egen embed. URL-ene i markdown-lenkene
teller med selv om de er usynlige (ca. 1750 av tegnene den uka). Går summen
over 6000, kuttes resten med en henvisning til osloomvendt.no.

Krediteringen står nederst i beskrivelsen og ikke i footeren, fordi Discord ikke
rendrer markdown i footere. `TITLE_GAP` bruker et zero width space fordi Discord
trimmer blanke linjer i starten av en beskrivelse. Embed-tekst vises mindre enn
vanlig meldingstekst; dagsnavn som `###`-overskrifter er måten å få større tekst
på uten å forlate embeds.

**Tid er ISO-uke i Europe/Oslo.** API-et leverer UTC (`2026-09-23T18:00:00Z` er
20:00 i Oslo). `model/Week.kt` eier ukeberegningen; `OSLO`-konstanten der er
eneste sted tidssonen skal defineres. `Week.next()` finnes hvis boten skal
varsle om kommende uke i stedet for inneværende.

**Oppsummeringen dekker onsdag–søndag, ikke hele uka**, og postes onsdag
14:00. `buildDigest()` filtrerer på `Week.digestDays` etter dato i Oslo-tid. Filteret må stå der selv om
kildene blir spurt om én uke: Oslo Omvendts uke 44 (2026) begynte med et event
søndag 25.10 og hadde ingenting søndag 1.11, så kildens ukegrenser er ikke
ISO-ukas.

**Alle innstillinger leses gjennom `Env`** (`Env[...]`, `Env.require`,
`Env.flag`): miljøvariabler først, så en lokal `.env` (git-ignorert), slik at
prosjektet kan kjøres rett fra IntelliJ. Bruker du `System.getenv` direkte,
virker ikke `.env` — og da stemmer ikke README lenger. `Config.load()` samler
verdiene boten trenger ved oppstart; `SHOW_TIME` slås opp der den brukes. `DISCORD_TOKEN` og `DISCORD_CHANNEL_IDS` er påkrevd; `POST_DAYS` og
`POST_TIME` defaulter til onsdag 14:00. `DISCORD_CHANNEL_IDS` og
`POST_DAYS` er kommaseparerte lister; entallsformene `DISCORD_CHANNEL_ID` og
`POST_DAY` virker fortsatt som fallback.

**Flere kanaler feiler uavhengig av hverandre.** `DiscordPublisher.post()`
logger og hopper over en kanal boten ikke når fram til, så de andre får posten
sin; den kaster bare hvis ingen av kanalene tok imot. Samme prinsipp som for
kilder i `buildDigest()`.

**Kjøreplanen ligger tre steder, og to av dem er i bruk.** I produksjon kjører
boten på GitHub Actions med `--now`. `POST_DAYS`/`POST_TIME` gjelder kun den
kontinuerlige modusen (`mvn exec:java` uten flagg) og rører ikke Actions.

Utløserne er to, og det er med vilje. `launchd/no.shipit.eventbot-trigger.plist`
på utviklingsmaskinen kaller `workflow_dispatch` kl. 14:00 onsdag —
det er den som faktisk treffer klokkeslettet. Cron i workflowen er et
sikkerhetsnett for dagene maskinen er av. Grunnen står i måledata: 22.09.2026
fyrte GitHub av 2 av 12 planlagte luker, og de to kom 3–4 timer for sent.
GitHub-cron kan altså ikke levere «kl. 14:00» for dette repoet, uansett hva som
står i YAML-en. Derfor `*/10` og ikke ett fast minutt — flere sjanser til at én
kommer fram.

Cron er i UTC. To timer fyres av hver kjøredag (12 og 13), og vakten slipper
gjennom den som er 14:00 i Oslo akkurat nå. Vakten sammenligner mot
`github.event.schedule`, altså cron-uttrykket som utløste kjøringen, ikke mot
klokka når jobben starter — en kjøring kan ligge timer i kø, og en
veggklokkevakt ville forkastet den som «tvilling». De to timene må stå som hver
sin `- cron:`-linje: `github.event.schedule` gjengir uttrykket slik det står i
fila, så en samlet `'*/10 12,13 * * 3'` gir begge timene samme uttrykk og gjør
vakten blind — timefeltet blir `12,13`, som bash-aritmetikken leser som
komma-operator og gir 13. Da forkastes alt om sommeren, og alt slipper gjennom
om vinteren.

**`.github/sist-postet.txt` er det som gjør to utløsere trygt.** Den holder
datoen for siste post i Oslo-tid, og kjøringer som ser dagens dato der, hopper
av før `setup-java`. Uten den ville en forsinket cron-kjøring postet oppå
launchd-posten. `workflow_dispatch` har en `tving`-input for å overstyre
markøren når du bevisst vil poste to ganger samme dag. Checkout bruker
`ref: main` og ikke `github.sha` nettopp her: en kjøring som har ligget i kø
ville ellers lest en markør som er timer gammel.

Markøren skrives gjennom Contents-API-et, ikke med `git push`. Checkouten er
grunn, og et push derfra kan avvises som non-fast-forward hvis `main` har
flyttet seg mens jobben stod i kø — da ville posten vært sendt uten at markøren
ble satt, og neste luke samme dag hadde postet på nytt. Steget kjører bevisst
*ikke* med `always()`: feiler selve posten, skal markøren stå urørt så neste
luke får prøve.

Den commiten er samtidig det som holder repoet i live. GitHub slår av planlagte
workflows i offentlige repoer etter 60 dager uten nye commits, og en bot som
bare poster til Discord teller ikke som aktivitet. Derfor står
`permissions: contents: write` der.
