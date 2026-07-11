package com.omniflow.android.ui

internal data class CategoryIconOption(val key: String, val label: String)

private val FlatCategoryIconOptions = listOf(
    CategoryIconOption("utensils", "餐饮"),
    CategoryIconOption("hot-beverage", "饮品"),
    CategoryIconOption("bread", "面包"),
    CategoryIconOption("pizza", "披萨"),
    CategoryIconOption("hamburger", "快餐"),
    CategoryIconOption("cake", "甜点"),
    CategoryIconOption("groceries", "买菜"),
    CategoryIconOption("bus", "公交"),
    CategoryIconOption("bicycle", "骑行"),
    CategoryIconOption("train", "火车"),
    CategoryIconOption("taxi", "打车"),
    CategoryIconOption("fuel", "加油"),
    CategoryIconOption("parking", "停车"),
    CategoryIconOption("shopping-bag", "购物"),
    CategoryIconOption("shirt", "服饰"),
    CategoryIconOption("beauty", "美妆"),
    CategoryIconOption("house", "住房"),
    CategoryIconOption("furniture", "家居"),
    CategoryIconOption("electricity", "电费"),
    CategoryIconOption("water", "水费"),
    CategoryIconOption("internet", "网络"),
    CategoryIconOption("phone", "话费"),
    CategoryIconOption("smartphone", "数码"),
    CategoryIconOption("education", "教育"),
    CategoryIconOption("books", "书籍"),
    CategoryIconOption("baby", "育儿"),
    CategoryIconOption("pet", "宠物"),
    CategoryIconOption("film", "电影"),
    CategoryIconOption("game", "游戏"),
    CategoryIconOption("music", "音乐"),
    CategoryIconOption("sports", "运动"),
    CategoryIconOption("fitness", "健身"),
    CategoryIconOption("camera", "摄影"),
    CategoryIconOption("party", "聚会"),
    CategoryIconOption("wine", "酒水"),
    CategoryIconOption("plane", "旅行"),
    CategoryIconOption("car", "汽车"),
    CategoryIconOption("heart-pulse", "医疗"),
    CategoryIconOption("medicine", "药品"),
    CategoryIconOption("insurance", "保险"),
    CategoryIconOption("wrench", "维修"),
    CategoryIconOption("donation", "公益"),
    CategoryIconOption("tax", "税费"),
    CategoryIconOption("refund", "退款"),
    CategoryIconOption("banknote", "工资"),
    CategoryIconOption("chart-line", "理财"),
    CategoryIconOption("briefcase-business", "工作"),
    CategoryIconOption("office", "办公"),
    CategoryIconOption("trophy", "奖金"),
    CategoryIconOption("gift", "礼物"),
)

internal val CategoryIconOptions = FlatCategoryIconOptions + FlatCategoryIconOptions.map {
    CategoryIconOption("fluent-${it.key}", "彩·${it.label}")
}

private val categoryIconKeys = CategoryIconOptions.mapTo(hashSetOf(), CategoryIconOption::key)

internal fun categoryIconKey(iconKey: String?): String =
    iconKey?.takeIf(categoryIconKeys::contains) ?: "category"
