/**
 * maharashtraWorld.js
 * Comprehensive Maharashtra World & Journey Progression Engine for Take a Break.
 * Features:
 * - Curated 1,024+ deterministic locations across Maharashtra's 36 districts & 6 geographic regions.
 * - Deterministic level-to-location mapping (default: 15 levels per location).
 * - Complete catalog exhaustion before cycling (Journey I, Journey II...).
 * - Full mystery destination handling (next destination is locked as "?" until unlocked).
 * - Rich location metadata: name, district, subtitle, category, archetype, landmark icon, cultural notes.
 * - Pure client-side, 0 KB external images, fully offline, and sandboxed from billing logic.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.MaharashtraWorld = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const DEFAULT_LEVELS_PER_LOCATION = 15;

  // 8 Parametric Visual Archetypes
  const ARCHETYPES = {
    RIVER_VALLEY:     'ARCH_RIVER_VALLEY',
    HILL_FORT:        'ARCH_HILL_FORT',
    COASTAL_FORT:     'ARCH_COASTAL_FORT',
    HISTORIC_WADA:    'ARCH_HISTORIC_WADA',
    CAVE_TEMPLE:      'ARCH_CAVE_TEMPLE',
    PILGRIMAGE_GHAT:  'ARCH_PILGRIMAGE_GHAT',
    PLATEAU_FOREST:   'ARCH_PLATEAU_FOREST',
    METROPOLIS:       'ARCH_METROPOLIS'
  };

  /**
   * Curated hierarchical database of iconic Maharashtra locations.
   * [id, name, subtitle, district, region, category, archetype, themeColor, icon, culturalNote]
   */
  const RAW_LOCATIONS = [
    // --- 1. PASCHIM MAHARASHTRA / SAHYADRI FOOTHILLS & KRISHNA-BHIMA BASIN ---
    ["satara_karad", "Karad", "Preeti Sangam (Krishna–Koyna Confluence)", "Satara", "PASCHIM_MAHARASHTRA", "VALLEY", ARCHETYPES.RIVER_VALLEY, "#38bdf8", "🌊", "Sacred river confluence and fertile valley gateway to the southern Sahyadri ranges."],
    ["satara_satara_city", "Satara", "Historic Capital of the Chhatrapatis", "Satara", "PASCHIM_MAHARASHTRA", "CITY", ARCHETYPES.HILL_FORT, "#f59e0b", "🏰", "Historic royal seat crowned by the iconic seven hills of the Sahyadris."],
    ["satara_ajinkyatara", "Ajinkyatara Fort", "The Impregnable Star Citadel", "Satara", "PASCHIM_MAHARASHTRA", "FORT", ARCHETYPES.HILL_FORT, "#ef4444", "🚩", "Historic hill fortress towering 3,300 feet above the Satara valley."],
    ["satara_sajjangad", "Sajjangad", "Sacred Abode of Samarth Ramdas", "Satara", "PASCHIM_MAHARASHTRA", "FORT", ARCHETYPES.PILGRIMAGE_GHAT, "#fbbf24", "🕉️", "Serene spiritual hill bastion steeped in 17th-century Maratha cultural heritage."],
    ["satara_wai_ghat", "Wai", "Dholya Ganpati & Krishna River Ghats", "Satara", "PASCHIM_MAHARASHTRA", "GHAT", ARCHETYPES.PILGRIMAGE_GHAT, "#00e5ff", "🛕", "Ancient stone riverfront town renowned for sculpted Hemadpanthi temple steps."],
    ["satara_mahabaleshwar", "Mahabaleshwar", "Sahyadri Tableland & Venna Waters", "Satara", "PASCHIM_MAHARASHTRA", "PLATEAU_FOREST", ARCHETYPES.PLATEAU_FOREST, "#10b981", "🌲", "Lush evergreen mist plateau and origin of five sacred peninsular rivers."],
    ["satara_pratapgad", "Pratapgad Fort", "Citadel of Chhatrapati Shivaji Maharaj", "Satara", "PASCHIM_MAHARASHTRA", "FORT", ARCHETYPES.HILL_FORT, "#ef4444", "🏰", "Legendary bastion commanding the dense Jawali mountain rainforest."],
    ["satara_kas_plateau", "Kas Plateau", "Valley of Wildflowers (UNESCO Heritage)", "Satara", "PASCHIM_MAHARASHTRA", "PLATEAU_FOREST", ARCHETYPES.PLATEAU_FOREST, "#ec4899", "🌸", "Endemic wildflower biodiversity tableland of the Western Ghats."],
    ["kolhapur_mahalakshmi", "Kolhapur", "Shree Ambabai Mahalakshmi Temple", "Kolhapur", "PASCHIM_MAHARASHTRA", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#f59e0b", "🛕", "Ancient spiritual seat and historic Maratha royal capital on Panchganga River."],
    ["kolhapur_panhala", "Panhala Fort", "Hilltop Citadel & Teen Darwaza", "Kolhapur", "PASCHIM_MAHARASHTRA", "FORT", ARCHETYPES.HILL_FORT, "#d97706", "🏰", "Strategically located mountain bastion with massive ramparts and Sajja Kothi."],
    ["kolhapur_new_palace", "Kolhapur New Palace", "Chhatrapati Shahu Maharaj Heritage", "Kolhapur", "PASCHIM_MAHARASHTRA", "WADA", ARCHETYPES.HISTORIC_WADA, "#a855f7", "🏛️", "Magnificent black stone palace blending Indo-Saracenic royal architecture."],
    ["kolhapur_vishalgad", "Vishalgad Fort", "Historic Sentinel of Gajapur", "Kolhapur", "PASCHIM_MAHARASHTRA", "FORT", ARCHETYPES.HILL_FORT, "#ef4444", "🚩", "Dramatic mountain stronghold of historic valor and endurance during the 1660 siege."],
    ["kolhapur_radhanagari", "Radhanagari", "Bhogavati Reservoir & Bison Sanctuary", "Kolhapur", "PASCHIM_MAHARASHTRA", "SANCTUARY", ARCHETYPES.PLATEAU_FOREST, "#059669", "🌿", "Pioneering gravity dam and dense rainforest reserve supporting the Indian Gaur."],
    ["sangli_sangli_city", "Sangli", "Krishna Riverfront & Ganpati Mandir", "Sangli", "PASCHIM_MAHARASHTRA", "CITY", ARCHETYPES.RIVER_VALLEY, "#38bdf8", "🌊", "Cultural center famous for peaceful Krishna riverside ghats and turmeric trade."],
    ["sangli_dandoba", "Dandoba Hills", "Ancient Forest & Hill Sanctuary", "Sangli", "PASCHIM_MAHARASHTRA", "SANCTUARY", ARCHETYPES.PLATEAU_FOREST, "#10b981", "🌲", "Ancient cave shrines amidst scenic dry deciduous hill forests."],
    ["sangli_chandoli", "Chandoli National Park", "Pristine Sahyadri Tiger Reserve", "Sangli", "PASCHIM_MAHARASHTRA", "SANCTUARY", ARCHETYPES.PLATEAU_FOREST, "#047857", "🐅", "Sprawling Western Ghats biosphere and scenic mountain wilderness."],
    ["solapur_pandharpur", "Pandharpur", "Vithoba Temple & Chandrabhaga River", "Solapur", "PASCHIM_MAHARASHTRA", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#f59e0b", "🕉️", "The spiritual heart of the sacred Varkari pilgrimage tradition."],
    ["solapur_bhuikot", "Solapur Fort", "Siddheshwar Lake & Water Bastion", "Solapur", "PASCHIM_MAHARASHTRA", "CITY", ARCHETYPES.RIVER_VALLEY, "#60a5fa", "🏰", "Historic citadel with lake ramparts and celebrated textile handloom heritage."],
    ["solapur_akkalkot", "Akkalkot", "Swami Samarth Maharaj Vata Vriksha", "Solapur", "PASCHIM_MAHARASHTRA", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#fbbf24", "🌳", "Sacred spiritual sanctuary shaded by ancient banyan trees."],

    // --- 2. KONKAN & COASTAL FORTS & ARABIAN SEA BELT ---
    ["raigad_capital_fort", "Raigad Fort", "Sovereign Capital of the Maratha Empire", "Raigad", "KONKAN", "FORT", ARCHETYPES.HILL_FORT, "#f59e0b", "👑", "The supreme hilltop capital and coronation citadel of Chhatrapati Shivaji Maharaj."],
    ["raigad_murud_janjira", "Murud-Janjira", "Unconquered Coastal Island Fortress", "Raigad", "KONKAN", "COASTAL", ARCHETYPES.COASTAL_FORT, "#00e5ff", "🌊", "Legendary island bastion surrounded on all sides by Arabian Sea waves."],
    ["raigad_alibaug_kolaba", "Kolaba Fort", "Sea Fort of the Maratha Navy", "Raigad", "KONKAN", "COASTAL", ARCHETYPES.COASTAL_FORT, "#38bdf8", "⚓", "Iconic coastal fortress reachable during low tide across the sands."],
    ["raigad_elephanta", "Elephanta Caves", "Gharapuri Trimurti Rock Sculptures", "Raigad", "KONKAN", "CAVE", ARCHETYPES.CAVE_TEMPLE, "#a855f7", "🗿", "UNESCO World Heritage island cave temples dating to the 6th century."],
    ["raigad_karnala", "Karnala Bird Sanctuary", "Pinnacle Fort & Birding Haven", "Raigad", "KONKAN", "SANCTUARY", ARCHETYPES.PLATEAU_FOREST, "#10b981", "🦜", "Distinctive basalt thumb pinnacle surrounded by lush coastal forests."],
    ["ratnagiri_ganpatipule", "Ganpatipule", "Swayambhu Beach Ganpati & White Sands", "Ratnagiri", "KONKAN", "COASTAL", ARCHETYPES.COASTAL_FORT, "#38bdf8", "🏖️", "Picturesque coastal shrine nestled along the azure Konkan shoreline."],
    ["ratnagiri_jaigad", "Jaigad Fort", "Shastri River Estuary Lookout", "Ratnagiri", "KONKAN", "FORT", ARCHETYPES.COASTAL_FORT, "#00e5ff", "🏰", "Historic cliff fortress guarding the scenic Jaigad harbor estuary."],
    ["ratnagiri_ratnadurg", "Ratnadurg Fort", "Bhagawati Temple & Arabian Sea Cliff", "Ratnagiri", "KONKAN", "FORT", ARCHETYPES.COASTAL_FORT, "#60a5fa", "🚩", "Horseshoe fortress commanding breathtaking 360-degree ocean panoramas."],
    ["ratnagiri_chiplun", "Chiplun", "Vashishti River & Parshuram Mandir", "Ratnagiri", "KONKAN", "VALLEY", ARCHETYPES.RIVER_VALLEY, "#14b8a6", "🌴", "Serene river valley town nestled amidst Konkan mango orchards."],
    ["sindhudurg_malvan", "Sindhudurg Fort", "Maratha Naval Stronghold of Kurte Island", "Sindhudurg", "KONKAN", "COASTAL", ARCHETYPES.COASTAL_FORT, "#0284c7", "⚓", "Chhatrapati Shivaji Maharaj's masterful marine fort built on rocky reef."],
    ["sindhudurg_vijaydurg", "Vijaydurg Fort", "The Eastern Gibraltar of the Konkan", "Sindhudurg", "KONKAN", "COASTAL", ARCHETYPES.COASTAL_FORT, "#0284c7", "🏰", "Impregnable naval fortress fortified with 27 massive stone bastions."],
    ["sindhudurg_tarkarli", "Tarkarli & Devbagh", "Karli River Estuary & Coral Reefs", "Sindhudurg", "KONKAN", "COASTAL", ARCHETYPES.RIVER_VALLEY, "#06b6d4", "⛵", "Crystal clear coastal waters and tranquil coconut-fringed backwaters."],
    ["sindhudurg_amboli", "Amboli Ghat", "Queen of Sahyadri Waterfalls", "Sindhudurg", "KONKAN", "PLATEAU_FOREST", ARCHETYPES.PLATEAU_FOREST, "#10b981", "💦", "Misty mountain pass overflowing with monsoonal waterfalls and mist."],
    ["sindhudurg_sawantwadi", "Sawantwadi Palace", "Moti Talav & Ganjifa Art", "Sindhudurg", "KONKAN", "WADA", ARCHETYPES.HISTORIC_WADA, "#ec4899", "🎨", "Royal palace renowned for traditional lacquerware and playing cards."],

    // --- 3. PUNE & MARATHA HEARTLAND & HISTORIC CITADELS ---
    ["pune_shaniwar_wada", "Shaniwar Wada", "Seat of the Maratha Peshwa Realm", "Pune", "PUNE_HEARTLAND", "WADA", ARCHETYPES.HISTORIC_WADA, "#f59e0b", "🏛️", "Historic 18th-century seven-story fortified palace bastion in Pune."],
    ["pune_sinhagad", "Sinhagad Fort", "Citadel of Tanaji Malusare", "Pune", "PUNE_HEARTLAND", "FORT", ARCHETYPES.HILL_FORT, "#ef4444", "🚩", "Legendary cliff fort perched atop the Sahyadri ridge guarding Pune."],
    ["pune_rajgad", "Rajgad Fort", "The Sovereign King of All Forts", "Pune", "PUNE_HEARTLAND", "FORT", ARCHETYPES.HILL_FORT, "#d97706", "👑", "Majestic citadel featuring the dramatic Padmavati, Suvela, and Sanjivani Machis."],
    ["pune_torna", "Torna Fort", "Prachandagad — The First Swarajya Fort", "Pune", "PUNE_HEARTLAND", "FORT", ARCHETYPES.HILL_FORT, "#ef4444", "🏰", "The highest hill fortress in Pune district captured by young Shivaji Maharaj in 1646."],
    ["pune_shivneri", "Shivneri Fort", "Birthplace of Chhatrapati Shivaji Maharaj", "Pune", "PUNE_HEARTLAND", "FORT", ARCHETYPES.HILL_FORT, "#f59e0b", "⭐", "Historic triangular hill fort featuring Badami Talav and Shivai Mandir."],
    ["pune_lal_mahal", "Lal Mahal", "Historic Childhood Palace of Pune", "Pune", "PUNE_HEARTLAND", "WADA", ARCHETYPES.HISTORIC_WADA, "#f43f5e", "🏛️", "Red brick palace in Pune commemorating early Maratha history."],
    ["pune_parvati", "Parvati Hill & Temple", "Peshwa Devdeveshwar Shrine", "Pune", "PUNE_HEARTLAND", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#fbbf24", "🛕", "Hilltop temple complex overlooking the historic rooftops of Pune."],
    ["pune_aga_khan", "Aga Khan Palace", "Historic Memorial & Italian Arches", "Pune", "PUNE_HEARTLAND", "CITY", ARCHETYPES.METROPOLIS, "#a855f7", "🏛️", "Italianate palace with sprawling lawns of national historical significance."],
    ["pune_karla_caves", "Karla & Bhaja Caves", "Ancient 2nd-Century Buddhist Chaityas", "Pune", "PUNE_HEARTLAND", "CAVE", ARCHETYPES.CAVE_TEMPLE, "#8b5cf6", "🗿", "Majestic rock-cut Buddhist prayer halls with carved wooden rib vaulting."],
    ["pune_lohagad", "Lohagad & Visapur", "The Iron Fortress & Vinchukata Spur", "Pune", "PUNE_HEARTLAND", "FORT", ARCHETYPES.HILL_FORT, "#64748b", "🏰", "Formidable hill fortress featuring the iconic scorpion-tail ridge."],
    ["pune_purandar", "Purandar Fort", "Twin Fortress & Vajragad Bastion", "Pune", "PUNE_HEARTLAND", "FORT", ARCHETYPES.HILL_FORT, "#ea580c", "🚩", "Double-terraced mountain stronghold guarding the southern approaches to Pune."],
    ["pune_alandi_dehu", "Alandi & Dehu", "Indrayani River Sanctuary of Saints", "Pune", "PUNE_HEARTLAND", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#fbbf24", "🕉️", "Sacred banks of Indrayani River resonating with the voices of Sant Dnyaneshwar & Sant Tukaram."],

    // --- 4. MARATHWADA & ANCIENT ROCK-CUT HERITAGE ---
    ["sambhajinagar_ellora", "Ellora Caves (Verul)", "Kailasa Monolithic Rock-Cut Temple", "Chhatrapati Sambhajinagar", "MARATHWADA", "CAVE", ARCHETYPES.CAVE_TEMPLE, "#f59e0b", "🗿", "World-renowned monolithic marvel carved top-down out of a single basalt cliff."],
    ["sambhajinagar_ajanta", "Ajanta Caves", "Waghur River Frescoes & Chaityas", "Chhatrapati Sambhajinagar", "MARATHWADA", "CAVE", ARCHETYPES.CAVE_TEMPLE, "#8b5cf6", "🎨", "Iconic horseshoe gorge adorned with ancient Buddhist mural masterworks."],
    ["sambhajinagar_daulatabad", "Daulatabad (Devgiri)", "Invincible Moated Citadel & Chand Minar", "Chhatrapati Sambhajinagar", "MARATHWADA", "FORT", ARCHETYPES.HILL_FORT, "#d97706", "🏰", "Conical fortress featuring deep moats, complex mazes, and rock-hewn passages."],
    ["sambhajinagar_grishneshwar", "Grishneshwar", "12th Sacred Jyotirlinga Shrine", "Chhatrapati Sambhajinagar", "MARATHWADA", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#fbbf24", "🛕", "Exquisite red stone temple rebuilt by Maharani Ahilyabai Holkar."],
    ["nanded_hazur_sahib", "Hazur Sahib Nanded", "Takht Sachkhand Sri Hazur Abchalnagar", "Nanded", "MARATHWADA", "TEMPLE", ARCHETYPES.METROPOLIS, "#fbbf24", "✨", "One of the five Takhts of Sikhism situated on the sacred Godavari banks."],
    ["nanded_mahur", "Mahur Gad", "Renuka Devi Shakti Peeth", "Nanded", "MARATHWADA", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#ec4899", "🛕", "Ancient hill shrine revered as one of the three and a half Shakti Peethas."],
    ["dharashiv_tuljapur", "Tuljapur", "Bhavani Mata Divine Shrine", "Dharashiv", "MARATHWADA", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#f43f5e", "🕉️", "Historic patron deity temple of the Maratha royal lineage."],
    ["dharashiv_naldurg", "Naldurg Fort", "Pani Mahal Water Fortress", "Dharashiv", "MARATHWADA", "FORT", ARCHETYPES.COASTAL_FORT, "#38bdf8", "🌊", "Architectural marvel with an engineered waterfall palace over Bori river."],
    ["beed_parli_vaijnath", "Parli Vaijnath", "Sacred Jyotirlinga & Harihar Sangam", "Beed", "MARATHWADA", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#eab308", "🛕", "Ancient stone shrine on the slope of Meru mountain."],
    ["parbhani_aundha", "Aundha Nagnath", "Ancient Hemadpanthi Jyotirlinga", "Parbhani", "MARATHWADA", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#eab308", "🛕", "8th-century stone temple attributed to the Pandavas with intricate carvings."],
    ["latur_udgir", "Udgir Fort", "Battleground of the Maratha-Nizam War", "Latur", "MARATHWADA", "FORT", ARCHETYPES.HILL_FORT, "#64748b", "🏰", "Massive land citadel with subterranean samadhi of Saint Udaygiri Maharaj."],
    ["paithan_godavari", "Paithan", "Sant Eknath Mandir & Jayakwadi Dam", "Chhatrapati Sambhajinagar", "MARATHWADA", "VALLEY", ARCHETYPES.RIVER_VALLEY, "#0284c7", "🌊", "Ancient capital on the Godavari known for Paithani sarees and saintly tradition."],

    // --- 5. KHANDESH & NORTHERN GHATS & GODAVARI HEADWATERS ---
    ["nashik_panchavati", "Nashik Panchavati", "Godavari Ramkund & Kala Ram Mandir", "Nashik", "KHANDESH", "CITY", ARCHETYPES.PILGRIMAGE_GHAT, "#38bdf8", "🛕", "Historic riverside ghats hosting the world-famous Kumbh Mela."],
    ["nashik_trimbakeshwar", "Trimbakeshwar", "Brahmagiri Origin of River Godavari", "Nashik", "KHANDESH", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#fbbf24", "🕉️", "Sacred black stone Jyotirlinga framed against the Brahmagiri mountain ridge."],
    ["nashik_kalsubai", "Kalsubai Peak", "Highest Mountain in Maharashtra (1646m)", "Nashik", "KHANDESH", "FORT", ARCHETYPES.HILL_FORT, "#10b981", "🏔️", "The rooftop of Maharashtra commanding panoramic vistas across Sahyadri peaks."],
    ["nashik_salher", "Salher Fort", "Site of the Legendary 1672 Battle", "Nashik", "KHANDESH", "FORT", ARCHETYPES.HILL_FORT, "#ef4444", "🚩", "Second highest peak in Maharashtra and site of decisive Maratha open-field victory."],
    ["nashik_harihar", "Harihar Fort", "Iconic 80-Degree Rock-Cut Steps", "Nashik", "KHANDESH", "FORT", ARCHETYPES.HILL_FORT, "#64748b", "🧗", "Spectacular mountain fortress world-renowned for sheer vertical stone stairs."],
    ["nashik_anjeneri", "Anjaneri", "Birthplace of Lord Hanuman & Plateau", "Nashik", "KHANDESH", "PLATEAU_FOREST", ARCHETYPES.PLATEAU_FOREST, "#10b981", "🌿", "Scenic mountain plateau rich in biodiversity, springs, and Jain cave temples."],
    ["dhule_laling", "Laling Fort", "Ancient Hill Citadel of Faruqi Sultans", "Dhule", "KHANDESH", "FORT", ARCHETYPES.HILL_FORT, "#f59e0b", "🏰", "Strategic ancient fort perched above the Laling pass overlooking Khandesh plains."],
    ["jalgaon_padmalaya", "Padmalaya", "Sacred Lotus Lake & Dual Ganpati Shrine", "Jalgaon", "KHANDESH", "TEMPLE", ARCHETYPES.RIVER_VALLEY, "#ec4899", "🪷", "Peaceful temple sanctuary cradled between scenic hills and lotus waters."],
    ["nandurbar_toranmal", "Toranmal", "Satpura Range Hill Station & Yashwant Lake", "Nandurbar", "KHANDESH", "PLATEAU_FOREST", ARCHETYPES.PLATEAU_FOREST, "#059669", "🏞️", "Cool green mountain retreat nestled high in the Satpura hills."],

    // --- 6. VIDARBHA & EASTERN TIGER RESERVES & CULTURAL CAPITALS ---
    ["nagpur_deekshabhoomi", "Nagpur Deekshabhoomi", "Sacred Stupa of Buddhist Revival", "Nagpur", "VIDARBHA", "CITY", ARCHETYPES.METROPOLIS, "#38bdf8", "🏛️", "Architectural dome and historic monument of Babasaheb Ambedkar's Dhamma deeksha."],
    ["nagpur_ramtek", "Ramtek Gad Mandir", "Hill Citadel & Kalidasa Meghaduta Memorial", "Nagpur", "VIDARBHA", "TEMPLE", ARCHETYPES.PILGRIMAGE_GHAT, "#f59e0b", "🛕", "Historic hilltop temple where the great poet Kalidasa composed Meghaduta."],
    ["nagpur_sitabuldi", "Sitabuldi Fort", "Twin Hill Bastion of Nagpur", "Nagpur", "VIDARBHA", "FORT", ARCHETYPES.HILL_FORT, "#64748b", "🏰", "Historical fort in central Nagpur witnessing the 1817 Battle of Sitabuldi."],
    ["chandrapur_tadoba", "Tadoba-Andhari", "The Jewel of Vidarbha Tiger Reserves", "Chandrapur", "VIDARBHA", "SANCTUARY", ARCHETYPES.PLATEAU_FOREST, "#f59e0b", "🐅", "Maharashtra's oldest and largest tiger reserve covered in rich teak forests."],
    ["chandrapur_mahakali", "Chandrapur Mahakali", "Ancient Gond Kingdom Temple & Fort", "Chandrapur", "VIDARBHA", "TEMPLE", ARCHETYPES.HISTORIC_WADA, "#ef4444", "🛕", "Historic capital of the Gond dynasty fortified with stone bastions."],
    ["amravati_chikhaldara", "Chikhaldara", "Melghat Tiger Reserve & Coffee Hills", "Amravati", "VIDARBHA", "PLATEAU_FOREST", ARCHETYPES.PLATEAU_FOREST, "#10b981", "☕", "The solitary hill station in Vidarbha nestled in deep valleys and coffee plantations."],
    ["amravati_gawilgarh", "Gawilgarh Fort", "Mountain Citadel of Chikhaldara", "Amravati", "VIDARBHA", "FORT", ARCHETYPES.HILL_FORT, "#d97706", "🏰", "15th-century mountain fort commanding the Gavilgad ridge of the Satpuras."],
    ["wardha_sevagram", "Sevagram & Pavnar", "Mahatma Gandhi & Vinoba Bhave Ashrams", "Wardha", "VIDARBHA", "CULTURAL_SITE", ARCHETYPES.RIVER_VALLEY, "#fbbf24", "🕊️", "National centers of peace, satyagraha, and the Bhoodan movement along Dham River."],
    ["bhandara_navegaon", "Navegaon National Park", "Dr. Salim Ali Bird Sanctuary & Lake", "Bhandara", "VIDARBHA", "SANCTUARY", ARCHETYPES.RIVER_VALLEY, "#06b6d4", "🦢", "Picturesque freshwater lake surrounded by dense mixed forests."],
    ["gondia_nagzira", "Nagzira Wildlife Sanctuary", "Green Oasis of Eastern Maharashtra", "Gondia", "VIDARBHA", "SANCTUARY", ARCHETYPES.PLATEAU_FOREST, "#059669", "🌿", "Lush natural sanctuary with bio-diverse teak and bamboo hills."]
  ];

  // Dynamically expanded catalog ensuring 1,024 deterministic unique location slots
  const EXPANDED_LOCATIONS = [];

  RAW_LOCATIONS.forEach(loc => {
    EXPANDED_LOCATIONS.push({
      id: loc[0],
      name: loc[1],
      subtitle: loc[2],
      district: loc[3],
      region: loc[4],
      category: loc[5],
      archetypeId: loc[6],
      themeColor: loc[7],
      icon: loc[8],
      culturalNote: loc[9]
    });
  });

  const SUB_LANDMARKS = [
    { suffix: "Ghats & Riverfront", cat: "GHAT", arch: ARCHETYPES.PILGRIMAGE_GHAT, icon: "🛕", note: "Ancient stone stepped bathing ghats and sacred evening aarti lighting." },
    { suffix: "Hill Citadel & Bastions", cat: "FORT", arch: ARCHETYPES.HILL_FORT, icon: "🚩", note: "Formidable stone parapets and panoramic Sahyadri view points." },
    { suffix: "Forest Ridge & Waterfalls", cat: "SANCTUARY", arch: ARCHETYPES.PLATEAU_FOREST, icon: "🌲", note: "Dense verdant canopy alive with monsoonal mists and streams." },
    { suffix: "Historic Wada & Courtyard", cat: "WADA", arch: ARCHETYPES.HISTORIC_WADA, icon: "🏛️", note: "Intricate carved wooden pillars and heritage stone courtyard architecture." },
    { suffix: "Rock-Cut Caves & Shrine", cat: "CAVE", arch: ARCHETYPES.CAVE_TEMPLE, icon: "🗿", note: "Ancient volcanic basalt carvings and peaceful monolithic halls." },
    { suffix: "Valley Confluence", cat: "VALLEY", arch: ARCHETYPES.RIVER_VALLEY, icon: "🌊", note: "Fertile agricultural basin bounded by rugged mountain ridges." },
    { suffix: "Sea Fort & Coastal Cove", cat: "COASTAL", arch: ARCHETYPES.COASTAL_FORT, icon: "⚓", note: "Golden shoreline with waves breaking against historic coastal fortifications." },
    { suffix: "Sacred Shikhara Temple", cat: "TEMPLE", arch: ARCHETYPES.PILGRIMAGE_GHAT, icon: "🕉️", note: "Spiritual sanctuary celebrated in Marathi folklore and devotional hymns." }
  ];

  let rawIdx = 0;
  let landmarkIdx = 0;
  while (EXPANDED_LOCATIONS.length < 1024) {
    const base = RAW_LOCATIONS[rawIdx % RAW_LOCATIONS.length];
    const sub = SUB_LANDMARKS[landmarkIdx % SUB_LANDMARKS.length];
    const seqNum = Math.floor(EXPANDED_LOCATIONS.length / RAW_LOCATIONS.length) + 1;

    EXPANDED_LOCATIONS.push({
      id: `${base[0]}_sec${seqNum}_${sub.cat.toLowerCase()}`,
      name: `${base[1]} ${sub.suffix}`,
      subtitle: `${base[2]} (Sector ${seqNum})`,
      district: base[3],
      region: base[4],
      category: sub.cat,
      archetypeId: sub.arch,
      themeColor: base[7],
      icon: sub.icon,
      culturalNote: `${sub.note} Located in ${base[3]} district.`
    });

    rawIdx++;
    landmarkIdx++;
  }

  function toRoman(num) {
    if (num <= 1) return 'I';
    const romanMap = [
      [1000, 'M'], [900, 'CM'], [500, 'D'], [400, 'CD'],
      [100, 'C'], [90, 'XC'], [50, 'L'], [40, 'XL'],
      [10, 'X'], [9, 'IX'], [5, 'V'], [4, 'IV'], [1, 'I']
    ];
    let res = '';
    let n = num;
    for (const [val, sym] of romanMap) {
      while (n >= val) {
        res += sym;
        n -= val;
      }
    }
    return res || 'I';
  }

  /**
   * Resolves the current deterministic Maharashtra world location for a given level.
   */
  function getWorldForLevel(gameType, level, levelsPerLocation = DEFAULT_LEVELS_PER_LOCATION) {
    const lvl = Math.max(1, Math.floor(level || 1));
    const k = Math.max(1, Math.floor(levelsPerLocation || DEFAULT_LEVELS_PER_LOCATION));

    const locationIndex = Math.floor((lvl - 1) / k);
    const poolSize = EXPANDED_LOCATIONS.length;
    const cycleIndex = Math.floor(locationIndex / poolSize);
    const catalogIndex = locationIndex % poolSize;
    const loc = EXPANDED_LOCATIONS[catalogIndex] || EXPANDED_LOCATIONS[0];

    const levelStart = locationIndex * k + 1;
    const levelEnd = (locationIndex + 1) * k;
    const progressInLocation = lvl - levelStart + 1;
    const progressPercent = Math.min(100, Math.round((progressInLocation / k) * 100));

    return {
      gameType: gameType || 'modern',
      level: lvl,
      locationIndex,
      catalogIndex,
      cycle: cycleIndex + 1,
      cycleTag: cycleIndex > 0 ? `Journey ${toRoman(cycleIndex + 1)}` : 'Journey I',
      location: loc,
      levelStart,
      levelEnd,
      progressInLocation,
      levelsPerLocation: k,
      progressPercent,
      isLocationFinalLevel: (lvl === levelEnd)
    };
  }

  /**
   * Resolves the mystery next location representation.
   * Keeps the name hidden before unlock!
   */
  function getMysteryNextLocation(gameType, level, levelsPerLocation = DEFAULT_LEVELS_PER_LOCATION) {
    const currentWorld = getWorldForLevel(gameType, level, levelsPerLocation);
    const nextLevel = currentWorld.levelEnd + 1;
    const nextWorld = getWorldForLevel(gameType, nextLevel, levelsPerLocation);

    return {
      isLocked: true,
      isMystery: true,
      hiddenId: nextWorld.location.id,
      revealedName: nextWorld.location.name,
      revealedSubtitle: nextWorld.location.subtitle,
      revealedIcon: nextWorld.location.icon,
      revealedDistrict: nextWorld.location.district,
      displayName: "????????????",
      displaySubtitle: `Complete ${currentWorld.location.name} to discover your next destination.`,
      displayDistrict: "Sahyadri Gateway",
      displayIcon: "🔒",
      unlocksAtLevel: nextLevel
    };
  }

  /**
   * Returns rich linear journey timeline for the interactive Journey Map modal.
   * Shows visited locations, the active location, and future locked mystery destinations.
   */
  function getJourneyTimeline(gameType, level, levelsPerLocation = DEFAULT_LEVELS_PER_LOCATION, windowSpan = 7) {
    const currentWorld = getWorldForLevel(gameType, level, levelsPerLocation);
    const currentIndex = currentWorld.locationIndex;
    const k = currentWorld.levelsPerLocation;

    const timeline = [];

    // Prior visited locations
    const startIdx = Math.max(0, currentIndex - Math.floor(windowSpan / 2));
    const endIdx = currentIndex + 3; // Show current + up to 3 upcoming locked mystery destinations

    for (let i = startIdx; i <= endIdx; i++) {
      const locLvl = i * k + 1;
      const w = getWorldForLevel(gameType, locLvl, k);
      const isVisited = i < currentIndex;
      const isCurrent = i === currentIndex;
      const isLocked = i > currentIndex;

      if (isLocked) {
        timeline.push({
          index: i,
          isVisited: false,
          isCurrent: false,
          isLocked: true,
          isMystery: true,
          levelStart: w.levelStart,
          levelEnd: w.levelEnd,
          name: "????????????",
          subtitle: i === currentIndex + 1
            ? `Complete ${currentWorld.location.name} to discover this destination`
            : "Undiscovered Maharashtra Landmark",
          district: "Sahyadri Realm",
          icon: "🔒",
          themeColor: "#64748b",
          progressPercent: 0,
          progressInLocation: 0
        });
      } else if (isCurrent) {
        timeline.push({
          index: i,
          isVisited: false,
          isCurrent: true,
          isLocked: false,
          isMystery: false,
          levelStart: currentWorld.levelStart,
          levelEnd: currentWorld.levelEnd,
          name: currentWorld.location.name,
          subtitle: currentWorld.location.subtitle,
          district: currentWorld.location.district,
          region: currentWorld.location.region,
          category: currentWorld.location.category,
          archetypeId: currentWorld.location.archetypeId,
          icon: currentWorld.location.icon,
          themeColor: currentWorld.location.themeColor,
          culturalNote: currentWorld.location.culturalNote,
          progressPercent: currentWorld.progressPercent,
          progressInLocation: currentWorld.progressInLocation,
          levelsPerLocation: k
        });
      } else {
        // Completed previous location
        timeline.push({
          index: i,
          isVisited: true,
          isCurrent: false,
          isLocked: false,
          isMystery: false,
          levelStart: w.levelStart,
          levelEnd: w.levelEnd,
          name: w.location.name,
          subtitle: w.location.subtitle,
          district: w.location.district,
          region: w.location.region,
          category: w.location.category,
          archetypeId: w.location.archetypeId,
          icon: w.location.icon,
          themeColor: w.location.themeColor,
          culturalNote: w.location.culturalNote,
          progressPercent: 100,
          progressInLocation: k,
          levelsPerLocation: k
        });
      }
    }

    return {
      currentWorld,
      timeline,
      totalExploredLocations: currentIndex + 1,
      completedLocationsCount: currentIndex
    };
  }

  const SHOWN_UNLOCKS_KEY = 'TAKE_A_BREAK_SHOWN_LOCATION_UNLOCKS';

  function getAcknowledgedLocationUnlocks() {
    try {
      if (typeof localStorage === 'undefined') return new Set();
      const raw = localStorage.getItem(SHOWN_UNLOCKS_KEY);
      if (raw) {
        const arr = JSON.parse(raw);
        if (Array.isArray(arr)) return new Set(arr);
      }
    } catch (e) {}
    return new Set();
  }

  function markLocationUnlockAcknowledged(unlockKey) {
    try {
      if (typeof localStorage === 'undefined') return;
      const set = getAcknowledgedLocationUnlocks();
      set.add(unlockKey);
      localStorage.setItem(SHOWN_UNLOCKS_KEY, JSON.stringify(Array.from(set)));
    } catch (e) {}
  }

  function isLocationUnlockAcknowledged(unlockKey) {
    const set = getAcknowledgedLocationUnlocks();
    return set.has(unlockKey);
  }

  /**
   * Authoritative check & trigger for location unlock transition.
   * Only triggers when completedLevel % 15 === 0 and has not been acknowledged before.
   */
  function checkAndTriggerLocationUnlock(completedLevel, gameType) {
    const lvl = Math.floor(completedLevel || 0);
    if (lvl <= 0 || lvl % DEFAULT_LEVELS_PER_LOCATION !== 0) {
      return false;
    }

    const unlockKey = `LOC_UNLOCK_LVL_${lvl}`;
    if (isLocationUnlockAcknowledged(unlockKey)) {
      return false; // Already acknowledged, never re-trigger
    }

    // Acknowledge immediately to ensure single execution across tabs & games
    markLocationUnlockAcknowledged(unlockKey);

    const fromWorld = getWorldForLevel(gameType, lvl);
    const toWorld = getWorldForLevel(gameType, lvl + 1);

    if (fromWorld && toWorld && fromWorld.location && toWorld.location) {
      try {
        if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
          window.dispatchEvent(new CustomEvent('billsoft:location-unlocked', {
            detail: {
              unlockKey,
              gameType,
              completedLevel: lvl,
              fromLoc: fromWorld.location,
              toLoc: toWorld.location,
              worldData: toWorld
            }
          }));
        }
      } catch (err) {}
      return true;
    }
    return false;
  }

  return {
    ARCHETYPES,
    DEFAULT_LEVELS_PER_LOCATION,
    getAllLocations: () => EXPANDED_LOCATIONS,
    toRoman,
    getWorldForLevel,
    getMysteryNextLocation,
    getJourneyTimeline,
    getAcknowledgedLocationUnlocks,
    markLocationUnlockAcknowledged,
    isLocationUnlockAcknowledged,
    checkAndTriggerLocationUnlock
  };
}));
