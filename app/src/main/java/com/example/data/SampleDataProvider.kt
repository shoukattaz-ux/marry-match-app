package com.example.data

import com.example.model.ChatMessage
import com.example.model.MatchItem
import com.example.model.ProfilePrompt
import com.example.model.ReportItem
import com.example.model.UserProfile
import com.example.model.VerificationRequest

object SampleDataProvider {

    val currentUser = UserProfile(
        id = "user_me",
        email = "babar@example.de",
        displayName = "Lukas",
        birthYear = 1996,
        age = 30,
        gender = "man",
        lookingFor = "woman",
        city = "München",
        region = "Bayern",
        bio = "Engineer based in Munich. Looking for a partner who values mutual respect, honesty, and wants to build a warm, supportive family together.",
        marriageTimeline = "1_2y",
        wantsChildren = "yes",
        religion = "Christian",
        smoking = "Non-smoker",
        education = "Master in Computer Science (TU München)",
        occupation = "Software Architect",
        languages = listOf("German", "English"),
        heightCm = 184,
        onboardingDone = true,
        photos = listOf(
            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=800&q=80"
        ),
        prompts = listOf(
            ProfilePrompt(
                "ideal_sunday",
                "My ideal Sunday is...",
                "Mein idealer Sonntag ist...",
                "A fresh morning walk through Englischer Garten, brewing espresso together, and cooking a leisurely homemade dinner."
            ),
            ProfilePrompt(
                "family_means",
                "What family means to me...",
                "Was Familie für mich bedeutet...",
                "A safe harbor of unconditional love, laughter across generations, and growing together through every chapter."
            ),
            ProfilePrompt(
                "non_negotiable",
                "A value I will not compromise on...",
                "Ein Wert, bei dem ich keine Kompromisse eingehe...",
                "Integrity and deep mutual respect. In a marriage, communication is everything."
            )
        ),
        selfieVerified = false,
        phoneVerified = true,
        isAdmin = false,
        respectScore = 100
    )

    fun getDailyProfiles(): List<UserProfile> = listOf(
        UserProfile(
            id = "c1",
            email = "anna.k@marrymatch.de",
            displayName = "Anna",
            birthYear = 1997,
            age = 29,
            gender = "woman",
            lookingFor = "man",
            city = "München",
            region = "Bayern",
            bio = "Pediatrician in Munich. Passionate about helping children, spending sunny weekends in the Bavarian Alps, and preparing for the next big milestone in life: marriage and family.",
            marriageTimeline = "1_2y",
            wantsChildren = "yes",
            religion = "Christian",
            smoking = "Non-smoker",
            education = "State Examination in Medicine (LMU)",
            occupation = "Pediatrician",
            languages = listOf("German", "English", "French"),
            heightCm = 170,
            photos = listOf(
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=800&q=80"
            ),
            prompts = listOf(
                ProfilePrompt(
                    "ideal_sunday",
                    "My ideal Sunday is...",
                    "Mein idealer Sonntag ist...",
                    "Hiking up to Tegernsee, fresh Apfelstrudel, and peaceful quiet away from the hospital rush."
                ),
                ProfilePrompt(
                    "lasting_marriage",
                    "The key to a lasting marriage is...",
                    "Der Schlüssel zu einer dauerhaften Ehe ist...",
                    "Teamwork in small moments and being each other's greatest cheerleader when life gets challenging."
                ),
                ProfilePrompt(
                    "family_means",
                    "What family means to me...",
                    "Was Familie für mich bedeutet...",
                    "Sunday dinners around a bustling dining table where everyone feels heard and loved."
                )
            ),
            interests = listOf("Alps Hiking", "Baking & Cooking", "Family Dinners", "Classical Music", "Nature Walks"),
            selfieVerified = true,
            phoneVerified = true,
            respectScore = 100
        ),
        UserProfile(
            id = "c2",
            email = "sophie.b@marrymatch.de",
            displayName = "Sophie",
            birthYear = 1998,
            age = 28,
            gender = "woman",
            lookingFor = "man",
            city = "Berlin",
            region = "Berlin",
            bio = "Architect working on sustainable wooden housing. Living in Prenzlauer Berg. Looking for someone grounded, kind-hearted, with shared family values.",
            marriageTimeline = "within_1y",
            wantsChildren = "yes",
            religion = "Spiritual",
            smoking = "Non-smoker",
            education = "M.Sc. Architecture (TU Berlin)",
            occupation = "Sustainable Architect",
            languages = listOf("German", "English"),
            heightCm = 168,
            photos = listOf(
                "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1488426862026-3ee34a7d66df?auto=format&fit=crop&w=800&q=80"
            ),
            prompts = listOf(
                ProfilePrompt(
                    "non_negotiable",
                    "A value I will not compromise on...",
                    "Ein Wert, bei dem ich keine Kompromisse eingehe...",
                    "Honesty and emotional maturity. I value a partner who talks through feelings openly."
                ),
                ProfilePrompt(
                    "ideal_sunday",
                    "My ideal Sunday is...",
                    "Mein idealer Sonntag ist...",
                    "Biking by Wannsee, browsing a flea market for vintage furniture, and cooking pasta from scratch."
                ),
                ProfilePrompt(
                    "tradition",
                    "My favorite tradition to share...",
                    "Meine Lieblingstradition, die ich teilen möchte...",
                    "Baking traditional German Plätzchen with my family every December while listening to acoustic jazz."
                )
            ),
            interests = listOf("Architecture & Art", "Biking", "Baking & Cooking", "Travel & Roadtrips", "Coffee Culture"),
            selfieVerified = true,
            phoneVerified = false,
            respectScore = 95
        ),
        UserProfile(
            id = "c3",
            email = "elena.m@marrymatch.de",
            displayName = "Elena",
            birthYear = 1995,
            age = 31,
            gender = "woman",
            lookingFor = "man",
            city = "Frankfurt am Main",
            region = "Hessen",
            bio = "Compliance lawyer in Frankfurt. In my free time, I play cello and volunteer at an animal shelter. Looking for an equal partnership grounded in mutual affection.",
            marriageTimeline = "1_2y",
            wantsChildren = "yes",
            religion = "Christian",
            smoking = "Non-smoker",
            education = "2nd Legal State Examination",
            occupation = "Legal Counsel",
            languages = listOf("German", "English", "Italian"),
            heightCm = 172,
            photos = listOf(
                "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?auto=format&fit=crop&w=800&q=80"
            ),
            prompts = listOf(
                ProfilePrompt(
                    "lasting_marriage",
                    "The key to a lasting marriage is...",
                    "Der Schlüssel zu einer dauerhaften Ehe ist...",
                    "Never going to bed angry, laughing together every single day, and continuous emotional curiosity."
                ),
                ProfilePrompt(
                    "family_means",
                    "What family means to me...",
                    "Was Familie für mich bedeutet...",
                    "The anchor of life. Creating a home where peace, joy, and encouragement live."
                ),
                ProfilePrompt(
                    "ideal_sunday",
                    "My ideal Sunday is...",
                    "Mein idealer Sonntag ist...",
                    "Classical concert matinee at Alte Oper followed by coffee along the river Main."
                )
            ),
            interests = listOf("Classical Music", "Animal Welfare", "Reading & Literature", "Coffee Culture", "Travel & Roadtrips"),
            selfieVerified = true,
            phoneVerified = true,
            respectScore = 100
        ),
        UserProfile(
            id = "c4",
            email = "laura.w@marrymatch.de",
            displayName = "Laura",
            birthYear = 1999,
            age = 27,
            gender = "woman",
            lookingFor = "man",
            city = "Hamburg",
            region = "Hamburg",
            bio = "Marine Biologist working on North Sea conservation. Down-to-earth, love seaside breezes, Nordic minimalism, and serious intent.",
            marriageTimeline = "2_5y",
            wantsChildren = "open",
            religion = "None / Secular",
            smoking = "Non-smoker",
            education = "M.Sc. Marine Ecology (Uni Hamburg)",
            occupation = "Marine Biologist",
            languages = listOf("German", "English", "Danish"),
            heightCm = 175,
            photos = listOf(
                "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=800&q=80"
            ),
            prompts = listOf(
                ProfilePrompt(
                    "ideal_sunday",
                    "My ideal Sunday is...",
                    "Mein idealer Sonntag ist...",
                    "Strolling along the Elbstrand, eating Franzbrötchen, and watching container ships roll in."
                ),
                ProfilePrompt(
                    "non_negotiable",
                    "A value I will not compromise on...",
                    "Ein Wert, bei dem ich keine Kompromisse eingehe...",
                    "Kindness to animals, strangers, and the planet."
                ),
                ProfilePrompt(
                    "disagreements",
                    "How I handle disagreements...",
                    "Wie ich Meinungsverschiedenheiten löse...",
                    "Pausing to listen first. We are on the same team against the problem, not each other."
                )
            ),
            interests = listOf("Nature Walks", "Biking", "Alps Hiking", "Travel & Roadtrips", "Animal Welfare"),
            selfieVerified = false,
            phoneVerified = true,
            respectScore = 100
        ),
        UserProfile(
            id = "c5",
            email = "miriam.s@marrymatch.de",
            displayName = "Miriam",
            birthYear = 1996,
            age = 30,
            gender = "woman",
            lookingFor = "man",
            city = "Köln",
            region = "Nordrhein-Westfalen",
            bio = "High school teacher for German & History. Warm-hearted, love book clubs, historic architecture, and ready to meet someone ready for the adventure of marriage.",
            marriageTimeline = "1_2y",
            wantsChildren = "yes",
            religion = "Christian",
            smoking = "Non-smoker",
            education = "Master of Education (Uni Köln)",
            occupation = "Gymnasium Teacher",
            languages = listOf("German", "English", "Latin"),
            heightCm = 165,
            photos = listOf(
                "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?auto=format&fit=crop&w=800&q=80"
            ),
            prompts = listOf(
                ProfilePrompt(
                    "family_means",
                    "What family means to me...",
                    "Was Familie für mich bedeutet...",
                    "The foundation of everything good. Traditions, warmth, and lifelong loyalty."
                ),
                ProfilePrompt(
                    "ideal_sunday",
                    "My ideal Sunday is...",
                    "Mein idealer Sonntag ist...",
                    "Reading with tea, a long walk in the Stadtwald, and baking sourdough bread."
                ),
                ProfilePrompt(
                    "lasting_marriage",
                    "The key to a lasting marriage is...",
                    "Der Schlüssel zu einer dauerhaften Ehe ist...",
                    "Gratitude for each other's efforts and never taking presence for granted."
                )
            ),
            interests = listOf("Reading & Literature", "Baking & Cooking", "Nature Walks", "Coffee Culture", "Family Dinners"),
            selfieVerified = true,
            phoneVerified = true,
            respectScore = 100
        ),
        UserProfile(
            id = "c6",
            email = "clara.v@marrymatch.de",
            displayName = "Clara",
            birthYear = 2000,
            age = 26,
            gender = "woman",
            lookingFor = "man",
            city = "Stuttgart",
            region = "Baden-Württemberg",
            bio = "Product designer and outdoor enthusiast living in Stuttgart. I believe in quiet moments, big dreams, and building an intentional marriage.",
            marriageTimeline = "1_2y",
            wantsChildren = "yes",
            religion = "Christian",
            smoking = "Non-smoker",
            education = "B.A. Industrial Design (HfG Schwäbisch Gmünd)",
            occupation = "Product Designer",
            languages = listOf("German", "English"),
            heightCm = 171,
            photos = listOf(
                "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=800&q=80",
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=800&q=80"
            ),
            prompts = listOf(
                ProfilePrompt(
                    "ideal_sunday",
                    "My ideal Sunday is...",
                    "Mein idealer Sonntag ist...",
                    "Early morning trail run in Schönbuch followed by fresh pretzel breakfast."
                )
            ),
            interests = listOf("Alps Hiking", "Biking", "Coffee Culture", "Architecture & Art"),
            selfieVerified = true,
            phoneVerified = true,
            countryOk = true,
            respectScore = 98
        ),
        UserProfile(
            id = "c7_foreign",
            email = "chloe.dupont@example.com",
            displayName = "Chloé",
            birthYear = 1997,
            age = 29,
            gender = "woman",
            lookingFor = "man",
            city = "Paris",
            region = "Île-de-France",
            bio = "Artist currently in Paris.",
            marriageTimeline = "open",
            wantsChildren = "open",
            education = "Art Academy",
            occupation = "Illustrator",
            interests = listOf("Architecture & Art", "Travel & Roadtrips"),
            photos = listOf("https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=800&q=80"),
            selfieVerified = false,
            phoneVerified = false,
            countryOk = false, // Outside Germany - to verify Germany location filter
            respectScore = 80
        )
    )

    fun getInitialMatches(): List<MatchItem> = listOf(
        MatchItem(
            id = "match_1",
            matchedUser = getDailyProfiles()[0], // Anna
            matchedAt = "Yesterday",
            lastMessage = "Your Sunday walk through Englischer Garten sounds wonderful! Have you been near Kleinhesseloher See lately?",
            lastMessageTime = "14:20",
            unread = true
        ),
        MatchItem(
            id = "match_2",
            matchedUser = getDailyProfiles()[2], // Elena
            matchedAt = "3 days ago",
            lastMessage = "It's so refreshing to meet someone here with clear intentions for marriage. Looking forward to our conversation!",
            lastMessageTime = "Oct 12",
            unread = false
        )
    )

    fun getInitialMessages(matchId: String): List<ChatMessage> {
        return if (matchId == "match_1") {
            listOf(
                ChatMessage("m1", matchId, "c1", "Hallo Lukas! It's so nice to connect with you here.", "14:15"),
                ChatMessage("m2", matchId, "user_me", "Hallo Anna! Great to meet you. I loved what you wrote about teamwork in marriage.", "14:18"),
                ChatMessage("m3", matchId, "c1", "Your Sunday walk through Englischer Garten sounds wonderful! Have you been near Kleinhesseloher See lately?", "14:20")
            )
        } else {
            listOf(
                ChatMessage("m4", matchId, "c3", "Hallo Lukas! I saw your answer regarding communication.", "Oct 12, 10:11"),
                ChatMessage("m5", matchId, "user_me", "Hallo Elena! Yes, I believe open communication is the cornerstone of everything.", "Oct 12, 11:05"),
                ChatMessage("m6", matchId, "c3", "It's so refreshing to meet someone here with clear intentions for marriage. Looking forward to our conversation!", "Oct 12, 11:20")
            )
        }
    }

    fun getPendingVerifications(): List<VerificationRequest> = listOf(
        VerificationRequest(
            id = "ver_1",
            userId = "c2",
            userName = "Sophie",
            userAge = 28,
            userCity = "Berlin",
            profilePhoto = "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?auto=format&fit=crop&w=800&q=80",
            pose = "Touch your nose with one finger",
            selfiePhotoUrl = "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?auto=format&fit=crop&w=400&q=80",
            status = "pending",
            createdAt = "10 mins ago"
        ),
        VerificationRequest(
            id = "ver_2",
            userId = "c4",
            userName = "Laura",
            userAge = 27,
            userCity = "Hamburg",
            profilePhoto = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=800&q=80",
            pose = "Thumbs up and tilt head to the left",
            selfiePhotoUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=400&q=80",
            status = "pending",
            createdAt = "1 hour ago"
        )
    )

    fun getInitialReports(): List<ReportItem> = listOf(
        ReportItem(
            id = "rep_1",
            reporterId = "user_me",
            reportedUserId = "bad_user_99",
            reportedUserName = "Suspicious Profile",
            reason = "scam",
            details = "User immediately asked to move to Telegram and requested crypto investment.",
            status = "open",
            createdAt = "2 hours ago"
        )
    )
}
