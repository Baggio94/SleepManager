package com.med.sleepmanager.update

object VersionComparator {
    fun isNewer(latest: String, current: String): Boolean {
        val latestVersion = parse(latest) ?: return false
        val currentVersion = parse(current) ?: return false

        for (index in 0..2) {
            val comparison = latestVersion.core[index].compareTo(currentVersion.core[index])
            if (comparison != 0) return comparison > 0
        }

        val latestPre = latestVersion.preRelease
        val currentPre = currentVersion.preRelease

        if (latestPre == null && currentPre != null) return true
        if (latestPre != null && currentPre == null) return false
        if (latestPre == null) return false

        return comparePreRelease(latestPre, currentPre.orEmpty()) > 0
    }

    private data class ParsedVersion(
        val core: List<Int>,
        val preRelease: String?
    )

    private fun parse(raw: String): ParsedVersion? {
        val normalized = raw.trim().removePrefix("v")
        val coreText = normalized.substringBefore("-")
        val parts = coreText.split(".")
        if (parts.isEmpty()) return null

        val core = List(3) { index ->
            parts.getOrNull(index)?.toIntOrNull() ?: 0
        }
        val preRelease =
            normalized.substringAfter("-", "")
                .takeIf { it.isNotBlank() }

        return ParsedVersion(core, preRelease)
    }

    private fun comparePreRelease(left: String, right: String): Int {
        val leftParts = left.split(".", "-")
        val rightParts = right.split(".", "-")
        val count = maxOf(leftParts.size, rightParts.size)

        repeat(count) { index ->
            val l = leftParts.getOrNull(index) ?: return -1
            val r = rightParts.getOrNull(index) ?: return 1
            val lNumber = l.toIntOrNull()
            val rNumber = r.toIntOrNull()

            val comparison = when {
                lNumber != null && rNumber != null -> lNumber.compareTo(rNumber)
                lNumber != null -> -1
                rNumber != null -> 1
                else -> l.compareTo(r)
            }
            if (comparison != 0) return comparison
        }
        return 0
    }
}
