package io.github.remdee13.pavlovtv.data

/** Groups of the topic list in Settings → News. */
enum class TopicGroup { TECH, SCIENCE, MONEY, SPORT, CULTURE, GAMES, AUTO, HEALTH, LIFE, WORLD }

/** A ready-made topic; its name in the app language is also the search words. */
class TopicSuggestion(val group: TopicGroup, private val names: List<String>) {
    fun name(language: String): String = names[LANGUAGES.indexOf(language).takeIf { it >= 0 } ?: 0]

    /** Matches what the user types in any language, so "футбол" and "football" both find it. */
    fun matches(query: String): Boolean = names.any { it.contains(query, ignoreCase = true) }

    /** True when [words] is this topic's name in one of the languages. */
    fun isNamed(words: String): Boolean = names.any { it.equals(words, ignoreCase = true) }

    private companion object {
        val LANGUAGES = listOf("en", "ru", "es", "tr", "de", "fr", "pt")
    }
}

object TopicCatalog {
    /** "en|ru|es|tr|de|fr|pt", or one name used everywhere. */
    private fun t(group: TopicGroup, names: String): TopicSuggestion {
        val parts = names.split('|')
        return TopicSuggestion(group, if (parts.size == 1) List(7) { parts[0] } else parts)
    }

    val all: List<TopicSuggestion> = listOf(
        // Technology
        t(TopicGroup.TECH, "Artificial intelligence|Искусственный интеллект|Inteligencia artificial|Yapay zeka|Künstliche Intelligenz|Intelligence artificielle|Inteligência artificial"),
        t(TopicGroup.TECH, "ChatGPT"),
        t(TopicGroup.TECH, "OpenAI"),
        t(TopicGroup.TECH, "Smartphones|Смартфоны|Smartphones|Akıllı telefonlar|Smartphones|Smartphones|Smartphones"),
        t(TopicGroup.TECH, "Apple"),
        t(TopicGroup.TECH, "iPhone"),
        t(TopicGroup.TECH, "Google"),
        t(TopicGroup.TECH, "Android"),
        t(TopicGroup.TECH, "Samsung"),
        t(TopicGroup.TECH, "Microsoft"),
        t(TopicGroup.TECH, "Nvidia"),
        t(TopicGroup.TECH, "Elon Musk|Илон Маск|Elon Musk|Elon Musk|Elon Musk|Elon Musk|Elon Musk"),
        t(TopicGroup.TECH, "Cybersecurity|Кибербезопасность|Ciberseguridad|Siber güvenlik|Cybersicherheit|Cybersécurité|Cibersegurança"),
        t(TopicGroup.TECH, "Startups|Стартапы|Startups|Girişimler|Start-ups|Start-up|Startups"),
        t(TopicGroup.TECH, "Robotics|Робототехника|Robótica|Robotik|Robotik|Robotique|Robótica"),
        t(TopicGroup.TECH, "Gadgets|Гаджеты|Gadgets|Teknolojik cihazlar|Gadgets|Gadgets|Gadgets"),
        t(TopicGroup.TECH, "Programming|Программирование|Programación|Programlama|Programmierung|Programmation|Programação"),
        t(TopicGroup.TECH, "Semiconductors|Полупроводники|Semiconductores|Yarı iletkenler|Halbleiter|Semi-conducteurs|Semicondutores"),
        t(TopicGroup.TECH, "Virtual reality|Виртуальная реальность|Realidad virtual|Sanal gerçeklik|Virtuelle Realität|Réalité virtuelle|Realidade virtual"),
        t(TopicGroup.TECH, "Social media|Социальные сети|Redes sociales|Sosyal medya|Soziale Netzwerke|Réseaux sociaux|Redes sociais"),
        t(TopicGroup.TECH, "Telegram"),
        // Science
        t(TopicGroup.SCIENCE, "Space|Космос|Espacio|Uzay|Raumfahrt|Espace|Espaço"),
        t(TopicGroup.SCIENCE, "SpaceX"),
        t(TopicGroup.SCIENCE, "NASA|НАСА|NASA|NASA|NASA|NASA|NASA"),
        t(TopicGroup.SCIENCE, "Astronomy|Астрономия|Astronomía|Astronomi|Astronomie|Astronomie|Astronomia"),
        t(TopicGroup.SCIENCE, "Physics|Физика|Física|Fizik|Physik|Physique|Física"),
        t(TopicGroup.SCIENCE, "Biology|Биология|Biología|Biyoloji|Biologie|Biologie|Biologia"),
        t(TopicGroup.SCIENCE, "Climate|Климат|Clima|İklim|Klima|Climat|Clima"),
        t(TopicGroup.SCIENCE, "Ecology|Экология|Ecología|Ekoloji|Umweltschutz|Écologie|Ecologia"),
        t(TopicGroup.SCIENCE, "Archaeology|Археология|Arqueología|Arkeoloji|Archäologie|Archéologie|Arqueologia"),
        t(TopicGroup.SCIENCE, "Energy|Энергетика|Energía|Enerji|Energie|Énergie|Energia"),
        t(TopicGroup.SCIENCE, "Oceans|Океаны|Océanos|Okyanuslar|Ozeane|Océans|Oceanos"),
        // Money
        t(TopicGroup.MONEY, "Business|Бизнес|Negocios|İş dünyası|Wirtschaft|Entreprises|Negócios"),
        t(TopicGroup.MONEY, "Economy|Экономика|Economía|Ekonomi|Konjunktur|Économie|Economia"),
        t(TopicGroup.MONEY, "Stock market|Фондовый рынок|Bolsa|Borsa|Börse|Bourse|Bolsa de valores"),
        t(TopicGroup.MONEY, "Cryptocurrency|Криптовалюта|Criptomonedas|Kripto para|Kryptowährungen|Cryptomonnaies|Criptomoedas"),
        t(TopicGroup.MONEY, "Bitcoin|Биткоин|Bitcoin|Bitcoin|Bitcoin|Bitcoin|Bitcoin"),
        t(TopicGroup.MONEY, "Real estate|Недвижимость|Inmobiliario|Emlak|Immobilien|Immobilier|Imóveis"),
        t(TopicGroup.MONEY, "Personal finance|Личные финансы|Finanzas personales|Kişisel finans|Private Finanzen|Finances personnelles|Finanças pessoais"),
        t(TopicGroup.MONEY, "Banks|Банки|Bancos|Bankalar|Banken|Banques|Bancos"),
        t(TopicGroup.MONEY, "Oil prices|Цены на нефть|Precio del petróleo|Petrol fiyatları|Ölpreis|Prix du pétrole|Preço do petróleo"),
        t(TopicGroup.MONEY, "Gold price|Цена золота|Precio del oro|Altın fiyatları|Goldpreis|Cours de l'or|Preço do ouro"),
        t(TopicGroup.MONEY, "Marketing|Маркетинг|Marketing|Pazarlama|Marketing|Marketing|Marketing"),
        t(TopicGroup.MONEY, "E-commerce|Электронная коммерция|Comercio electrónico|E-ticaret|E-Commerce|E-commerce|Comércio eletrônico"),
        t(TopicGroup.MONEY, "Venture capital|Венчурные инвестиции|Capital riesgo|Girişim sermayesi|Risikokapital|Capital-risque|Capital de risco"),
        t(TopicGroup.MONEY, "Exchange rates|Курсы валют|Tipos de cambio|Döviz kurları|Wechselkurse|Taux de change|Câmbio"),
        // Sport
        t(TopicGroup.SPORT, "Football|Футбол|Fútbol|Futbol|Fußball|Football|Futebol"),
        t(TopicGroup.SPORT, "Champions League|Лига чемпионов|Champions League|Şampiyonlar Ligi|Champions League|Ligue des champions|Liga dos Campeões"),
        t(TopicGroup.SPORT, "Premier League|Английская премьер-лига|Premier League|Premier Lig|Premier League|Premier League|Premier League"),
        t(TopicGroup.SPORT, "Formula 1|Формула-1|Fórmula 1|Formula 1|Formel 1|Formule 1|Fórmula 1"),
        t(TopicGroup.SPORT, "Tennis|Теннис|Tenis|Tenis|Tennis|Tennis|Tênis"),
        t(TopicGroup.SPORT, "Basketball|Баскетбол|Baloncesto|Basketbol|Basketball|Basket-ball|Basquete"),
        t(TopicGroup.SPORT, "NBA"),
        t(TopicGroup.SPORT, "Ice hockey|Хоккей|Hockey sobre hielo|Buz hokeyi|Eishockey|Hockey sur glace|Hóquei no gelo"),
        t(TopicGroup.SPORT, "UFC"),
        t(TopicGroup.SPORT, "Boxing|Бокс|Boxeo|Boks|Boxen|Boxe|Boxe"),
        t(TopicGroup.SPORT, "Muay Thai|Тайский бокс|Muay Thai|Muay Thai|Muay Thai|Boxe thaïe|Muay Thai"),
        t(TopicGroup.SPORT, "Olympic Games|Олимпийские игры|Juegos Olímpicos|Olimpiyat Oyunları|Olympische Spiele|Jeux olympiques|Jogos Olímpicos"),
        t(TopicGroup.SPORT, "Cycling|Велоспорт|Ciclismo|Bisiklet|Radsport|Cyclisme|Ciclismo"),
        t(TopicGroup.SPORT, "Golf|Гольф|Golf|Golf|Golf|Golf|Golfe"),
        t(TopicGroup.SPORT, "Chess|Шахматы|Ajedrez|Satranç|Schach|Échecs|Xadrez"),
        t(TopicGroup.SPORT, "Surfing|Сёрфинг|Surf|Sörf|Surfen|Surf|Surfe"),
        t(TopicGroup.SPORT, "Running|Бег|Running|Koşu|Laufsport|Course à pied|Corrida"),
        // Movies, music, books
        t(TopicGroup.CULTURE, "Movies|Кино|Cine|Sinema|Kino|Cinéma|Cinema"),
        t(TopicGroup.CULTURE, "TV series|Сериалы|Series|Diziler|Serien|Séries|Séries"),
        t(TopicGroup.CULTURE, "Netflix"),
        t(TopicGroup.CULTURE, "Music|Музыка|Música|Müzik|Musik|Musique|Música"),
        t(TopicGroup.CULTURE, "Celebrities|Знаменитости|Famosos|Ünlüler|Promis|Célébrités|Celebridades"),
        t(TopicGroup.CULTURE, "Books|Книги|Libros|Kitaplar|Bücher|Livres|Livros"),
        t(TopicGroup.CULTURE, "Anime|Аниме|Anime|Anime|Anime|Anime|Anime"),
        t(TopicGroup.CULTURE, "Art|Искусство|Arte|Sanat|Kunst|Art|Arte"),
        t(TopicGroup.CULTURE, "Theatre|Театр|Teatro|Tiyatro|Theater|Théâtre|Teatro"),
        t(TopicGroup.CULTURE, "Marvel"),
        t(TopicGroup.CULTURE, "Oscars|Оскар|Premios Óscar|Oscar Ödülleri|Oscars|Oscars|Oscar"),
        // Games
        t(TopicGroup.GAMES, "Video games|Видеоигры|Videojuegos|Video oyunları|Videospiele|Jeux vidéo|Videogames"),
        t(TopicGroup.GAMES, "PlayStation"),
        t(TopicGroup.GAMES, "Xbox"),
        t(TopicGroup.GAMES, "Nintendo"),
        t(TopicGroup.GAMES, "Steam"),
        t(TopicGroup.GAMES, "Esports|Киберспорт|Esports|E-spor|E-Sport|E-sport|E-sports"),
        t(TopicGroup.GAMES, "GTA 6"),
        t(TopicGroup.GAMES, "Minecraft"),
        // Cars and transport
        t(TopicGroup.AUTO, "Cars|Автомобили|Coches|Otomobil|Autos|Automobile|Carros"),
        t(TopicGroup.AUTO, "Electric cars|Электромобили|Coches eléctricos|Elektrikli araçlar|Elektroautos|Voitures électriques|Carros elétricos"),
        t(TopicGroup.AUTO, "Tesla"),
        t(TopicGroup.AUTO, "BMW"),
        t(TopicGroup.AUTO, "Toyota"),
        t(TopicGroup.AUTO, "Motorcycles|Мотоциклы|Motos|Motosiklet|Motorräder|Motos|Motos"),
        t(TopicGroup.AUTO, "Aviation|Авиация|Aviación|Havacılık|Luftfahrt|Aviation|Aviação"),
        t(TopicGroup.AUTO, "Self-driving cars|Беспилотные автомобили|Coches autónomos|Otonom araçlar|Autonomes Fahren|Voitures autonomes|Carros autônomos"),
        // Health
        t(TopicGroup.HEALTH, "Health|Здоровье|Salud|Sağlık|Gesundheit|Santé|Saúde"),
        t(TopicGroup.HEALTH, "Fitness|Фитнес|Fitness|Fitness|Fitness|Fitness|Fitness"),
        t(TopicGroup.HEALTH, "Nutrition|Питание|Nutrición|Beslenme|Ernährung|Nutrition|Nutrição"),
        t(TopicGroup.HEALTH, "Medicine|Медицина|Medicina|Tıp|Medizin|Médecine|Medicina"),
        t(TopicGroup.HEALTH, "Psychology|Психология|Psicología|Psikoloji|Psychologie|Psychologie|Psicologia"),
        t(TopicGroup.HEALTH, "Longevity|Долголетие|Longevidad|Uzun yaşam|Langlebigkeit|Longévité|Longevidade"),
        t(TopicGroup.HEALTH, "Sleep|Сон|Sueño|Uyku|Schlaf|Sommeil|Sono"),
        // Lifestyle
        t(TopicGroup.LIFE, "Travel|Путешествия|Viajes|Seyahat|Reisen|Voyage|Viagens"),
        t(TopicGroup.LIFE, "Food|Еда|Gastronomía|Yemek|Essen und Trinken|Gastronomie|Gastronomia"),
        t(TopicGroup.LIFE, "Fashion|Мода|Moda|Moda|Mode|Mode|Moda"),
        t(TopicGroup.LIFE, "Design|Дизайн|Diseño|Tasarım|Design|Design|Design"),
        t(TopicGroup.LIFE, "Architecture|Архитектура|Arquitectura|Mimarlık|Architektur|Architecture|Arquitetura"),
        t(TopicGroup.LIFE, "Education|Образование|Educación|Eğitim|Bildung|Éducation|Educação"),
        t(TopicGroup.LIFE, "Parenting|Дети и родители|Crianza|Ebeveynlik|Familie|Parentalité|Parentalidade"),
        t(TopicGroup.LIFE, "Pets|Домашние животные|Mascotas|Evcil hayvanlar|Haustiere|Animaux de compagnie|Animais de estimação"),
        t(TopicGroup.LIFE, "Coffee|Кофе|Café|Kahve|Kaffee|Café|Café"),
        t(TopicGroup.LIFE, "Wine|Вино|Vino|Şarap|Wein|Vin|Vinho"),
        // World and places
        t(TopicGroup.WORLD, "World news|Новости мира|Internacional|Dünya haberleri|Weltnachrichten|International|Mundo"),
        t(TopicGroup.WORLD, "Politics|Политика|Política|Siyaset|Politik|Politique|Política"),
        t(TopicGroup.WORLD, "Thailand|Таиланд|Tailandia|Tayland|Thailand|Thaïlande|Tailândia"),
        t(TopicGroup.WORLD, "Phuket|Пхукет|Phuket|Phuket|Phuket|Phuket|Phuket"),
        t(TopicGroup.WORLD, "Bali|Бали|Bali|Bali|Bali|Bali|Bali"),
        t(TopicGroup.WORLD, "Dubai|Дубай|Dubái|Dubai|Dubai|Dubaï|Dubai"),
        t(TopicGroup.WORLD, "Russia|Россия|Rusia|Rusya|Russland|Russie|Rússia"),
        t(TopicGroup.WORLD, "United States|США|Estados Unidos|ABD|USA|États-Unis|Estados Unidos"),
        t(TopicGroup.WORLD, "Europe|Европа|Europa|Avrupa|Europa|Europe|Europa"),
        t(TopicGroup.WORLD, "China|Китай|China|Çin|China|Chine|China"),
        t(TopicGroup.WORLD, "Asia|Азия|Asia|Asya|Asien|Asie|Ásia"),
        t(TopicGroup.WORLD, "Middle East|Ближний Восток|Oriente Medio|Orta Doğu|Nahost|Moyen-Orient|Oriente Médio"),
        t(TopicGroup.WORLD, "Turkey|Турция|Turquía|Türkiye|Türkei|Turquie|Turquia"),
        t(TopicGroup.WORLD, "Latin America|Латинская Америка|América Latina|Latin Amerika|Lateinamerika|Amérique latine|América Latina"),
        t(TopicGroup.WORLD, "Africa|Африка|África|Afrika|Afrika|Afrique|África"),
    )
}
