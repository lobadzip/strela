package io.github.lobadzip.strela.server.demo

import io.github.lobadzip.strela.model.City
import io.github.lobadzip.strela.model.GeoPoint
import io.github.lobadzip.strela.model.OrderTag
import io.github.lobadzip.strela.model.Vehicle

/** A shop the courier picks up from. Names are made up; the streets are real. */
data class Shop(
    val title: String,
    val address: String,
    val point: GeoPoint,
    val note: String,
    val menu: List<Pair<String, Long>>,
    val tags: List<OrderTag> = emptyList(),
)

data class Home(val address: String, val point: GeoPoint)

data class DemoCourier(
    val id: String,
    val name: String,
    val phone: String,
    val vehicle: Vehicle,
    val rating: Double,
    /** Visitors can sign in as these; the rest are only ever driven by the simulator. */
    val loginAllowed: Boolean,
)

/**
 * Central Moscow, inside the Garden Ring: dense enough that every delivery is a short, believable
 * trip, and compact enough that the whole demo fits on one screen of map.
 */
object DemoCity {
    val city = City(name = "Москва", center = GeoPoint(55.7539, 37.6208), utcOffsetMinutes = 180)

    /** Demo sign-in: these numbers cannot exist, so tapping "call" never reaches a real person. */
    const val DEMO_CODE = "0000"

    val couriers = listOf(
        DemoCourier("c-alexey", "Алексей Смирнов", "+7 000 000-00-01", Vehicle.BIKE, 4.94, loginAllowed = true),
        DemoCourier("c-marina", "Марина Ковалёва", "+7 000 000-00-02", Vehicle.FOOT, 4.98, loginAllowed = true),
        DemoCourier("c-timur", "Тимур Ахмедов", "+7 000 000-00-03", Vehicle.CAR, 4.87, loginAllowed = true),
        DemoCourier("c-oleg", "Олег Волков", "+7 000 000-00-11", Vehicle.BIKE, 4.91, loginAllowed = false),
        DemoCourier("c-dasha", "Дарья Лебедева", "+7 000 000-00-12", Vehicle.FOOT, 4.96, loginAllowed = false),
        DemoCourier("c-ilya", "Илья Морозов", "+7 000 000-00-13", Vehicle.CAR, 4.82, loginAllowed = false),
        DemoCourier("c-sveta", "Светлана Орлова", "+7 000 000-00-14", Vehicle.BIKE, 4.89, loginAllowed = false),
    )

    val shops = listOf(
        Shop(
            "Пекарня «Тесто и мак»", "ул. Маросейка, 6", GeoPoint(55.75690, 37.63430),
            "Окно выдачи справа от входа",
            listOf("Круассан с миндалём" to 24_000, "Багет на закваске" to 19_000, "Синнабон" to 27_000,
                "Пирог с вишней" to 89_000, "Улитка с маком" to 21_000),
            listOf(OrderTag.HOT),
        ),
        Shop(
            "Цветочная «Пион»", "ул. Покровка, 17", GeoPoint(55.75950, 37.64550),
            "Звонок у двери, заказ на стойке",
            listOf("Букет пионов" to 450_000, "Букет тюльпанов" to 290_000, "Открытка" to 25_000,
                "Ваза стеклянная" to 120_000),
            listOf(OrderTag.FRAGILE),
        ),
        Shop(
            "Кофейня «Обжарка»", "ул. Большая Дмитровка, 20", GeoPoint(55.76470, 37.61220),
            "Заказы на полке «Доставка» у кассы",
            listOf("Капучино 0,4" to 32_000, "Флэт уайт" to 29_000, "Чизкейк" to 38_000,
                "Сэндвич с курицей" to 42_000, "Зерно 250 г" to 95_000),
            listOf(OrderTag.HOT),
        ),
        Shop(
            "Лавка «Сыр и мёд»", "ул. Пятницкая, 33", GeoPoint(55.73970, 37.62830),
            "Вход с Пятницкой, спросить у продавца",
            listOf("Камамбер" to 69_000, "Мёд гречишный 500 г" to 85_000, "Пармезан 200 г" to 79_000,
                "Набор сыров" to 249_000),
            listOf(OrderTag.HEAVY),
        ),
        Shop(
            "Пиццерия «Корочка»", "ул. Арбат, 30", GeoPoint(55.74930, 37.59130),
            "Термосумка обязательна",
            listOf("Пицца «Маргарита» 30 см" to 59_000, "Пицца «Пепперони» 30 см" to 69_000,
                "Лимонад" to 19_000, "Тирамису" to 34_000),
            listOf(OrderTag.HOT),
        ),
        Shop(
            "Аптека «Ромашка»", "ул. Сретенка, 18", GeoPoint(55.76820, 37.63270),
            "Выдача у второй кассы",
            listOf("Витамин D3" to 49_000, "Термометр" to 39_000, "Пластырь" to 12_000,
                "Сироп от кашля" to 31_000),
        ),
        Shop(
            "Книжный «Переплёт»", "ул. Большая Никитская, 24", GeoPoint(55.75710, 37.60140),
            "Пакет с заказом у администратора",
            listOf("«Мастер и Маргарита»" to 79_000, "Блокнот в клетку" to 35_000, "Атлас Москвы" to 129_000,
                "Подарочная упаковка" to 15_000),
        ),
        Shop(
            "Рамен «Бульон»", "ул. Земляной Вал, 33", GeoPoint(55.75780, 37.65970),
            "Выдача курьерам с торца здания",
            listOf("Рамен тонкоцу" to 64_000, "Гёдза с курицей" to 39_000, "Моти" to 22_000,
                "Холодный чай" to 18_000),
            listOf(OrderTag.HOT),
        ),
    )

    val homes = listOf(
        Home("ул. Тверская, 12, стр. 2", GeoPoint(55.76330, 37.60770)),
        Home("Чистопрудный бульвар, 12", GeoPoint(55.76180, 37.64500)),
        Home("ул. Мясницкая, 24/7", GeoPoint(55.76370, 37.63660)),
        Home("ул. Петровка, 26", GeoPoint(55.76670, 37.61670)),
        Home("ул. Остоженка, 25", GeoPoint(55.73840, 37.59660)),
        Home("ул. Пречистенка, 17", GeoPoint(55.74180, 37.59460)),
        Home("ул. Малая Бронная, 28", GeoPoint(55.76340, 37.59350)),
        Home("ул. Большая Ордынка, 21", GeoPoint(55.74040, 37.62430)),
        Home("ул. Большая Якиманка, 22", GeoPoint(55.73460, 37.61280)),
        Home("Цветной бульвар, 15", GeoPoint(55.77120, 37.62030)),
        Home("ул. Солянка, 1", GeoPoint(55.75270, 37.63820)),
        Home("Гоголевский бульвар, 10", GeoPoint(55.74550, 37.60030)),
        Home("ул. Новый Арбат, 15", GeoPoint(55.75220, 37.58880)),
        Home("Садовническая ул., 9", GeoPoint(55.74490, 37.63360)),
        Home("ул. Покровка, 42", GeoPoint(55.75900, 37.65200)),
        Home("ул. Неглинная, 18", GeoPoint(55.76500, 37.62150)),
        Home("ул. Рождественка, 12", GeoPoint(55.76280, 37.62550)),
        Home("Новинский бульвар, 12", GeoPoint(55.75350, 37.58350)),
        Home("ул. Воздвиженка, 10", GeoPoint(55.75250, 37.60400)),
        Home("ул. Знаменка, 13", GeoPoint(55.74930, 37.60400)),
        Home("ул. Таганская, 17", GeoPoint(55.73980, 37.66200)),
        Home("Котельническая наб., 1/15", GeoPoint(55.74710, 37.64270)),
        Home("ул. Большая Полянка, 30", GeoPoint(55.73650, 37.61850)),
        Home("ул. Спиридоновка, 9", GeoPoint(55.76000, 37.59250)),
        Home("Трёхпрудный пер., 11", GeoPoint(55.76520, 37.59780)),
        Home("ул. Ильинка, 4", GeoPoint(55.75450, 37.62500)),
    )

    val customers = listOf(
        "Анна", "Дмитрий", "Ольга", "Сергей", "Екатерина", "Михаил", "Наталья", "Игорь",
        "Юлия", "Павел", "Светлана", "Артём", "Ксения", "Роман", "Виктория", "Илья",
    )

    val doorNotes = listOf(
        { r: kotlin.random.Random -> "Подъезд ${r.nextInt(1, 6)}, этаж ${r.nextInt(2, 10)}, кв. ${r.nextInt(5, 160)}" },
        { r: kotlin.random.Random -> "Подъезд ${r.nextInt(1, 4)}, домофон ${r.nextInt(10, 99)}К, этаж ${r.nextInt(2, 8)}" },
        { _: kotlin.random.Random -> "Офис на 3 этаже, на ресепшене скажите «для Strela»" },
        { r: kotlin.random.Random -> "Кв. ${r.nextInt(5, 160)}, позвоните за 5 минут" },
        { _: kotlin.random.Random -> "Шлагбаум со двора, код 2580" },
    )
}
