# Храмові будівлі церков (Економіка 6b)

`ChurchStructurePlacer` (плагін) при автоспавні церкви біля села вантажить структуру
`mysteries:church_<shortId>` із цього датапаку (`data/mysteries/structure/church_<shortId>.nbt`).
Якщо NBT відсутній — у лог іде warning, а сайт + NPC-священик усе одно ставляться
(фолбек без будівлі). Тобто реліз коду **не блокується** відсутністю цих файлів;
храми з'являються як будівлі в міру додавання `.nbt`.

## 10 очікуваних ключів

`shortId` = id церкви без префікса `church-`, дефіси → підкреслення:

| Церква (id у реєстрі)         | Ключ структури                       | Файл                                  |
|-------------------------------|--------------------------------------|---------------------------------------|
| church-evernight              | `mysteries:church_evernight`         | `church_evernight.nbt`                |
| church-god-of-combat          | `mysteries:church_god_of_combat`     | `church_god_of_combat.nbt`            |
| church-earth-mother           | `mysteries:church_earth_mother`      | `church_earth_mother.nbt`             |
| church-lord-of-storms         | `mysteries:church_lord_of_storms`    | `church_lord_of_storms.nbt`           |
| church-knowledge-wisdom       | `mysteries:church_knowledge_wisdom`  | `church_knowledge_wisdom.nbt`         |
| church-eternal-sun            | `mysteries:church_eternal_sun`       | `church_eternal_sun.nbt`              |
| church-steam-machinery        | `mysteries:church_steam_machinery`   | `church_steam_machinery.nbt`          |
| church-fool                   | `mysteries:church_fool`              | `church_fool.nbt`                     |
| church-eternal-darkness       | `mysteries:church_eternal_darkness`  | `church_eternal_darkness.nbt`         |
| church-ruler-of-calamity      | `mysteries:church_ruler_of_calamity` | `church_ruler_of_calamity.nbt`        |

## Як зберегти будівлю (в грі)

1. Побудуй храм у світі.
2. Постав **Structure Block** у режимі SAVE, задай назву `mysteries:church_<shortId>`,
   виділи область, натисни SAVE.
3. Забери згенерований `.nbt` із `world/generated/mysteries/structures/church_<shortId>.nbt`
   у `mysteries-datapack/data/mysteries/structure/` (джерело правди — репозиторій, не сервер).
4. NBT-файли додавай окремими комітами в міру готовності.

> Плейсер ставить структуру без ротації (`StructureRotation.NONE`) у точку сайту;
> проєктуй будівлю з дверима/входом до NPC-священика, що спавниться в тій самій точці.
