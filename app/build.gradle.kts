plugins {
    application
}

dependencies {
    api(project(":core"))
}

application {
    mainClass.set("org.hertsig.app.AppKt")
}
