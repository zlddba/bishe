package team.wd.atnestapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform