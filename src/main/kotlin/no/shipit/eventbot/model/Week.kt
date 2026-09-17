package no.shipit.eventbot.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

val OSLO: ZoneId = ZoneId.of("Europe/Oslo")

/** ISO-uke, som er det /api/events spør etter. */
data class Week(val year: Int, val week: Int) {
    val monday: LocalDate =
        LocalDate.of(year, 1, 4)
            .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
            .with(WeekFields.ISO.dayOfWeek(), 1)

    val sunday: LocalDate = monday.plusDays(6)

    companion object {
        fun of(date: LocalDate): Week =
            Week(date.get(WeekFields.ISO.weekBasedYear()), date.get(WeekFields.ISO.weekOfWeekBasedYear()))

        fun current(): Week = of(LocalDate.now(OSLO))

        fun next(): Week = of(LocalDate.now(OSLO).plusWeeks(1))
    }
}
