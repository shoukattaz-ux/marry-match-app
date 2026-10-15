package com.example.model

data class GermanCity(
    val name: String,
    val state: String,
    val lat: Double,
    val lng: Double
)

object GermanCities {
    val list = listOf(
        GermanCity("Berlin", "Berlin", 52.5200, 13.4050),
        GermanCity("München", "Bayern", 48.1351, 11.5820),
        GermanCity("Hamburg", "Hamburg", 53.5511, 9.9937),
        GermanCity("Köln", "Nordrhein-Westfalen", 50.9375, 6.9603),
        GermanCity("Frankfurt am Main", "Hessen", 50.1109, 8.6821),
        GermanCity("Stuttgart", "Baden-Württemberg", 48.7758, 9.1829),
        GermanCity("Düsseldorf", "Nordrhein-Westfalen", 51.2277, 6.7735),
        GermanCity("Leipzig", "Sachsen", 51.3397, 12.3731),
        GermanCity("Dortmund", "Nordrhein-Westfalen", 51.5136, 7.4653),
        GermanCity("Essen", "Nordrhein-Westfalen", 51.4556, 7.0116),
        GermanCity("Bremen", "Bremen", 53.0793, 8.8017),
        GermanCity("Dresden", "Sachsen", 51.0504, 13.7373),
        GermanCity("Hannover", "Niedersachsen", 52.3759, 9.7320),
        GermanCity("Nürnberg", "Bayern", 49.4521, 11.0767),
        GermanCity("Duisburg", "Nordrhein-Westfalen", 51.4344, 6.7623),
        GermanCity("Bochum", "Nordrhein-Westfalen", 51.4818, 7.2162),
        GermanCity("Wuppertal", "Nordrhein-Westfalen", 51.2562, 7.1508),
        GermanCity("Bonn", "Nordrhein-Westfalen", 50.7374, 7.0982),
        GermanCity("Münster", "Nordrhein-Westfalen", 51.9607, 7.6261),
        GermanCity("Karlsruhe", "Baden-Württemberg", 49.0069, 8.4037),
        GermanCity("Mannheim", "Baden-Württemberg", 49.4875, 8.4660),
        GermanCity("Augsburg", "Bayern", 48.3705, 10.8978),
        GermanCity("Wiesbaden", "Hessen", 50.0782, 8.2398)
    )
}
