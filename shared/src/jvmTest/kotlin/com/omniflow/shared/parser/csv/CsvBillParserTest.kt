package com.omniflow.shared.parser.csv

import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.TransactionType
import com.omniflow.shared.parser.ImportFormat
import java.io.File
import java.nio.charset.Charset
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CsvBillParserTest {
    private val parser = CsvBillParser()

    @Test
    fun parsesAlipayFixtureAndCombinesMerchantFieldsIntoNote() {
        val rows = parser.parse(
            ImportFormat.ALIPAY,
            fixture("支付宝.csv").readText(Charset.forName("GB18030")),
        ).getOrThrow()

        assertEquals(107, rows.size)
        assertEquals(Money(25_964), rows.first().amount)
        assertEquals(TransactionType.EXPENSE, rows.first().type)
        assertTrue(rows.first().note!!.contains("马克**店"))
        assertTrue(rows.first().note!!.contains("马克华菲"))
        assertNull(rows[3].type)
        assertTrue(rows[3].isExcluded)
    }

    @Test
    fun parsesJdAndMeituanFixtures() {
        val jdRows = parser.parse(ImportFormat.JD, fixture("京东.csv").readText()).getOrThrow()
        val meituanRows = parser.parse(ImportFormat.MEITUAN, fixture("美团.csv").readText()).getOrThrow()

        assertEquals(16, jdRows.size)
        assertEquals(18, meituanRows.size)
        assertEquals(Money(1_400), meituanRows.first().amount)
        assertEquals(TransactionType.EXPENSE, meituanRows.first().type)
        assertTrue(jdRows.first().note!!.contains("京东平台商户"))
    }

    private fun fixture(name: String): File {
        val file = File("../examples/$name").takeIf(File::exists) ?: File("examples/$name")
        assumeTrue("本地真实账单样例不存在：$name", file.exists())
        return file
    }
}
