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

**Oslo Omvendt har åpent API — ikke scrape HTML.**
`GET https://www.osloomvendt.no/api/events?year=<N>&week=<N>`, ingen nøkkel.
Svaret er `{"events": [...]}`. Feltene `genres`, `description` og `highlight` er
ofte `null`. De tilbyr også en MCP-server på `/api/mcp`.

**Discord tar maks 2000 tegn per melding**, og `digest()` returnerer derfor en
liste. Oppdelingen er én melding med overskrift pluss én melding per dag — ikke
for å spare tegn, men fordi avstanden blir jevn: blanke linjer inne i en melding
gir ujevn luft, og forsvinner helt der meldingene deles. `dayMessages()` deler
en enkelt dag videre bare hvis den alene sprenger grensen.

**Tid er ISO-uke i Europe/Oslo.** API-et leverer UTC (`2026-09-23T18:00:00Z` er
20:00 i Oslo). `model/Week.kt` eier ukeberegningen; `OSLO`-konstanten der er
eneste sted tidssonen skal defineres. `Week.next()` finnes hvis boten skal
varsle om kommende uke i stedet for inneværende.

**Konfigurasjon** leses av `Config.load()`: miljøvariabler først, så en lokal
`.env` (git-ignorert) som fallback, slik at prosjektet kan kjøres rett fra
IntelliJ. `DISCORD_TOKEN` og `DISCORD_CHANNEL_ID` er påkrevd; `POST_DAY` og
`POST_TIME` defaulter til mandag 09:00.
