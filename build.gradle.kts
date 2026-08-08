plugins {
    java
    application
}

group = "itt.tcl"
version = "1.0.0"

// Java 版本配置
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    // 启用预览功能（如果有需要）
    // toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// JavaFX 版本
val javafxVersion = "21.0.2"

application {
    mainClass.set("itt.tcl.TechCraftLauncher")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8",
        // 性能优化 JVM 参数
        "-XX:+UseG1GC",
        "-XX:+UseStringDeduplication",
        "-XX:MaxGCPauseMillis=200",
        "-XX:InitiatingHeapOccupancyPercent=45",
        "-XX:G1HeapRegionSize=16m",
        "-XX:G1ReservePercent=15",
        // 内存限制
        "-Xmx512m",
        "-Xms128m",
        // 启动优化
        "-XX:+TieredCompilation",
        "-XX:TieredStopAtLevel=1"
    )
}

repositories {
    mavenCentral()
    // 添加阿里云镜像加速（中国用户）
    maven { url = uri("https://maven.aliyun.com/repository/public") }
}

dependencies {
    // 核心依赖
    implementation("com.google.code.gson:gson:2.10.1")
    
    // JavaFX 依赖（按需加载）
    implementation("org.openjfx:javafx-base:$javafxVersion:win")
    implementation("org.openjfx:javafx-controls:$javafxVersion:win")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:win")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:win")
}

// 编译优化
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-Xlint:unchecked",
        "-Xlint:deprecation"
    ))
    // 启用增量编译
    options.isIncremental = true
}

// JAR 打包优化
tasks.register<Jar>("fatJar") {
    group = "build"
    archiveClassifier.set("")
    
    manifest {
        attributes("Main-Class" to "itt.tcl.TechCraftLauncher")
    }
    
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    
    from({
        configurations.runtimeClasspath.get().map { 
            if (it.isDirectory) it else zipTree(it) 
        }
    })
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    // 排除不必要的文件
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("META-INF/INDEX.LIST")
}

// 测试配置（如果有）
tasks.withType<Test> {
    useJUnitPlatform()
    
    // 测试 JVM 参数
    jvmArgs = listOf(
        "-XX:+UseG1GC",
        "-Xmx256m"
    )
    
    // 并行测试
    maxParallelForks = Runtime.getRuntime().availableProcessors().div(2).coerceAtLeast(1)
}

// 清理任务
tasks.register("cleanAll") {
    doFirst {
        delete("build")
        delete("out")
        delete(".gradle")
    }
}