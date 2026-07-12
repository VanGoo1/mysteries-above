package me.vangoo.domain.organizations;

import java.util.List;
import java.util.Optional;

import static me.vangoo.domain.organizations.InstitutionType.CHURCH;
import static me.vangoo.domain.organizations.InstitutionType.SECRET_ORDER;
import static me.vangoo.domain.organizations.PathwayAccess.full;
import static me.vangoo.domain.organizations.PathwayAccess.partial;

/** Кодовий реєстр усіх канонічних інституцій (10 церков + 25 таємних організацій). */
public final class InstitutionRegistry {

    private final List<Institution> all = buildAll();

    public List<Institution> all() {
        return all;
    }

    public List<Institution> churches() {
        return all.stream().filter(i -> i.type() == CHURCH).toList();
    }

    public Optional<Institution> byId(String id) {
        return all.stream().filter(i -> i.id().equalsIgnoreCase(id)).findFirst();
    }

    public List<Institution> churchesAccepting(String pathwayNameOrNull) {
        return churches().stream().filter(c -> c.acceptsPathway(pathwayNameOrNull)).toList();
    }

    private static List<Institution> buildAll() {
        return List.of(
                // ── Церкви (10) ──────────────────────────────────────────────
                new Institution("church-evernight", CHURCH, "Церква Богині Вічної Ночі",
                        "Богиня Вічної Ночі береже сон світу й таємниці пітьми.",
                        List.of(full("Darkness"), full("Death"), full("TwilightGiant"),
                                partial("Fool", 3), partial("Hermit", 9))),
                new Institution("church-god-of-combat", CHURCH, "Церква Бога Битви",
                        "Бог Битви шанує силу, звитягу і чесний двобій.",
                        List.of(full("TwilightGiant"), partial("Darkness", 4), partial("Death", 4))),
                new Institution("church-earth-mother", CHURCH, "Церква Матері-Землі",
                        "Мати-Земля дарує врожай, родючість і тихе зростання.",
                        List.of(full("Mother"), full("Moon"))),
                new Institution("church-lord-of-storms", CHURCH, "Церква Володаря Штормів",
                        "Володар Штормів панує над морем, громом і гнівом небес.",
                        List.of(full("Tyrant"), partial("Visionary", 7))),
                new Institution("church-knowledge-wisdom", CHURCH, "Церква Бога Знань та Мудрості",
                        "Бог Знань та Мудрості освячує книги, школи й розум.",
                        List.of(full("WhiteTower"))),
                new Institution("church-eternal-sun", CHURCH, "Церква Вічного Палаючого Сонця",
                        "Вічне Палаюче Сонце спалює нечисть і дарує світло.",
                        List.of(full("Sun"))),
                new Institution("church-steam-machinery", CHURCH, "Церква Бога Пари та Машин",
                        "Бог Пари та Машин благословляє фабрики, винаходи й прогрес.",
                        List.of(full("Paragon"), partial("Hermit", 4))),
                new Institution("church-fool", CHURCH, "Церква Блазня",
                        "Той, хто сидить над сірим туманом; править через підвладні групи.",
                        List.of(full("Door", "Сім'я Авраам"),
                                partial("Chained", 1, "Фракція Стриманості"),
                                partial("TwilightGiant", 2, "Срібне Місто"),
                                partial("Death", 2, "Срібне Місто"),
                                partial("HangedMan", 2, "Срібне Місто"),
                                partial("Sun", 2, "Срібне Місто"),
                                partial("Darkness", 2, "Місячне Місто"),
                                partial("Justiciar", 2, "Місячне Місто"),
                                full("RedPriest", "Місячне Місто"))),
                new Institution("church-eternal-darkness", CHURCH, "Церква Вічної Темряви",
                        "Вічна Темрява — давній лик ночі, що не знає світанку.",
                        List.of(full("Darkness"), full("TwilightGiant"), full("Death"))),
                new Institution("church-ruler-of-calamity", CHURCH, "Церква Володаря Катаклізмів",
                        "Володар Катаклізмів несе бурю, пошесть і кару.",
                        List.of(full("RedPriest"), full("Demoness"))),

                // ── Таємні організації (25) — у 6b лише дані ────────────────
                new Institution("order-great-white-brotherhood", SECRET_ORDER,
                        "Велике Біле Братство", "Братство обраних, що шукає вознесіння.", List.of()),
                new Institution("order-rose-redemption", SECRET_ORDER,
                        "Спокута Рози", "Уламок Школи Думки Рози, що шукає прощення.", List.of()),
                new Institution("order-twilight-hermit", SECRET_ORDER,
                        "Орден Сутінкового Відшельника", "Служить Відшельникові Сутінків.", List.of()),
                new Institution("order-moses-ascetic", SECRET_ORDER,
                        "Аскетичний Орден Мойсея", "Аскети таємниць і самозречення.",
                        List.of(full("Hermit"))),
                new Institution("order-demoness-sect", SECRET_ORDER,
                        "Секта Відьом", "Служниці Відьми у вічній змові.",
                        List.of(full("Demoness"))),
                new Institution("order-mirror-people", SECRET_ORDER,
                        "Дзеркальні Люди", "Живуть у відображеннях і крадуть лики.", List.of()),
                new Institution("order-secret-order", SECRET_ORDER,
                        "Таємний Орден", "Стережуть спадок Блазня.",
                        List.of(full("Fool"))),
                new Institution("order-shadow-of-order", SECRET_ORDER,
                        "Тінь Порядку", "Вірять у залізний лад Чорного Імператора.",
                        List.of(full("BlackEmperor"))),
                new Institution("order-blood-sanctify", SECRET_ORDER,
                        "Секта Освячення Крові", "Кров — двері в Безодню.",
                        List.of(full("Abyss"))),
                new Institution("order-theosophy", SECRET_ORDER,
                        "Теософський Орден", "Шукачі прихованих імен богів.",
                        List.of(partial("Demoness", 8), partial("Door", 8))),
                new Institution("order-numinous-episcopate", SECRET_ORDER,
                        "Нумінозний Епіскопат", "Єпископат мертвих і тиші.",
                        List.of(full("Death"))),
                new Institution("order-life-school", SECRET_ORDER,
                        "Школа Думки Життя", "Вчення про колесо життя і місячні припливи.",
                        List.of(full("WheelOfFortune"), full("Moon"))),
                new Institution("order-rose-school", SECRET_ORDER,
                        "Школа Думки Рози", "Давнє вчення кайданів і місяця.",
                        List.of(full("Chained"), full("Moon"))),
                new Institution("order-aurora", SECRET_ORDER,
                        "Орден Аврори", "Чекають світанку Повішеного.",
                        List.of(full("HangedMan"), partial("Door", 4), full("WheelOfFortune"))),
                new Institution("order-iron-blood-cross", SECRET_ORDER,
                        "Орден Залізного та Кривавого Хреста", "Хрест із заліза, віра з крові.",
                        List.of(full("RedPriest"))),
                new Institution("order-psychology-alchemists", SECRET_ORDER,
                        "Психологічні Алхіміки", "Алхімія розуму й сновидінь.",
                        List.of(full("Visionary"))),
                new Institution("order-element-dawn", SECRET_ORDER,
                        "Елемент Світанку", "Первісні стихії проти нового світу.",
                        List.of(full("Hermit"))),
                new Institution("order-hermits-of-fate", SECRET_ORDER,
                        "Відшельники Долі", "Злодії доль і читачі помилок світу.",
                        List.of(full("Error"))),
                new Institution("order-naturism-sect", SECRET_ORDER,
                        "Секта Натуризму", "Повернення до дикої природи кайданів.",
                        List.of(full("Chained"))),
                new Institution("order-baboons-society", SECRET_ORDER,
                        "Дослідницьке Товариство Кучерявих Бабуїнів",
                        "Ексцентричні дослідники потойбічного.", List.of()),
                new Institution("order-april-fools", SECRET_ORDER,
                        "Першоквітневі Дурні", "Жарт, що став вірою.", List.of()),
                new Institution("order-nightstalkers", SECRET_ORDER,
                        "Нічні Сталкери", "Полюють у місячному сяйві.",
                        List.of(full("Mother"), full("Moon"))),
                new Institution("order-bliss-society", SECRET_ORDER,
                        "Товариство Блаженства", "Насолода як шлях у Безодню.",
                        List.of(full("Abyss"), full("Chained"))),
                new Institution("order-god-descent-school", SECRET_ORDER,
                        "Школа Сходження Бога", "Закликають бога зійти в тіло.",
                        List.of(full("Mother"), full("Moon"))),
                new Institution("order-truth-school", SECRET_ORDER,
                        "Школа Істини", "Істина понад закон і хаос.",
                        List.of(full("BlackEmperor"), full("Justiciar"))));
    }
}
