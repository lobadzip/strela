package io.github.lobadzip.strela

import io.github.lobadzip.strela.model.Format
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    private val nbsp = ' '

    @Test
    fun `rubles are grouped by thousands and kopecks appear only when present`() {
        assertEquals("0${nbsp}₽", Format.rub(0))
        assertEquals("250${nbsp}₽", Format.rub(25_000))
        assertEquals("1${nbsp}450${nbsp}₽", Format.rub(145_000))
        assertEquals("1${nbsp}234${nbsp}567,05${nbsp}₽", Format.rub(123_456_705))
        assertEquals("−90${nbsp}₽", Format.rub(-9_000))
    }

    @Test
    fun `distances switch to kilometres with one decimal`() {
        assertEquals("850${nbsp}м", Format.distance(857))
        assertEquals("1${nbsp}км", Format.distance(1_020))
        assertEquals("2,4${nbsp}км", Format.distance(2_380))
    }

    @Test
    fun `durations round up to whole minutes`() {
        assertEquals("1${nbsp}мин", Format.duration(5))
        assertEquals("2${nbsp}мин", Format.duration(61))
        assertEquals("1${nbsp}ч 5${nbsp}мин", Format.duration(3_900))
    }

    @Test
    fun `clock applies the city offset and wraps past midnight`() {
        // 2026-01-01T21:30Z is 00:30 in Moscow (UTC+3).
        val millis = 1_767_303_000_000L
        assertEquals("21:30", Format.clock(millis, 0))
        assertEquals("00:30", Format.clock(millis, 180))
    }

    @Test
    fun `russian plurals`() {
        val forms = listOf(1, 2, 5, 11, 21, 22, 25, 111).map { Format.plural(it, "заказ", "заказа", "заказов") }
        assertEquals(
            listOf("заказ", "заказа", "заказов", "заказов", "заказ", "заказа", "заказов", "заказов"),
            forms,
        )
    }
}
