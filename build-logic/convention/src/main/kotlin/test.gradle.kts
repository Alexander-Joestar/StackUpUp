// 测试：JUnit 5（Gradle 测试任务统一使用 JUnit Platform）。

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
