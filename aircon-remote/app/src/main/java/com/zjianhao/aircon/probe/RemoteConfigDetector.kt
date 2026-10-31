package com.zjianhao.aircon.probe

import android.content.Context
import android.content.Intent
import java.io.File

/** 检测到的一个遥控类应用。 */
data class RemoteApp(val packageName: String, val label: String)

/**
 * 检测结果的原始事实，格式化交给界面层。
 */
data class ProbeFacts(
    val installedApps: List<RemoteApp>,
    val hasRoot: Boolean,
    val readableDirs: List<String>,
    val configFileCount: Int
) {
    /** 只要真的读到配置文件，才算"能获取万能遥控的配置"。 */
    val canAccessConfig: Boolean get() = configFileCount > 0
}

/** 一行检测结果。 */
data class ProbeResult(val title: String, val passed: Boolean, val detail: String)

/**
 * 检测本机能否获取「万能遥控」类应用保存的遥控配置。
 *
 * 背景：这类配置存放在各自的私有目录（/data/data/<包名>/shared_prefs、files、databases），
 * Android 4.1 起对私有目录做强制沙箱隔离，普通应用即使知道包名也读不到。
 * 因此本检测的价值不只是"能不能拿到"，更重要的是在拿不到时给出明确原因与替代路径。
 *
 * 检测项：
 * 1. 设备上是否存在万能遥控类应用
 * 2. 本应用是否具备 root
 * 3. 能否列出目标应用的私有目录
 * 4. 能解析到多少配置条目
 */
class RemoteConfigDetector(private val context: Context) {

    fun detect(): ProbeFacts {
        val apps = findRemoteApps()
        val rooted = hasRootAccess()

        val readableDirs = mutableListOf<String>()
        var fileCount = 0
        apps.forEach { app ->
            val dir = File("/data/data/${app.packageName}")
            val children = try {
                if (dir.canRead()) dir.listFiles() else null
            } catch (e: SecurityException) {
                null
            }
            if (children != null) {
                readableDirs += dir.absolutePath
                fileCount += countConfigFiles(dir)
            }
        }

        return ProbeFacts(
            installedApps = apps,
            hasRoot = rooted,
            readableDirs = readableDirs,
            configFileCount = fileCount
        )
    }

    /**
     * 通过 LAUNCHER 意图枚举已安装应用，按包名或名称特征筛选遥控类应用。
     * 需要 Manifest 中的 <queries> 声明，否则 Android 11+ 查不到其他应用。
     */
    private fun findRemoteApps(): List<RemoteApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }

        return resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
            val matched = KNOWN_PACKAGES.any { pkg.contains(it) } ||
                label.contains("遥控") ||
                label.contains("remote", ignoreCase = true) ||
                pkg.contains("remote", ignoreCase = true)
            if (matched) RemoteApp(pkg, label) else null
        }.distinctBy { it.packageName }
            .sortedBy { it.label }
    }

    /** 只统计 shared_prefs / files / databases 三处的直接文件。 */
    private fun countConfigFiles(root: File): Int {
        val names = arrayOf("shared_prefs", "files", "databases")
        var count = 0
        names.forEach { name ->
            val dir = File(root, name)
            val files = try {
                dir.listFiles()
            } catch (e: SecurityException) {
                null
            }
            if (files != null) {
                count += files.count { it.isFile }
            }
        }
        return count
    }

    private fun hasRootAccess(): Boolean {
        if (ROOT_PATHS.any { File(it).exists() }) return true
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            output.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        /** 已知的遥控类应用包名片段。 */
        val KNOWN_PACKAGES = listOf(
            "com.duokan.phone.remotecontroller", // 小米万能遥控
            "com.xiaomi.mitv.remotecontroller",  // 小米电视遥控
            "com.tiqiaa.remote",                 // 遥控精灵
            "com.wukongtv.wkremote"              // 悟空遥控
        )

        val ROOT_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/system/sd/xbin/su"
        )
    }
}
