package com.omniflow.shared.parser

import java.io.File
import java.nio.charset.Charset
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class BillFormatDetectorTest {
    private val detector = BillFormatDetector()

    @Test
    fun detectsSupportedTextFixtures() {
        assertEquals(listOf(ImportFormat.ALIPAY), detector.detect(
            "支付宝.csv",
            fixture("支付宝.csv").readText(Charset.forName("GB18030")),
        ))
        assertEquals(listOf(ImportFormat.JD), detector.detect("京东.csv", fixture("京东.csv").readText()))
        assertEquals(listOf(ImportFormat.MEITUAN), detector.detect("美团.csv", fixture("美团.csv").readText()))
        assertEquals(listOf(ImportFormat.QINGZI), detector.detect("青子记账.json", fixture("青子记账.json").readText()))
    }

    private fun fixture(name: String): File {
        val file = File("../examples/$name").takeIf(File::exists) ?: File("examples/$name")
        assumeTrue("本地真实账单样例不存在：$name", file.exists())
        return file
    }
}
