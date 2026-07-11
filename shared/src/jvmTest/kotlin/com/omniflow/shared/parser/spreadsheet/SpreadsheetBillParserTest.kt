package com.omniflow.shared.parser.spreadsheet

import com.omniflow.shared.parser.ImportFormat
import java.io.File
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class SpreadsheetBillParserTest {
    @Test
    fun parsesWechatAndCcbFixtures() {
        assertEquals(86, SpreadsheetBillParser.parse(ImportFormat.WECHAT, fixture("微信.xlsx").readBytes()).getOrThrow().size)
        assertEquals(152, SpreadsheetBillParser.parse(ImportFormat.CCB, fixture("CCB.xls").readBytes()).getOrThrow().size)
    }

    private fun fixture(name: String): File {
        val file = File("../examples/$name").takeIf(File::exists) ?: File("examples/$name")
        assumeTrue("本地真实账单样例不存在：$name", file.exists())
        return file
    }
}
