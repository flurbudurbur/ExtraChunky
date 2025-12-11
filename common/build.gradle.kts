dependencies {
    compileOnly(group = "com.google.code.gson", name = "gson", version = "2.10.1")
    compileOnly(group = "org.popcraft", name = "chunky-common", version = "1.4.55.0")

    // SSH/SFTP for file transfers
    implementation(group = "com.hierynomus", name = "sshj", version = "0.38.0")

    // ZSTD compression for region files
    implementation(group = "com.github.luben", name = "zstd-jni", version = "1.5.5-11")
}
