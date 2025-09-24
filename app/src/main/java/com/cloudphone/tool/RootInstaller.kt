package com.cloudphone.tool

import java.io.File

object RootInstaller {
    fun isDeviceRooted(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exit = p.waitFor()
            exit == 0
        } catch (_: Exception) {
            false
        }
    }

    fun installApk(file: File): Pair<Boolean, String> {
        // 1) Try streaming install: pm install -r -S <size>
        val stream = streamInstall(file)
        if (stream.first) return stream
        // 2) Fallback: copy to /data/local/tmp and install from path
        val copy = copyToTmpAndInstall(file)
        return copy
    }

    // Cài đặt nhiều APK (split) cho file .apks qua root
    fun installApks(files: List<File>): Pair<Boolean, String> {
        val inputs = files.filter { it.exists() && it.isFile }
        if (inputs.isEmpty()) return false to "no apk files"
        // Ưu tiên: pm install-create + install-write (stream) + install-commit
        try {
            var p = ProcessBuilder("su", "-c", "pm install-create -r")
                .redirectErrorStream(true)
                .start()
            val outCreate = p.inputStream.bufferedReader().readText()
            val exitCreate = p.waitFor()
            if (exitCreate == 0) {
                val sessionId = Regex("\\[(\\d+)\\]").find(outCreate)?.groupValues?.get(1)
                    ?: Regex("session\\s+(\\d+)", RegexOption.IGNORE_CASE).find(outCreate)?.groupValues?.get(1)
                if (sessionId != null) {
                    for (f in inputs) {
                        val size = f.length()
                        val safeName = f.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        p = ProcessBuilder("su", "-c", "pm install-write -S $size $sessionId $safeName -")
                            .redirectErrorStream(true)
                            .start()
                        f.inputStream().use { input ->
                            p.outputStream.use { out ->
                                val buf = ByteArray(8 * 1024)
                                while (true) {
                                    val r = input.read(buf)
                                    if (r == -1) break
                                    out.write(buf, 0, r)
                                }
                                out.flush()
                            }
                        }
                        val exitW = p.waitFor()
                        val outW = p.inputStream.bufferedReader().readText()
                        if (exitW != 0) {
                            // Gặp lỗi khi write: fallback sang install-multiple
                            return fallbackInstallMultiple(inputs)
                        }
                    }
                    p = ProcessBuilder("su", "-c", "pm install-commit $sessionId")
                        .redirectErrorStream(true)
                        .start()
                    val outCommit = p.inputStream.bufferedReader().readText()
                    val exitCommit = p.waitFor()
                    return (exitCommit == 0) to outCommit
                }
            }
        } catch (_: Exception) {
            // Bỏ qua và thử fallback
        }
        // Fallback: copy sang /data/local/tmp và dùng pm install-multiple -r
        return fallbackInstallMultiple(inputs)
    }

    fun uninstall(packageName: String): Pair<Boolean, String> {
        return try {
            var p = ProcessBuilder("su", "-c", "pm uninstall $packageName")
                .redirectErrorStream(true)
                .start()
            val out1 = p.inputStream.bufferedReader().readText()
            val exit1 = p.waitFor()
            if (exit1 == 0) return true to out1
            // fallback --user 0
            p = ProcessBuilder("su", "-c", "pm uninstall --user 0 $packageName")
                .redirectErrorStream(true)
                .start()
            val out2 = p.inputStream.bufferedReader().readText()
            val exit2 = p.waitFor()
            (exit2 == 0) to out2
        } catch (e: Exception) {
            false to (e.message ?: "unknown error")
        }
    }

    private fun streamInstall(file: File): Pair<Boolean, String> {
        return try {
            val size = file.length()
            val p = ProcessBuilder("su", "-c", "pm install -r -S $size")
                .redirectErrorStream(true)
                .start()
            file.inputStream().use { input ->
                p.outputStream.use { out ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val r = input.read(buf)
                        if (r == -1) break
                        out.write(buf, 0, r)
                    }
                    out.flush()
                }
            }
            val exit = p.waitFor()
            val output = p.inputStream.bufferedReader().readText()
            (exit == 0) to output
        } catch (e: Exception) {
            false to (e.message ?: "unknown error")
        }
    }

    private fun copyToTmpAndInstall(file: File): Pair<Boolean, String> {
        val safeName = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val tmpPath = "/data/local/tmp/$safeName"
        return try {
            // Copy via su using cat > tmp
            var p = ProcessBuilder("su", "-c", "cat > $tmpPath")
                .redirectErrorStream(true)
                .start()
            file.inputStream().use { input ->
                p.outputStream.use { out ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val r = input.read(buf)
                        if (r == -1) break
                        out.write(buf, 0, r)
                    }
                    out.flush()
                }
            }
            p.waitFor()
            // chmod 644
            p = ProcessBuilder("su", "-c", "chmod 644 $tmpPath")
                .redirectErrorStream(true)
                .start()
            p.waitFor()
            // Try pm install -r from path
            p = ProcessBuilder("su", "-c", "pm install -r $tmpPath")
                .redirectErrorStream(true)
                .start()
            var output = p.inputStream.bufferedReader().readText()
            var exit = p.waitFor()
            if (exit != 0) {
                // fallback --user 0
                p = ProcessBuilder("su", "-c", "pm install -r --user 0 $tmpPath")
                    .redirectErrorStream(true)
                    .start()
                output = p.inputStream.bufferedReader().readText()
                exit = p.waitFor()
            }
            // cleanup
            ProcessBuilder("su", "-c", "rm -f $tmpPath").start()
            (exit == 0) to output
        } catch (e: Exception) {
            try { ProcessBuilder("su", "-c", "rm -f $tmpPath").start() } catch (_: Exception) {}
            false to (e.message ?: "unknown error")
        }
    }

    private fun fallbackInstallMultiple(files: List<File>): Pair<Boolean, String> {
        return try {
            val tmpDir = "/data/local/tmp/splits"
            ProcessBuilder("su", "-c", "rm -rf $tmpDir && mkdir -p $tmpDir && chmod 777 $tmpDir")
                .redirectErrorStream(true)
                .start()
                .waitFor()
            val paths = mutableListOf<String>()
            for (f in files) {
                val safe = f.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val remote = "$tmpDir/$safe"
                var p = ProcessBuilder("su", "-c", "cat > $remote")
                    .redirectErrorStream(true)
                    .start()
                f.inputStream().use { input ->
                    p.outputStream.use { out ->
                        val buf = ByteArray(8 * 1024)
                        while (true) {
                            val r = input.read(buf)
                            if (r == -1) break
                            out.write(buf, 0, r)
                        }
                        out.flush()
                    }
                }
                p.waitFor()
                ProcessBuilder("su", "-c", "chmod 644 $remote").start().waitFor()
                paths.add(remote)
            }
            val cmd = listOf("su", "-c", "pm install-multiple -r ${paths.joinToString(" ")}")
            val p = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText()
            val exit = p.waitFor()
            // cleanup
            ProcessBuilder("su", "-c", "rm -rf $tmpDir").start()
            (exit == 0) to out
        } catch (e: Exception) {
            false to (e.message ?: "unknown error")
        }
    }
}

