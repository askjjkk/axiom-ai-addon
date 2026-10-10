package com.example.axiomai.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Brush ideas for the start screen of a new chat. Four random ones are shown each time. */
final class Ideas {
    record Idea(String titleEn, String descEn, String promptEn, String titleRu, String descRu, String promptRu) {
        String title() { return Tr.isRussian() ? titleRu : titleEn; }
        String desc() { return Tr.isRussian() ? descRu : descEn; }
        String prompt() { return Tr.isRussian() ? promptRu : promptEn; }
    }

    private static final List<Idea> ALL = List.of(
        new Idea("Overgrown ruins", "Moss, vines and cracks: a ruin forgotten for centuries",
            "Make stone brick walls look like ancient overgrown ruins: replace some stone bricks with cracked and mossy variants (more of them near the ground), put moss carpet on top surfaces and let vines hang down the sides. Sliders for age and vine density.",
            "Заросшие руины", "Мох, лианы и трещины: руины, забытые на века",
            "Сделай каменные кирпичные стены похожими на древние заросшие руины: часть кирпичей заменяй потрескавшимися и замшелыми (ближе к земле чаще), на верхних поверхностях клади моховой ковёр, а по бокам пусть свисают лианы. Ползунки возраста и густоты лиан."),
        new Idea("Cherry petals", "Fallen pink petals drifting over the grass",
            "Scatter fallen cherry blossom petals (pink_petals with random amount 1 to 4 and random facing) on grass and dirt, in drifts that are denser in noise patches, with a density slider.",
            "Лепестки сакуры", "Опавшие розовые лепестки на траве",
            "Рассыпь опавшие лепестки сакуры (pink_petals со случайным количеством от 1 до 4 и случайным направлением) на траве и земле: кучками, гуще в пятнах шума, с ползунком плотности."),
        new Idea("Cobweb corners", "Dusty corners and ceilings full of cobwebs",
            "Put cobwebs into the upper corners of rooms and under ceilings: an air block that touches a ceiling and at least one wall gets a cobweb with a chance, more often when it touches two walls. Sliders for chance and for how far from corners it spreads.",
            "Паутина по углам", "Пыльные углы и потолки, затянутые паутиной",
            "Затяни паутиной верхние углы комнат и места под потолком: воздушный блок, который касается потолка и хотя бы одной стены, получает паутину с некоторым шансом, а если касается двух стен, то чаще. Ползунки шанса и дальности от угла."),
        new Idea("Icicles", "Icicles hanging under roofs and overhangs",
            "Hang icicles under every solid block that has air below it: from 1 to 5 blocks of packed ice or blue ice, shorter on the edges and longer in the middle, with sliders for chance and maximum length.",
            "Сосульки", "Сосульки под крышами и нависающими краями",
            "Развесь сосульки под каждым твёрдым блоком, под которым есть воздух: от 1 до 5 блоков упакованного или синего льда, по краям короче, в середине длиннее. Ползунки шанса и максимальной длины."),
        new Idea("Cracked ice", "Frozen lake with a net of deep blue cracks",
            "Turn the top layer of water into ice with a net of cracks: packed ice as the main surface and blue ice veins along the edges of a voronoi pattern. Sliders for cell size and crack width.",
            "Треснувший лёд", "Замёрзшее озеро с сетью глубоких синих трещин",
            "Преврати верхний слой воды в лёд с сетью трещин: основная поверхность из упакованного льда, а по краям ячеек узора Вороного синие жилы из синего льда. Ползунки размера ячеек и ширины трещин."),
        new Idea("Lava cracks", "Glowing magma cracks across dark volcanic rock",
            "Make volcanic ground: turn the surface blocks into basalt and blackstone and cut glowing cracks of magma blocks into it along voronoi edges. Sliders for crack width and for how much magma there is.",
            "Лавовые трещины", "Светящиеся магмовые трещины в тёмной вулканической породе",
            "Сделай вулканическую землю: поверхностные блоки превращай в базальт и чёрный камень и прорезай в них светящиеся трещины из магмовых блоков по границам ячеек Вороного. Ползунки ширины трещин и количества магмы."),
        new Idea("Desert ripples", "Wind ripples and low dunes on sand",
            "Add wind ripples to sand: parallel wavy ridges one block high made of sand on top of the surface, with the wave direction, wave length and ridge height as sliders, slightly distorted by noise.",
            "Барханы и рябь", "Ветровая рябь и низкие дюны на песке",
            "Добавь на песок ветровую рябь: параллельные волнистые гребни высотой в один блок из песка поверх поверхности, чуть искажённые шумом. Направление, длина волны и высота гребня должны быть ползунками."),
        new Idea("Mushroom grove", "Mushroom clusters on mycelium and podzol",
            "Grow clusters of red and brown mushrooms on mycelium and podzol: dense inside noise clumps and rare outside them, sometimes a small mushroom next to a big one. Sliders for density and clump size.",
            "Грибная роща", "Грибные семейки на мицелии и подзоле",
            "Выращивай семейки красных и коричневых грибов на мицелии и подзоле: густо внутри пятен шума и редко вне их. Ползунки плотности и размера пятен."),
        new Idea("Forest floor", "Leaf litter, ferns and mushrooms under the trees",
            "Dress a forest floor: turn some grass and dirt into podzol and coarse dirt, and on top place leaf litter, ferns and a few mushrooms, denser where there are leaves above. Sliders for each kind of plant.",
            "Лесная подстилка", "Опавшая листва, папоротники и грибы под деревьями",
            "Оформи лесную подстилку: часть травы и земли превращай в подзол и каменистую землю, а сверху клади опавшую листву, папоротники и немного грибов, гуще там, где сверху есть листья. По ползунку на каждый вид."),
        new Idea("Coral reef", "Colorful corals and sea pickles on the seabed",
            "Plant a coral reef on sand and gravel under water: clumps of different coral blocks with coral fans and sea pickles on top, with sea grass between the clumps. Waterlogged where needed. Sliders for clump size and density.",
            "Коралловый риф", "Яркие кораллы и морские огурцы на дне",
            "Посади коралловый риф на песке и гравии под водой: кучки разных коралловых блоков, сверху веерные кораллы и морские огурцы, а между кучками морская трава. Где нужно, с водой внутри блока. Ползунки размера кучек и плотности."),
        new Idea("Aging copper", "Copper turning green from the edges, like old roofs",
            "Age copper blocks: the blocks on the outside and near edges become exposed, weathered and then oxidized copper, the further from the inside the greener. Sliders for how far the aging goes and for randomness.",
            "Состаренная медь", "Медь зеленеет с краёв, как старые крыши",
            "Состаривай медные блоки: блоки снаружи и у краёв становятся слегка окисленными, выветрившимися и потом окисленными, чем дальше от середины, тем зеленее. Ползунки глубины старения и случайности."),
        new Idea("Amethyst crystals", "Crystal clusters sprouting from the ground in groups",
            "Make amethyst crystals grow out of stone and deepslate: groups of amethyst clusters pointing up with a few large and buds around, sometimes a budding amethyst block under them. Sliders for density and group size.",
            "Аметистовые кристаллы", "Друзы кристаллов, растущие из земли группами",
            "Заставь аметистовые кристаллы расти из камня и глубинного сланца: группы аметистовых друз, направленных вверх, с несколькими большими и мелкими почками вокруг, иногда с растущим аметистом под ними. Ползунки плотности и размера групп."),
        new Idea("Dripstone cave", "Stalactites and stalagmites of random lengths",
            "Grow dripstone: pointed dripstone hanging down from cave ceilings and growing up from floors, with random lengths and the right thickness at the tip, sometimes meeting in a column. Sliders for density and maximum length.",
            "Сталактиты и сталагмиты", "Натёчные образования случайной длины",
            "Вырасти натёчный камень: капельник, свисающий с потолков пещер и растущий вверх с пола, случайной длины и с правильной толщиной на кончике, иногда сходящийся в колонну. Ползунки плотности и максимальной длины."),
        new Idea("Snow drifts", "Snow piling up against walls and in hollows",
            "Pile snow against walls: snow layers that are thicker next to a wall and thinner further away, with noise, on top of any solid surface, up to a maximum of 8 layers. Sliders for drift width and maximum height.",
            "Сугробы", "Снег, наметённый к стенам и в ямки",
            "Наметай снег к стенам: слои снега толще у стены и тоньше вдали, с шумом, поверх любой твёрдой поверхности, максимум 8 слоёв. Ползунки ширины сугроба и максимальной высоты."),
        new Idea("Pumpkin patch", "Pumpkins and melons scattered over a field",
            "Scatter pumpkins and melons over grass with random facing, with a little leafy vine next to some of them, in clumps. Sliders for density and the share of pumpkins against melons.",
            "Тыквенное поле", "Тыквы и арбузы, разбросанные по полю",
            "Разбросай по траве тыквы и арбузы со случайным направлением, рядом с частью из них небольшие стебли, кучками. Ползунки плотности и доли тыкв против арбузов."),
        new Idea("Crop rows", "Farm rows with irrigation channels",
            "Turn flat grass into farm rows: farmland rows with wheat, carrots and potatoes in random growth stages, and a water channel every fourth row. Sliders for row spacing and for the share of each crop.",
            "Грядки с каналами", "Ряды полей с оросительными каналами",
            "Преврати ровную траву в грядки: ряды вспаханной земли с пшеницей, морковью и картофелем разной стадии роста и водяной канал через каждые четыре ряда. Ползунки расстояния между рядами и доли каждой культуры."),
        new Idea("Winding path", "A worn path with gravel and stone edges",
            "Paint a worn path on the ground: dirt path in the middle, gravel and coarse dirt around, a few cobblestones at the edge, with a wavy boundary made by noise. Sliders for path width and edge roughness.",
            "Тропинка", "Вытоптанная тропа с гравием и камнями по краям",
            "Нарисуй на земле вытоптанную тропу: посередине тропинка, вокруг гравий и каменистая земля, по краям несколько булыжников, граница волнистая от шума. Ползунки ширины тропы и неровности краёв."),
        new Idea("Mossy boulder", "A rounded boulder with moss on top (click to build)",
            "On click build a rounded boulder: a sphere deformed by noise made of stone, andesite and mossy cobblestone, with moss on top and a little grass on the ground around. Sliders for radius, roughness and amount of moss.",
            "Замшелый валун", "Округлый валун с мхом сверху (строится кликом)",
            "По клику построй округлый валун: сфера, деформированная шумом, из камня, андезита и мшистого булыжника, сверху мох, а вокруг на земле немного травы. Ползунки радиуса, неровности и количества мха."),
        new Idea("Bent tree", "A tree with a curved trunk and a layered crown (click)",
            "On click grow a tree: a trunk of random height that bends a little, branches near the top, and a leaf crown made of overlapping blobs with a ragged edge. Choose wood and leaf blocks, sliders for height and crown size.",
            "Кривое дерево", "Дерево с изогнутым стволом и слоистой кроной (кликом)",
            "По клику вырасти дерево: ствол случайной высоты с лёгким изгибом, ветви у верхушки и крона из перекрывающихся шаров с рваным краем. Блоки древесины и листвы выбираются, ползунки высоты и размера кроны."),
        new Idea("Giant mushroom", "A giant mushroom with a stem and a spotted cap",
            "On click build a giant mushroom: a slightly curved stem and a wide rounded cap with spots and hanging gills underneath. Choose the stem and cap blocks, sliders for height and cap width.",
            "Гигантский гриб", "Гриб-великан с ножкой и пятнистой шляпкой",
            "По клику построй гигантский гриб: слегка изогнутая ножка и широкая округлая шляпка с пятнами и свисающими пластинками снизу. Блоки ножки и шляпки выбираются, ползунки высоты и ширины шляпки."),
        new Idea("Spiral tower", "A tower with a spiral staircase that faces the right way",
            "On click build a round tower with a spiral staircase inside: walls from a chosen block, stairs with the correct facing going around the center, a window every few blocks. Sliders for radius and height.",
            "Башня со спиралью", "Башня с винтовой лестницей, ступени смотрят верно",
            "По клику построй круглую башню с винтовой лестницей внутри: стены из выбранного блока, ступени с правильным направлением вокруг центра, окно через несколько блоков. Ползунки радиуса и высоты."),
        new Idea("Floating island", "A small sky island with a grassy top and a rocky belly",
            "On click build a small floating island: a grassy top, a belly of dirt and stone that tapers down into a cone with hanging roots, a few bushes and one small tree. Sliders for radius and thickness.",
            "Парящий остров", "Небольшой остров в небе с травой сверху и скалами снизу",
            "По клику построй небольшой парящий остров: сверху трава, снизу земляное и каменное брюхо, сужающееся конусом, со свисающими корнями, несколько кустов и одно маленькое дерево. Ползунки радиуса и толщины."),
        new Idea("Fairy lights", "Lanterns hanging among the tree crowns",
            "Hang lanterns from the underside of leaves in tree crowns: sparse, only where there is air below, sometimes two on a chain. Sliders for chance and for the number of chains.",
            "Волшебные огоньки", "Фонарики, свисающие в кронах деревьев",
            "Развесь фонарики под листвой в кронах деревьев: редко, только там, где снизу воздух, иногда по два на цепи. Ползунки шанса и количества цепей."),
        new Idea("Candle clusters", "Groups of candles in random colors",
            "Place groups of 1 to 4 candles in random colors on top of flat solid blocks: lit, clumped together in small groups with space between them. Sliders for group count and the share of lit candles.",
            "Свечи кучками", "Группы свечей случайных цветов",
            "Ставь группы из 1-4 свечей случайных цветов на ровные твёрдые блоки: зажжённые, маленькими кучками с пустым местом между ними. Ползунки количества групп и доли зажжённых свечей."),
        new Idea("Sculk infection", "Sculk spreading in noisy patches",
            "Spread sculk over the ground: patches shaped by noise, sculk veins around the edges and on walls, and a few sensors, catalysts and shriekers inside the patches. Sliders for patch size and the rarity of special blocks.",
            "Заражение скалком", "Скалк расползается неровными пятнами",
            "Распространи скалк по земле: пятна, форму которым задаёт шум, по краям и на стенах жилы скалка, а внутри пятен несколько сенсоров, катализаторов и крикунов. Ползунки размера пятен и редкости особых блоков."),
        new Idea("Bamboo grove", "Bamboo of different heights with leafy tops",
            "Plant a bamboo grove: stalks of random height from 4 to 12 blocks on grass, dirt and podzol, with the right leaf stage near the top, in dense clumps. Sliders for density and maximum height.",
            "Бамбуковая роща", "Бамбук разной высоты с листвой на верхушках",
            "Посади бамбуковую рощу: стебли случайной высоты от 4 до 12 блоков на траве, земле и подзоле, у верхушки с правильной стадией листьев, густыми кучками. Ползунки плотности и максимальной высоты."),
        new Idea("Swamp edge", "Mud, moss, lily pads and mangrove roots",
            "Dress a swamp shore: mud and moss near the water, lily pads on the water, mangrove roots in the shallows, and tall grass and ferns on dry land. Sliders for how wet the area is and for plant density.",
            "Берег болота", "Грязь, мох, кувшинки и корни мангров",
            "Оформи берег болота: грязь и мох у воды, кувшинки на воде, корни мангров на мелководье, а на суше высокая трава и папоротники. Ползунки влажности местности и плотности растений."),
        new Idea("Checkerboard", "A checker floor with tiles of any size",
            "Paint a checkerboard on any flat surface: two chosen blocks alternating in tiles whose size is a slider, with an option to rotate the pattern by 45 degrees.",
            "Шахматный пол", "Шахматная плитка любого размера",
            "Нарисуй шахматную доску на любой плоской поверхности: два выбираемых блока чередуются плитками, размер которых задаётся ползунком, с возможностью повернуть узор на 45 градусов."),
        new Idea("Mountain painter", "Grass, stone and snow by altitude",
            "Paint a mountain by altitude: grass and dirt at the bottom, stone with patches of gravel and andesite in the middle and snow on the peaks, with noise so the borders are not straight, and steep slopes staying bare stone. Sliders for the heights.",
            "Раскраска гор", "Трава, камень и снег в зависимости от высоты",
            "Раскрась гору по высоте: внизу трава и земля, в середине камень с пятнами гравия и андезита, на вершинах снег, границы неровные от шума, а крутые склоны остаются голым камнем. Ползунки высот."),
        new Idea("Broken walls", "Holes and missing blocks, as after a siege",
            "Damage walls: knock out random blocks and small clusters, more often near the top and the edges, leave rubble of cobblestone and gravel on the ground below. Sliders for damage and the size of holes.",
            "Разрушенные стены", "Дыры и выбитые блоки, как после осады",
            "Повреди стены: выбивай случайные блоки и небольшие группы, чаще у верха и краёв, а на земле под ними оставляй обломки из булыжника и гравия. Ползунки степени разрушений и размера дыр."),
        new Idea("Glow berries", "Cave vines with glowing berries on ceilings",
            "Hang cave vines with glow berries from cave ceilings: vines of random length, berries on part of the blocks, a moss patch where they attach. Sliders for density and length.",
            "Светоягоды", "Пещерные лианы со светящимися ягодами на потолках",
            "Развесь с потолков пещер пещерные лианы со светоягодами: лианы случайной длины, ягоды на части блоков, а там, где они крепятся, пятно мха. Ползунки плотности и длины."),
        new Idea("Beach gradient", "Wet sand, gravel and clay along the waterline",
            "Paint a beach: wet-looking mix of sand, gravel and clay next to the water that turns into clean sand further away and then into grass, with noise on the borders and a few sea pickles in the shallows.",
            "Градиент пляжа", "Мокрый песок, гравий и глина у кромки воды",
            "Нарисуй пляж: у воды смесь песка, гравия и глины, дальше чистый песок, потом трава, границы с шумом, на мелководье несколько морских огурцов."),
        new Idea("Meteor crater", "A crater with a raised rim and scorched ground (click)",
            "On click dig a meteor crater: a bowl with a raised rim, scorched ground of blackstone, coal and magma inside, and a few ore blocks in the middle. Sliders for radius and depth.",
            "Кратер от метеорита", "Кратер с приподнятым краем и обожжённой землёй (кликом)",
            "По клику выкопай кратер от метеорита: чаша с приподнятым краем, внутри обожжённая земля из чёрного камня, угля и магмы, а в центре несколько блоков руды. Ползунки радиуса и глубины."),
        new Idea("Lightning scar", "A burn scar with radial cracks",
            "Leave a lightning strike scar: a dark burnt spot with radial cracks of blackstone and magma that get thinner with distance, and a glassy fused patch in the center. Sliders for radius and number of cracks.",
            "След молнии", "Выжженное пятно с лучевыми трещинами",
            "Оставь след от удара молнии: тёмное выжженное пятно с лучевыми трещинами из чёрного камня и магмы, которые тоньше вдали от центра, а в середине оплавленное стекловидное пятно. Ползунки радиуса и количества трещин."),
        new Idea("Color gradient", "A smooth fade from one color to another",
            "Paint a smooth gradient between two chosen colors of wool (or concrete) along the chosen direction, using the intermediate colors in between with a little noise so the border is soft. Sliders for the length of the fade.",
            "Цветной градиент", "Плавный переход от одного цвета к другому",
            "Нарисуй плавный градиент между двумя выбранными цветами шерсти (или бетона) в выбранном направлении, используя промежуточные цвета и немного шума, чтобы граница была мягкой. Ползунок длины перехода."));

    private Ideas() {
    }

    /** n different random ideas. */
    static List<Idea> random(int n) {
        List<Idea> copy = new ArrayList<>(ALL);
        Collections.shuffle(copy, new Random());
        return new ArrayList<>(copy.subList(0, Math.min(n, copy.size())));
    }

    static int size() {
        return ALL.size();
    }

    static List<Idea> all() {
        return ALL;
    }
}
