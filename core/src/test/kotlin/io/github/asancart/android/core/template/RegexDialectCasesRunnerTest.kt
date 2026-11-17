package io.github.asancart.android.core.template

import io.github.asancart.android.core.template.fixtures.RegexDialectCases
import kotlin.test.Test
import kotlin.test.assertEquals

/** T4.24: the shared-case runner itself. The same check runs on ICU (app DeviceRegexDialectTest). */
class RegexDialectCasesRunnerTest {
    @Test
    fun `a groups text that doesn't match is a failure, also when every group listed is null`() {
        val report = RegexDialectCases(GROUPS_MISMATCH).run()
        assertEquals(GROUPS_MISMATCH_FAILURES, report.failures, report.toString())
    }

    companion object {
        /** One allowed case whose groups texts match, don't match with a group expected, and don't match with null only. */
        const val GROUPS_MISMATCH = """{"allowed": [{"construct": "runner", "pattern": "^(?<amount>\\d+)x$", "match": ["1x"], "noMatch": ["1y"],
            "groups": [{"text": "12x", "named": {"amount": "12"}}, {"text": "12y", "named": {"amount": "12"}}, {"text": "y", "named": {"amount": null}}]}],
            "refused": [], "regex": []}"""

        val GROUPS_MISMATCH_FAILURES = listOf(
            "allowed \"^(?<amount>\\d+)x$\": groups text \"12y\" doesn't match",
            "allowed \"^(?<amount>\\d+)x$\": on \"12y\": group amount is null, expected \"12\"",
            "allowed \"^(?<amount>\\d+)x$\": groups text \"y\" doesn't match",
        )
    }
}
