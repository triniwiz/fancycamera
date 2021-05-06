package io.github.triniwiz.fancycamera

enum class WhiteBalance(internal val value: String) {
    Auto("auto"),
    Sunny("daylight"),
    Cloudy("cloudy-daylight"),
    Shadow("shade"),
    Twilight("twilight"),
    Fluorescent("fluorescent"),
    Incandescent("incandescent"),
    WarmFluorescent("warm-fluorescent")
}