tasks.register("build") {
    dependsOn(
        gradle.includedBuild("sjdb").task(":build")
    )
}
