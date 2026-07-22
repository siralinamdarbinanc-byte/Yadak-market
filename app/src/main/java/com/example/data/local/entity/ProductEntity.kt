package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val brand: String,
    val price: String,
    val priceNumeric: Long,
    val csvId: Int = 0,
    val barcode: String? = null
) {
    /**
     * Dynamically determines the category of the auto part based on keywords in its name.
     */
    fun determineCategory(): String {
        val lowerName = name.lowercase()
        return when {
            lowerName.contains("بلبرینگ") || lowerName.contains("رولبرینگ") || lowerName.contains("رولربیرینگ") -> "بلبرینگ و رولبرینگ"
            lowerName.contains("کلاچ") || lowerName.contains("کالچ") || lowerName.contains("دیسک و صفحه") || lowerName.contains("فلایویل") -> "کلاچ و انتقال قدرت"
            lowerName.contains("پلوس") || lowerName.contains("سرپلوس") || lowerName.contains("مشعلی") || lowerName.contains("سه شاخه") -> "پلوس و سرپلوس"
            lowerName.contains("فرمان") || lowerName.contains("جعبه فرمان") || lowerName.contains("شیر فرمان") || lowerName.contains("سیبک فرمان") -> "فرمان و هدایت"
            lowerName.contains("ترمز") || lowerName.contains("بوستر") || lowerName.contains("لنت") || lowerName.contains("سیلندر چرخ") || lowerName.contains("کالیپر") || lowerName.contains("پمپ ترمز") -> "سیستم ترمز"
            lowerName.contains("استارت") || lowerName.contains("دینام") || lowerName.contains("رله") || lowerName.contains("کلید") || lowerName.contains("کویل") || lowerName.contains("کوئیل") || lowerName.contains("سنسور") || lowerName.contains("سوئیچ") || lowerName.contains("سوییچ") || lowerName.contains("استپر") || lowerName.contains("پتانسیومتر") || lowerName.contains("شمع") || lowerName.contains("آفتامات") || lowerName.contains("لامپ") || lowerName.contains("موتور فن") || lowerName.contains("بوق") -> "برقی و الکترونیک"
            lowerName.contains("اورینگ") || lowerName.contains("واشر") || lowerName.contains("کاسه نمد") || lowerName.contains("شیم") -> "واشر و آب‌بندی"
            lowerName.contains("شیلنگ") || lowerName.contains("لوله") || lowerName.contains("منبع انبساط") || lowerName.contains("ترموستات") || lowerName.contains("رادیاتور") || lowerName.contains("واتر پمپ") || lowerName.contains("کارتر") || lowerName.contains("باک") -> "شیلنگ و سیستم خنک‌کننده"
            lowerName.contains("طبق") || lowerName.contains("سیبک") || lowerName.contains("کمک") || lowerName.contains("دسته موتور") || lowerName.contains("بوش") || lowerName.contains("ژامبون") || lowerName.contains("میل موجگیر") || lowerName.contains("سگدست") || lowerName.contains("توپی") || lowerName.contains("تویی") || lowerName.contains("شاتون") || lowerName.contains("میل لنگ") || lowerName.contains("سوپاپ") || lowerName.contains("دنده") -> "جلوبندی و تعلیق"
            lowerName.contains("دستگیره") || lowerName.contains("آینه") || lowerName.contains("آینه") || lowerName.contains("شیشه") || lowerName.contains("اهرم") || lowerName.contains("زه") || lowerName.contains("سینی") || lowerName.contains("جک") || lowerName.contains("برف پاک کن") || lowerName.contains("برف پاکن") || lowerName.contains("تیغه") || lowerName.contains("آچار") || lowerName.contains("ابرویی") || lowerName.contains("چراغ") || lowerName.contains("قاب") || lowerName.contains("ساعت") -> "بدنه و تزئینات"
            else -> "سایر قطعات"
        }
    }
}
