plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

/**
 * Ověřovací modul: přilinkuje čistě JVM části aplikace (Modbus klient a dekódování)
 * a spustí je proti živému měniči. Slouží k porovnání s výstupem read_solax.py.
 * Android části (UI, Prefs, WifiNetwork, NetworkScanner) se sem záměrně netahají.
 */
sourceSets {
    main {
        kotlin {
            srcDir("../app/src/main/java")
            srcDir("src/main/kotlin")
            // Filtr platí pro oba srcDir dohromady, proto tu musí být i Verify.kt
            include(
                "Verify.kt",
                "cz/solax/monitor/modbus/**",
                "cz/solax/monitor/data/Decode.kt",
                "cz/solax/monitor/data/Tables.kt",
                "cz/solax/monitor/data/SolaxData.kt",
                "cz/solax/monitor/data/SolaxReader.kt",
                "cz/solax/monitor/data/HacData.kt",
                "cz/solax/monitor/data/HacTables.kt",
                "cz/solax/monitor/data/HacReader.kt",
            )
        }
    }
}

// Bez jvmToolchain - použije se JVM, na kterém běží Gradle (JBR 21 z Android Studia)

application {
    mainClass.set("VerifyKt")
}
