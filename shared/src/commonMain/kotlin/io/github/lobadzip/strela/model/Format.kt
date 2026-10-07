package io.github.lobadzip.strela.model

/** Display helpers shared by both apps. Money stays in kopecks everywhere and only becomes text here. */
object Format {
    private const val NBSP = ' '

    /** 145000 → "1 450 ₽"; kopecks are shown only when there are any. */
    fun rub(kopecks: Long): String {
        val sign = if (kopecks < 0) "−" else ""
        val abs = if (kopecks < 0) -kopecks else kopecks
        val rubles = groupThousands(abs / 100)
        val rest = abs % 100
        val tail = if (rest == 0L) "" else "," + rest.toString().padStart(2, '0')
        return "$sign$rubles$tail$NBSP₽"
    }

    fun groupThousands(value: Long): String {
        val digits = value.toString()
        val sb = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(NBSP)
            sb.append(c)
        }
        return sb.toString()
    }

    /** 850 → "850 м", 2400 → "2,4 км". */
    fun distance(meters: Int): String = when {
        meters < 1000 -> "${(meters / 10) * 10}${NBSP}м"
        else -> {
            val tenths = (meters + 50) / 100
            val km = tenths / 10
            val frac = tenths % 10
            if (frac == 0) "$km${NBSP}км" else "$km,$frac${NBSP}км"
        }
    }

    /** 90 → "2 мин", 3900 → "1 ч 5 мин". Rounds up: a courier is never early by our clock. */
    fun duration(seconds: Int): String {
        val minutes = ((seconds + 59) / 60).coerceAtLeast(1)
        return if (minutes < 60) "$minutes${NBSP}мин" else {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "$h${NBSP}ч" else "$h${NBSP}ч $m${NBSP}мин"
        }
    }

    /** Wall-clock HH:mm in the city's offset. Moscow has had no DST since 2014, so an offset is enough. */
    fun clock(epochMillis: Long, utcOffsetMinutes: Int): String {
        val minutesOfDay = (epochMillis.floorDiv(60_000L) + utcOffsetMinutes).mod(24 * 60L)
        val h = minutesOfDay / 60
        val m = minutesOfDay % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    fun epochDay(epochMillis: Long, utcOffsetMinutes: Int): Long =
        (epochMillis + utcOffsetMinutes * 60_000L).floorDiv(86_400_000L)

    /** Plural for Russian numerals: plural(5, "заказ", "заказа", "заказов"). */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }
}
