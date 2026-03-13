package dev.detekt.gradle.plugin.internal

internal object GradleProperties {
    // Setters for DetektExtension properties
    const val IGNORE_FAILURES = "detekt.ignoreFailures"
    const val FAIL_ON_SEVERITY = "detekt.failOnSeverity"
    const val ENABLE_COMPILER_PLUGIN = "detekt.enableCompilerPlugin"
    const val DEBUG = "detekt.debug"
    const val PARALLEL = "detekt.parallel"
    const val ALL_RULES = "detekt.allRules"
    const val BUILD_UPON_DEFAULT_CONFIG = "detekt.buildUponDefaultConfig"
    const val DISABLE_DEFAULT_RULE_SETS = "detekt.disableDefaultRuleSets"
    const val AUTO_CORRECT = "detekt.autoCorrect"

    // Disable framework-specific task registrations
    const val ANDROID_DISABLED = "detekt.android.disabled"
    const val MULTIPLATFORM_DISABLED = "detekt.multiplatform.disabled"

    const val USE_WORKER_API = "detekt.use.worker.api"
    const val DRY_RUN = "detekt-dry-run"
}
