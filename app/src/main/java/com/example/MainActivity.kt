package com.example

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.ui.viewinterop.AndroidView
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileUpload
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

import com.example.ui.theme.*
import com.example.ui.viewmodel.SortOrder
import com.example.ui.viewmodel.FilterState
import com.example.ui.viewmodel.ProductViewModel
import com.example.data.local.entity.ProductEntity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }
            MyApplicationTheme(darkTheme = isDarkTheme) {
                var showSettings by remember { mutableStateOf(false) }
                var showDebugWebView by remember { mutableStateOf(false) }
                var showCategoriesScreen by remember { mutableStateOf(false) }
                if (showDebugWebView) {
                    DebugNetworkScreen(onBack = { showDebugWebView = false })
                } else if (showCategoriesScreen) {
                    CategoriesScreen(
                        onBack = { showCategoriesScreen = false },
                        onIranKhodroClick = { showDebugWebView = true }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            BottomNavBar(
                                onSettingsClick = { showSettings = true },
                                onCategoriesClick = { showCategoriesScreen = true }
                            )
                        }
                    ) { innerPadding ->
                        SearchEngineContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            showSettings = showSettings,
                            onDismissSettings = { showSettings = false },
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = { newVal ->
                                isDarkTheme = newVal
                                prefs.edit().putBoolean("dark_theme", newVal).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ----- مدیریت درصد مارک‌آپ (عمومی + بر اساس برند) -----
fun getGeneralMarkupPercent(context: android.content.Context): Int {
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    return prefs.getInt("markup_general", 0)
}

fun setGeneralMarkupPercent(context: android.content.Context, percent: Int) {
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    prefs.edit().putInt("markup_general", percent).apply()
}

fun getBrandMarkupMap(context: android.content.Context): Map<String, Int> {
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    val raw = prefs.getString("markup_by_brand", "") ?: ""
    if (raw.isBlank()) return emptyMap()
    return raw.split("||").mapNotNull { entry ->
        val parts = entry.split("::")
        if (parts.size == 2) {
            val percent = parts[1].toIntOrNull()
            if (percent != null) parts[0] to percent else null
        } else null
    }.toMap()
}

fun setBrandMarkupMap(context: android.content.Context, map: Map<String, Int>) {
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    val raw = map.entries.joinToString("||") { "${it.key}::${it.value}" }
    prefs.edit().putString("markup_by_brand", raw).apply()
}

// محاسبه قیمت نمایشی نهایی: قانون قدیمی (زیر 80000 ضربدر 1.4) + درصد مارک‌آپ (عمومی یا برند-محور)
fun calculateDisplayPrice(numericPrice: Long, brand: String, generalPercent: Int, brandMarkupMap: Map<String, Int>): Long {
    val rawPrice = if (numericPrice < 80000) (numericPrice * 1.4).toLong() else numericPrice
    val percent = brandMarkupMap[brand] ?: generalPercent
    val withMarkup = rawPrice + (rawPrice * percent / 100)
    return (withMarkup / 1000 + if (withMarkup % 1000 > 0) 1 else 0) * 1000
}

// Data model parsed from CSV
data class Product(
    val row: Int,
    val name: String,
    val brand: String,
    val price: String,
    val numericPrice: Long
)

@Composable
fun SearchEngineContent(
    modifier: Modifier = Modifier,
    viewModel: ProductViewModel = viewModel(),
    showSettings: Boolean = false,
    onDismissSettings: () -> Unit = {},
    isDarkTheme: Boolean = false,
    onThemeToggle: (Boolean) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val allProducts by viewModel.uiState.collectAsState()
    val dynText = if (isDarkTheme) GeoTextDark else GeoText
    val dynMuted = if (isDarkTheme) GeoMutedTextDark else GeoMutedText
    val dynBorder = if (isDarkTheme) GeoBorderDark else GeoBorder
    val dynSearchBg = if (isDarkTheme) GeoSearchBarBgDark else GeoSearchBarBg
    val dynActivePillBg = if (isDarkTheme) GeoActivePillBgDark else GeoActivePillBg
    val dynActivePillText = if (isDarkTheme) GeoActivePillTextDark else GeoActivePillText
    val dynPrimary = if (isDarkTheme) GeoPrimaryDark else GeoPrimary
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast("/") ?: "فایل ناشناس"
            viewModel.previewCsv(it, fileName)
        }
    }
    val csvPreview by viewModel.csvPreview.collectAsState()

    csvPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            containerColor = if (isDarkTheme) GeoInactivePillBgDark else GeoInactivePillBg,
            shape = RoundedCornerShape(16.dp),
            title = { Text("بررسی فایل CSV", fontWeight = FontWeight.Bold, color = GeoText) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("فایل: ${preview.fileName}", fontSize = 13.sp, color = GeoText)
                    Text("محصولات جدید: ${preview.newProducts.size}", fontSize = 13.sp, color = GeoPrimary)

                    if (preview.duplicateMatches.isNotEmpty()) {
                        Text(
                            text = "موارد مشترک (${preview.duplicateMatches.size}):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(preview.duplicateMatches) { match ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = dynSearchBg),
                                    border = BorderStroke(1.dp, GeoBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(match.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GeoText)
                                        Text(match.brand, fontSize = 11.sp, color = Color.Red)
                                        Text(
                                            text = "${match.oldPrice} ← ${match.newPrice}",
                                            fontSize = 11.sp,
                                            color = if (match.oldPriceNumeric != match.newPriceNumeric) GeoPrimary else GeoMutedText
                                        )
                                    }
                                }
                            }
                        }
                        Text("قیمت موارد بالا بروزرسانی بشه؟", fontSize = 13.sp, color = GeoText)
                    }
                }
            },
            confirmButton = {
                if (preview.duplicateMatches.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.confirmImport(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("بله، بروزرسانی کن", color = Color.White) }
                        OutlinedButton(
                            onClick = { viewModel.confirmImport(false) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("فقط محصولات جدید اضافه کن") }
                        TextButton(
                            onClick = { viewModel.cancelImport() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("انصراف", color = GeoMutedText) }
                    }
                } else {
                    Button(
                        onClick = { viewModel.confirmImport(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary)
                    ) { Text("تایید و import", color = Color.White) }
                }
            },
            dismissButton = {}
        )
    }
    val DUMMY = listOf(
        Product(3001, "بلبرینگ چرخ جلو پژو405", "PSN", "17,100,500", 17100500),
        Product(3002, "بلبرینگ تقویتی چرخ جلو405", "PSN", "29,086,950", 29086950),
        Product(3003, "توپی چرخ عقب پژو405 - ترمز ضد قفلABS", "PSN", "26,621,350", 26621350),
        Product(3004, "توپی چرخ عقب ساده پژو405", "PSN", "25,634,650", 25634650),
        Product(3005, "بلبرینگ چرخ عقب206 تیپ پنج", "PSN", "29,351,450", 29351450),
        Product(3006, "بلبرینگ وسط پلوس405، سمند و پرشیا", "PSN", "3,548,900", 3548900),
        Product(3007, "رولبرینگ ژامبون بزرگ چرخ عقب پژو405", "PSN", "5,659,150", 5659150),
        Product(3008, "رولبرینگ ژامبون کوچک چرخ عقب پژو405", "PSN", "4,240,050", 4240050),
        Product(3009, "رولبرینگ سوزنی تقویتی پژو405", "PSN", "3,841,000", 3841000),
        Product(3010, "بلبرینگ چرخ عقب206 تیپ2 و3", "PSN", "14,836,150", 14836150),
        Product(3011, "بلبرینگ چرخ عقب پیکان /آردی", "PSN", "9,295,450", 9295450),
        Product(3012, "چهارشاخ گاردان پیکان", "PSN", "5,177,300", 5177300),
        Product(3013, "بلبرینگ گیربکس پیکان و آردی", "PSN", "10,496,050", 10496050),
        Product(3014, "بلبرینگ با توپی چرخ عقب دنا پنج پیچ", "PSN", "33,490,300", 33490300),
        Product(3015, "بلبرینگ دو ردیفه چرخ جلو تاراABS", "PSN", "21,641,850", 21641850),
        Product(3016, "بلبرینگ دو ردیفه چرخ عقب تارا", "PSN", "18,973,850", 18973850),
        Product(3017, "بلبرینگ چرخ جلو پراید جدید", "PSN", "11,646,050", 11646050),
        Product(3018, "رولبرینگ مخروطی چرخ جلو پراید قدیم69349", "PSN", "4,922,000", 4922000),
        Product(3021, "کیت کلاچ کامل پراید فنر دوبل", "پلاستکس", "82,300,900", 82300900),
        Product(3022, "کیت کلاچ کامل پراید پری دمپر6فنر", "پلاستکس", "84,838,490", 84838490),
        Product(3023, "کیت کلاچ کامل پری دمپر6فنر پژو405", "پلاستکس", "111,712,150", 111712150),
        Product(3024, "کیت کلاچ کامل پژو206 تیپ5", "پلاستکس", "108,903,850", 108903850),
        Product(3033, "بلبرینگ دوبل چرخ جلو پراید", "پلاستکس", "13,040,885", 13040885),
        Product(3034, "بلبرینگ دوبل چرخ جلو پژو ٥٠٤", "پلاستکس", "19,054,695", 19054695),
        Product(3038, "بلبرینگ وسط پلوس پژو405 و206", "پلاستکس", "3,827,890", 3827890),
        Product(3041, "توپی چرخ عقب پژو405 - پژو پارس- سمند - دنا -ABS", "پلاستکس", "40,410,425", 40410425),
        Product(3073, "پولی هرزگرد پژو405", "پلاستکس", "4,991,690", 4991690),
        Product(3080, "بلبرینگ تسمه تایم پژو405", "پلاستکس", "9,382,505", 9382505),
        Product(3081, "بلبرینگ تسمه تایم متحرک پژو206 تیپ5", "پلاستکس", "16,491,805", 16491805),
        Product(3087, "سرپلوس برلیانسABS-29TH 320/H330", "امیدفنر", "41,947,400", 41947400),
        Product(3088, "سرپلوس کیا ریو22 خارABS", "امیدفنر", "38,025,900", 38025900),
        Product(3090, "مشعلی گیربکس تیبا20 خارABS", "امیدفنر", "30,663,600", 30663600),
        Product(3091, "مشعلی گیربکس پژو405-34 خارABS سمت راست", "امیدفنر", "59,062,850", 59062850),
        Product(3105, "سه شاخه پلوس پژو405 - 34 خار", "امیدفنر", "6,919,550", 6919550),
        Product(3113, "سرپلوس تیبا20 خارABS", "امیدفنر", "26,008,400", 26008400),
        Product(3117, "سرپلوس پراید20 خارABS", "امیدفنر", "24,756,050", 24756050),
        Product(3126, "پلوس کامل تیبا20 خارABS سمت راست", "امیدفنر", "83,958,050", 83958050),
        Product(3130, "پلوس کامل پژو 405 - 22 خار ABS سمت چپ", "امیدفنر", "83,237,000", 83237000),
        Product(3142, "شیر فرمان قدیم 405", "امیدفنر", "75,900,000", 7590000),
        Product(3144, "شیر فرمان 206", "امیدفنر", "83,490,000", 8349000),
        Product(3145, "جعبه فرمان قدیم 405", "امیدفنر", "253,000,000", 25300000),
        Product(3148, "جعبه فرمان هیدرولیکی تیبا", "امیدفنر", "253,000,000", 25300000),
        Product(3151, "اتوماتیک استارت پراید-پیکان(یوگسالوی)", "مجد", "20,071,295", 20071295),
        Product(3158, "استارت پرایدمدل(valeo)", "مجد", "93,494,885", 93494885),
        Product(3160, "استارت پژو405", "مجد", "93,494,885", 93494885),
        Product(3162, "استب موتور( پژو405-سمند-پارس)", "مجد", "10,081,475", 10081475),
        Product(3163, "استب موتور پراید-پیکان-پژو206جدید و رانا", "مجد", "10,081,475", 10081475),
        Product(3165, "اورینگ ترموستات پرایدEURO4", "مجد", "250,010", 250010),
        Product(3170, "اورینگ درب باک پژو206", "مجد", "1,399,205", 1399205),
        Product(3182, "اورینگ سوزن انژکتور پراید زیمنس(NBR)", "مجد", "519,455", 519455),
        Product(3190, "اورینگ منیفولد هوا پرایدEURO4 (سیلیکون)", "مجد", "1,553,995", 1553995),
        Product(3201, "آرمیچر دینام پراید(بامهره)", "مجد", "51,371,650", 51371650),
        Product(3207, "آفتامات دینام پرایدEURO4", "مجد", "15,202,195", 15202195),
        Product(3231, "بازویی برف پاک کن پراید", "مجد", "4,046,850", 4046850),
        Product(3259, "بوق پراید", "مجد", "6,977,050", 6977050),
        Product(3260, "بوق پژو206", "مجد", "12,801,800", 12801800),
        Product(3267, "پتانسیومتر دریچه گازEF7-پراید- پژو جدید زیمنس(+A)", "مجد", "11,521,620", 11521620),
        Product(3284, "پمپ بنزین داخل باک پژو-پراید(مغزی پمپ بنزین)", "مجد", "16,594,385", 16594385),
        Product(3289, "پمپ بنزین کامل پژو206", "مجد", "97,511,950", 97511950),
        Product(3321, "ترموستات پراید(71درجه)", "مجد", "4,928,785", 4928785),
        Product(3324, "ترموستات پژو1800 (75 درجه)", "مجد", "5,144,065", 5144065),
        Product(3334, "تسمه تایم107 دندانه پراید -تیبا", "مجد", "11,915,955", 11915955),
        Product(3338, "تسمه دینام پژو1800 (1665)", "مجد", "14,160,640", 14160640),
        Product(3340, "تسمه دینام پژو206 تیپ5-3 (1568)", "مجد", "13,335,745", 13335745),
        Product(3356, "تیغه برف پاک کن پراید - نیسان(\"18\"-18)", "مجد", "3,803,510", 3803510),
        Product(3357, "تیغه برف پاک کن پژو206 (\"16\"-26)", "مجد", "7,961,450", 7961450),
        Product(3358, "تیغه برف پاک کن پژو405 - پارس با آب پاش(\"22\"-22)", "مجد", "6,743,485", 6743485),
        Product(3407, "درب ترموستات پژو1800 (پلیمری)", "مجد", "1,099,860", 1099860),
        Product(3408, "درب ترموستات پژو1800 (فلزی)", "مجد", "3,338,680", 3338680),
        Product(3413, "درب رادیاتور پراید پلیمری", "مجد", "1,553,765", 1553765),
        Product(3415, "درب رادیاتور پژو1800", "مجد", "3,306,710", 3306710),
        Product(3422, "درجه داخل باک انژکتور پراید", "مجد", "7,678,205", 7678205),
        Product(3423, "درجه داخل باک پژو", "مجد", "7,678,205", 7678205),
        Product(3429, "دریچه گاز کامل برقیTU5", "مجد", "125,137,595", 125137595),
        Product(3430, "دستگاه شیشه بالبر برقی راست پراید", "مجد", "44,355,385", 44355385),
        Product(3436, "دسته برف پاک کن پژو405", "مجد", "14,687,225", 14687225),
        Product(3443, "دسته راهنما پژو405", "مجد", "25,199,260", 25199260),
        Product(3470, "دینام پراید انژکتور(14v-65A)", "مجد", "134,603,015", 134603015),
        Product(3472, "دینام پژو405 مدل استام(14v-90A)", "مجد", "172,386,380", 172386380),
        Product(3531, "ست کامل سوئیچ پراید", "مجد", "41,349,860", 41349860),
        Product(3533, "ست کامل سوئیچ پژو405", "مجد", "52,146,980", 52146980),
        Product(3535, "سنسورABS چرخ عقب پژو206", "مجد", "10,147,600", 10147600),
        Product(3537, "سنسور اکسیژن پراید- تیبا(72cm) EURO4", "مجد", "29,188,150", 29188150),
        Product(3546, "سنسور کیلومتر پراید", "مجد", "4,511,105", 4511105),
        Product(3548, "سنسور کیلومتر پژو206-1800 (ته سفید)", "مجد", "4,624,725", 4624725),
        Product(3556, "سوزن انژکتور پراید-ROA", "مجد", "16,715,135", 16715135),
        Product(3634, "سوئیچ استارت پراید", "مجد", "31,883,865", 31883865),
        Product(3639, "سیلندر چرخ عقب پراید", "مجد", "4,773,075", 4773075),
        Product(3653, "شمعEURO4 پراید", "مجد", "3,644,235", 3644235),
        Product(3655, "شمع تک پلاتین انژکتور(پژو1800-پراید-،پیکان206تیپ2)", "مجد", "2,697,210", 2697210),
        Product(3658, "شیر فرمان هیدرولیک طرح جدید405 - سمند", "مجد", "63,678,375", 63678375),
        Product(3667, "صافی بنزین پراید پلیمری دو سرصاف با براکت", "مجد", "1,498,450", 1498450),
        Product(3711, "قفل سوئیچی درب پژو405-سمند- RD", "مجد", "10,369,550", 10369550),
        Product(3737, "کابل کلاچ بهینه پراید", "مجد", "3,183,085", 3183085),
        Product(3738, "کابل کلاچ پژو405", "مجد", "4,950,520", 4950520),
        Product(3740, "کاسه نمد پلوس پراید", "مجد", "1,555,950", 1555950),
        Product(3746, "کاسه نمد جلو میل لنگ پژو206", "مجد", "5,304,145", 5304145),
        Product(3773, "کلید شیشه بالابر تک پل پراید", "مجد", "3,578,685", 3578685),
        Product(3784, "کوئیل پژو206 تیپ2", "مجد", "64,009,000", 64009000),
        Product(3795, "کوئیل سمندEF7", "مجد", "19,458,805", 19458805),
        Product(3819, "لاستیک بالا رادیاتور پراید", "مجد", "400,085", 400085),
        Product(3829, "لاستیک چاکدار پژو405 (جدید)", "مجد", "1,027,065", 1027065),
        Product(3833, "لامپ تک کنتاکت", "مجد", "352,130", 352130),
        Product(3843, "لامپ چراغ جلو دو فیشH7 55 W", "مجد", "2,189,485", 2189485),
        Product(3859, "لوازم سیلندر ترمز چرخ جلو پژو405-پارس", "مجد", "1,573,775", 1573775),
        Product(3882, "مجموعه کامل موتورفن پراید", "مجد", "56,869,110", 56869110),
        Product(3883, "مجموعه کامل موتورفن پژو206", "مجد", "58,958,200", 58958200),
        Product(3890, "مغزی سوئیچ استارت پژو405-سمند-پارس", "مجد", "14,976,680", 14976680),
        Product(3904, "منبع انبساط پژو206 تیپ2", "مجد", "10,023,285", 10023285),
        Product(3924, "مهره دنده عقب پراید", "مجد", "5,638,680", 5638680),
        Product(3928, "مهره روغن پژو405", "مجد", "2,999,775", 2999775),
        Product(3934, "موتور فن پراید تک دور", "مجد", "46,146,050", 46146050),
        Product(3936, "موتور فن پژو206", "مجد", "56,228,790", 56228790),
        Product(3956, "واشر درب سوپاپEF7", "مجد", "6,394,345", 6394345),
        Product(3961, "واشر درب سوپاپ پژو1800", "مجد", "2,064,480", 2064480),
        Product(3966, "واشر سرسیلندر پراید1/5 میل(دونیت)", "مجد", "10,196,475", 10196475),
        Product(3996, "وایر شمع پراید انژکتور زیمنس", "مجد", "8,552,665", 8552665),
        Product(3999, "وایر شمع پژو1800-CNG انژکتور(تمام سیلیکون)", "مجد", "8,708,720", 8708720),
        Product(4015, "ابرویی زیر چراغ جلو پراید سفید -چپ", "JPA", "3,026,800", 3026800),
        Product(4041, "اتوماتیک استارت پراید - پیکان(طرح ولئو) دو زغاله", "JPA", "17,774,400", 17774400),
        Product(4058, "استپر موتور پراید و پیکان-زیمنس", "JPA", "10,278,240", 10278240),
        Product(4127, "آچار چرخ پژو405 و پارس و سمند", "JPA", "2,215,360", 2215360),
        Product(4134, "آینه برقی ساینا و کوئیک -چپ", "JPA", "23,892,400", 23892400),
        Product(4138, "آینه پژو206 مکانیکی سمت چپ با فلپ", "JPA", "17,748,640", 17748640),
        Product(4140, "آینه پژو405 -برقی سمت چپ با لچکی", "JPA", "17,156,160", 17156160),
        Product(4211, "بلبرینگ تسمه تایمL90 -ساعتی", "JPA", "22,102,080", 22102080),
        Product(4225, "بلبرینگ چرخ جلو206 تیپ2 و ساینا(BAH -0051B)", "JPA", "15,134,000", 15134000),
        Product(4226, "بلبرینگ چرخ جلو405 (GB40574)", "JPA", "16,666,720", 16666720),
        Product(4229, "بلبرینگ چرخ جلو پراید، تیبا، ساینا و کوئیک", "JPA", "11,566,240", 11566240),
        Product(4265, "بلبرینگ کلاچ پژو405 (RAC2091)", "JPA", "7,998,480", 7998480),
        Product(4289, "بوستر ترمز پراید -ABS", "JPA", "46,458,160", 46458160),
        Product(4291, "بوستر ترمز پیکان طلایی", "JPA", "45,157,280", 45157280),
        Product(4315, "بوش طبق جناقی مثلثی405 و سمند", "JPA", "5,358,080", 5358080),
        Product(4330, "پایه آنتن سقفی پژو405 ،206 ،سمند و پراید", "JPA", "680,064", 680064),
        Product(4396, "پمپ هیدرولیک فرمان پژو206 و رانا با مخزن", "JPA", "121,316,720", 121316720),
        Product(4405, "پولی سر میل لنگ206 -TU5", "JPA", "14,103,600", 14103600),
        Product(4406, "پولی سرمیل لنگ405 -1800cc", "JPA", "16,666,720", 16666720),
        Product(4426, "پیچ سرسیلندر405 (تمام رزوه)", "JPA", "1,313,760", 1313760),
        Product(4445, "ترموستات206 -83 درجه با محفظهPPS", "JPA", "5,499,760", 5499760),
        Product(4454, "تسمه تایمEF7 سمند(127RU24 (HNBR", "JPA", "31,620,400", 31620400),
        Product(4456, "تسمه تایم پراید -تیبا -ساینا -کوئیک و شاهین", "JPA", "10,613,120", 10613120),
        Product(4457, "تسمه تایم پژو206 تیپ5 (134RU25.4 (HNBR", "JPA", "31,556,000", 31556000),
        Product(4468, "تسمه دینام و کولر 206 تیپ 5 و 6-6PK1575", "JPA", "12,480,720", 12480720),
        Product(4471, "تسمه دینام و کولر پژو 405-6PK1663", "JPA", "12,854,240", 12854240),
        Product(4483, "تویی چرخ جلو پژو 405 و TU5 206 و دنا", "JPA", "9,363,760", 9363760),
        Product(4523, "جعبه فرمان هیدرولیک پراید", "JPA", "253,000,000", 253000000),
        Product(4524, "جعبه فرمان هیدرولیک پژو 206", "JPA", "278,300,000", 278300000),
        Product(4536, "جک گازی صندوق عقب پژو 206 صندوق دار و رانا", "JPA", "8,887,200", 8887200),
        Product(4544, "چراغ راهنمای گلگیر پژو 206 (سفید)", "JPA", "973,728", 973728),
        Product(4599, "درب سوپاپ(قالپاق سوپاپ) پژو206 TU5 -دود", "JPA", "14,026,320", 14026320),
        Product(4602, "درب سوپاپ(قالپاق سوپاپ) کامل پژو405", "JPA", "33,655,440", 33655440),
        Product(4617, "دریچه هوای کامل الکترونیکی تیبا و کوئیک", "JPA", "118,418,720", 118418720),
        Product(4630, "دستگیره بیرونی درب پراید -چپ", "JPA", "1,391,040", 1391040),
        Product(4632, "دستگیره بیرونی درب پژو206 جلو -چپ", "JPA", "3,567,760", 3567760),
        Product(4656, "دستگیره داخلی درب پژو206 -چپ", "JPA", "1,790,320", 1790320),
        Product(4658, "دستگیره داخلی درب پژو405 -چپ", "JPA", "1,738,800", 1738800),
        Product(4691, "دسته برف پاک کنL90", "JPA", "24,652,320", 24652320),
        Product(4699, "دسته راهنما و برف پاک کن پراید", "JPA", "38,433,920", 38433920),
        Product(4710, "دسته موتور پنجه ای پژو405", "JPA", "6,079,360", 6079360),
        Product(4717, "دسته موتور دو سر پیچ پژو 405", "JPA", "7,702,240", 7702240),
        Product(4777, "دوشاخه کلاچ بهینه پژو206 و رانا -TU5", "JPA", "6,749,120", 6749120),
        Product(4789, "دیسک ترمز چرخ جلو405", "JPA", "18,701,760", 18701760),
        Product(4791, "دیسک ترمز چرخ جلو پراید", "JPA", "10,278,240", 10278240),
        Product(4797, "دیسک و صفحهBE رانا - پژو و پارسTU5", "JPA", "78,812,720", 78812720),
        Product(4799, "دیسک و صفحه پراید(با بلبرینگ)", "JPA", "62,867,280", 62867280),
        Product(4802, "دیسک و صفحه پژو405 - سمندEF7", "JPA", "70,208,880", 70208880),
        Product(4807, "رادیاتور آب پراید", "JPA", "35,935,200", 35935200),
        Product(4813, "رام زیر موتور پژو206", "JPA", "87,983,280", 87983280),
        Product(4833, "رله دوبل پژو و پراید", "JPA", "6,633,200", 6633200),
        Product(4839, "رله فن سبز پژو206 -5 فیش", "JPA", "2,730,560", 2730560),
        Product(4896, "سردنده پژو206", "JPA", "1,957,760", 1957760),
        Product(4902, "سرسیلندر206 - تیپ2", "JPA", "222,411,840", 222411840),
        Product(4906, "سرسیلندر پژو405 ( دوگانه معمولی)", "JPA", "228,645,760", 228645760),
        Product(4914, "سگدست206 - تیپ5 -ABS (چپ)", "JPA", "21,419,440", 21419440),
        Product(4916, "سگدست405 - راست(ABS)", "JPA", "18,688,880", 18688880),
        Product(4939, "سنسور اکسیژن پراید زیمنس - سیم کوتاه", "JPA", "31,826,480", 31826480),
        Product(4944, "سنسور اکسیژن سمند و پراید زیمنس", "JPA", "31,826,480", 31826480),
        Product(4950, "سنسور دور موتور پژو405 - پراید", "JPA", "6,053,600", 6053600),
        Product(4954, "سنسور سرعت سنج پژو405 و206", "JPA", "5,448,240", 5448240),
        Product(4971, "سوزن انژکتور پراید زیمنس", "JPA", "18,457,040", 18457040),
        Product(4983, "سوزن انژکتور سمندEF7 و پژو206", "JPA", "18,457,040", 18457040),
        Product(4997, "سیبک فرمان پژو206 و رانا -چپ", "JPA", "5,409,600", 5409600),
        Product(5024, "سینی زیر موتور پژو405 و سمند", "JPA", "35,845,040", 35845040),
        Product(5030, "شاتون پژو206 و رانا -TU5", "JPA", "11,798,080", 11798080),
        Product(5031, "شاتون پژو405", "JPA", "11,424,560", 11424560),
        Product(5101, "شیلنگ بخاری405 - خروجی(بلند)", "JPA", "11,360,160", 11360160),
        Product(5142, "شیلنگ ترمز چرخ جلو پراید", "JPA", "15,056,720", 15056720),
        Product(5144, "شیلنگ ترمز چرخ جلو پژو 405", "JPA", "15,301,440", 15301440),
        Product(5163, "شیلنگ رادیاتور206 (TU3) -بالا", "JPA", "4,649,680", 4649680),
        Product(5167, "شیلنگ رادیاتور405 کلاسیک -پایین", "JPA", "10,780,560", 10780560),
        Product(5174, "شیلنگ رادیاتور پراید - بالا", "JPA", "2,447,200", 2447200),
        Product(5181, "شیلنگ رادیاتور تیبا -بالا", "JPA", "2,975,280", 2975280),
        Product(5192, "شیلنگ رادیاتور سمندEF7 - بالا", "JPA", "4,894,400", 4894400),
        Product(5217, "شیلنگ هیدرولیک فرمان فشار قوی پژو405", "JPA", "75,979,120", 75979120),
        Product(5270, "طبق چرخ جلو405 -چپ", "JPA", "43,328,320", 43328320),
        Product(5272, "طبق چرخ جلو پراید(بوش لاستیکی)", "JPA", "12,223,120", 12223120),
        Product(5274, "طبق چرخ جلو تیبا -چپ", "JPA", "20,337,520", 20337520),
        Product(5358, "کابل کلاچ206 مدل قبل96", "JPA", "8,114,400", 8114400),
        Product(5362, "کابل کلاچ پراید(سرسبی)", "JPA", "4,224,640", 4224640),
        Product(5375, "کابل کلاچ سمندR2 و پارس با موتورTU5", "JPA", "9,685,760", 9685760),
        Product(5380, "کابل مثبت باطری پژو206 TU5", "JPA", "24,781,120", 24781120),
        Product(5395, "کارتر405 و پرشیا و سمند", "JPA", "39,322,640", 39322640),
        Product(5404, "کاسه چرخ عقب405", "JPA", "20,260,240", 20260240),
        Product(5409, "کاسه نمد اویل پمپ(سر میل لنگ) پراید", "JPA", "1,468,320", 1468320),
        Product(5430, "کاسه نمد ساق سوپاپ405", "JPA", "5,847,520", 5847520),
        Product(5451, "کاسه نمد میل لنگ405 -جلو", "JPA", "2,782,080", 2782080),
        Product(5455, "کاسه نمد میل لنگ405 - عقب پهن", "JPA", "9,840,320", 9840320),
        Product(5512, "کلید شیشه بالابر پژو SLX- جلو چپ", "JPA", "10,200,960", 10200960),
        Product(5516, "کلید شیشه بالابر تیبا- تک پل", "JPA", "2,949,520", 2949520),
        Product(5520, "کلید شیشه بالابر جلو و تنظیم آینه", "JPA", "12,673,920", 12673920),
        Product(5559, "کویل405 و پراید ساژم", "JPA", "29,186,080", 29186080),
        Product(5573, "کویل تیبا و ساینا و پراید جدید", "JPA", "27,679,120", 27679120),
        Product(5577, "کیت تسمه تایمینگ پژو405", "JPA", "22,784,720", 22784720),
        Product(5753, "مپ سنسور پراید و پژو 405", "JPA", "11,521,620", 11521620),
        Product(5833, "مغزی پمپ بنزین پراید و پژو405", "JPA", "18,457,040", 18457040),
        Product(5869, "موتور و مکانیزم کامل برف پاک کن پژو405", "JPA", "48,931,120", 48931120),
        Product(5938, "میل لنگ پراید", "JPA", "62,905,920", 62905920),
        Product(5939, "میل لنگ پژو405", "JPA", "85,008,000", 85008000),
        Product(5940, "میل لنگ پیکان", "JPA", "75,992,000", 75992000),
        Product(5964, "واتر پمپ پراید", "JPA", "25,618,320", 25618320),
        Product(5965, "واتر پمپ پژو405", "JPA", "27,460,160", 27460160),
        Product(5968, "واتر پمپ سمندEF7", "JPA", "26,919,200", 26919200),
        Product(5980, "واشر درب سوپاپ405", "JPA", "1,777,440", 1777440),
        Product(5981, "واشر درب سوپاپ پراید(سیلیکونی)", "JPA", "2,485,840", 2485840),
        Product(5985, "واشر درب سوپاپ تیبا -کوئیک و شاهین", "JPA", "1,777,440", 1777440),
        Product(5990, "واشر سرسیلندر پراید - یورو4", "JPA", "4,611,040", 4611040),
        Product(5996, "واشر سرسیلندر تیبا", "JPA", "3,838,240", 3838240),
        Product(6017, "وایر شمع405 (سیلیکونی)", "JPA", "7,715,120", 7715120),
        Product(6021, "وایر شمع پراید انژکتور زیمنس", "JPA", "7,444,640", 7444640),
        Product(6030, "وایر شمع تیبا -(سیلیکونی)", "JPA", "7,728,000", 7728000),
        Product(6034, "وایر شمع سمند E-F7", "JPA", "3,464,720", 3464720),
        Product(6042, "هواکش کامل پژو 206 تیپ 5", "JPA", "13,111,840", 13111840)
    )
    val rawProducts = allProducts.map { e -> Product(e.id, e.name, e.brand, e.price, e.priceNumeric) }

    // Filters and search logic
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("همه دسته‌ها") }
    var selectedBrand by remember { mutableStateOf("همه برندها") }
    var minPriceInput by remember { mutableStateOf("") }
    var maxPriceInput by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.NAME_ASC) }
    var showAdvancedFilters by remember { mutableStateOf(false) }
    BackHandler(enabled = showAdvancedFilters) { showAdvancedFilters = false }

    // Unique Brands
    val brandsList = remember { listOf("همه برندها") + rawProducts.map { it.brand }.distinct().sorted() }

    // Dynamic Categories helper based on names
    fun determineCategory(name: String): String {
        val lowerName = name.lowercase()
        return when {
            lowerName.contains("بلبرینگ") || lowerName.contains("رولبرینگ") || lowerName.contains("رولربیرینگ") -> "بلبرینگ و رولبرینگ"
            lowerName.contains("کلاچ") || lowerName.contains("کالچ") || lowerName.contains("دیسک") || lowerName.contains("فلایویل") -> "کلاچ و انتقال قدرت"
            lowerName.contains("پلوس") || lowerName.contains("سرپلوس") || lowerName.contains("مشعلی") || lowerName.contains("سه شاخه") -> "پلوس و سرپلوس"
            lowerName.contains("فرمان") || lowerName.contains("جعبه فرمان") || lowerName.contains("شیر فرمان") || lowerName.contains("سیبک فرمان") -> "فرمان و هدایت"
            lowerName.contains("ترمز") || lowerName.contains("بوستر") || lowerName.contains("لنت") || lowerName.contains("سیلندر") || lowerName.contains("کالیپر") || lowerName.contains("پمپ") -> "سیستم ترمز"
            lowerName.contains("استارت") || lowerName.contains("دینام") || lowerName.contains("رله") || lowerName.contains("کلید") || lowerName.contains("کویل") || lowerName.contains("کوئیل") || lowerName.contains("سنسور") || lowerName.contains("سوئیچ") || lowerName.contains("سوییچ") || lowerName.contains("استپر") || lowerName.contains("پتانسیومتر") || lowerName.contains("شمع") || lowerName.contains("آفتامات") || lowerName.contains("لامپ") || lowerName.contains("فن") || lowerName.contains("بوق") -> "برقی و الکترونیک"
            lowerName.contains("اورینگ") || lowerName.contains("واشر") || lowerName.contains("کاسه نمد") || lowerName.contains("شیم") -> "واشر و آب‌بندی"
            lowerName.contains("شیلنگ") || lowerName.contains("لوله") || lowerName.contains("منبع") || lowerName.contains("ترموستات") || lowerName.contains("رادیاتور") || lowerName.contains("واتر") || lowerName.contains("کارتر") || lowerName.contains("باک") -> "سیستم خنک‌کننده"
            lowerName.contains("طبق") || lowerName.contains("سیبک") || lowerName.contains("کمک") || lowerName.contains("دسته موتور") || lowerName.contains("بوش") || lowerName.contains("ژامبون") || lowerName.contains("موجگیر") || lowerName.contains("سگدست") || lowerName.contains("توپی") || lowerName.contains("تویی") || lowerName.contains("شاتون") -> "جلوبندی و تعلیق"
            else -> "سایر قطعات"
        }
    }

    val categoriesList = listOf(
        "همه دسته‌ها",
        "بلبرینگ و رولبرینگ",
        "کلاچ و انتقال قدرت",
        "پلوس و سرپلوس",
        "فرمان و هدایت",
        "سیستم ترمز",
        "برقی و الکترونیک",
        "واشر و آب‌بندی",
        "سیستم خنک‌کننده",
        "جلوبندی و تعلیق",
        "سایر قطعات"
    )

    // Filtered list
    fun normalizeQuery(q: String): String {
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        var result = q
        persian.forEachIndexed { i, c -> result = result.replace(c, '0' + i) }
        arabic.forEachIndexed { i, c -> result = result.replace(c, '0' + i) }
        return result
    }

    val normalizedQuery = normalizeQuery(searchQuery)

    val filteredProducts = remember(normalizedQuery, selectedCategory, selectedBrand, minPriceInput, maxPriceInput, sortOrder) {
        rawProducts.filter { product ->
            val matchesQuery = if (normalizedQuery.isBlank()) true else {
                val tokens = normalizedQuery.trim().split(Regex("\\s+"))
                val searchIn = normalizeQuery(product.name) + " " + normalizeQuery(product.brand)
                tokens.all { token -> searchIn.contains(token) }
            }
            val productCat = determineCategory(product.name)
            val matchesCategory = selectedCategory == "همه دسته‌ها" || productCat == selectedCategory
            val matchesBrand = selectedBrand == "همه برندها" || product.brand == selectedBrand
            
            val minP = minPriceInput.toLongOrNull()
            val maxP = maxPriceInput.toLongOrNull()
            val matchesMin = minP == null || product.numericPrice >= minP
            val matchesMax = maxP == null || product.numericPrice <= maxP
            
            matchesQuery && matchesCategory && matchesBrand && matchesMin && matchesMax
        }.sortedWith { p1, p2 ->
            when (sortOrder) {
                SortOrder.NAME_ASC -> p1.name.compareTo(p2.name)
                SortOrder.PRICE_ASC -> p1.numericPrice.compareTo(p2.numericPrice)
                SortOrder.PRICE_DESC -> p2.numericPrice.compareTo(p1.numericPrice)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Import CSV Button

        // App Branding / Header
        Text(
            text = "یدک مارکت (زینلی)",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = GeoPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Search Bar Fill style
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input"),
            placeholder = { Text("نام کالا یا برند را جستجو کنید...", fontSize = 14.sp) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = dynSearchBg,
                unfocusedContainerColor = dynSearchBg,
                focusedIndicatorColor = GeoPrimary,
                unfocusedIndicatorColor = GeoBorder
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Toggle Filter Button and Sort selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = BoxArrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showAdvancedFilters = !showAdvancedFilters },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showAdvancedFilters) dynPrimary else dynSearchBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("toggle_filters_button")
            ) {
                Text(
                    text = if (showAdvancedFilters) "پنهان کردن فیلترها" else "فیلترهای پیشرفته",
                    fontSize = 12.sp,
                    color = GeoText
                )
            }

            // Quick Sort Button
            Button(
                onClick = {
                    sortOrder = when (sortOrder) {
                        SortOrder.NAME_ASC -> SortOrder.PRICE_ASC
                        SortOrder.PRICE_ASC -> SortOrder.PRICE_DESC
                        SortOrder.PRICE_DESC -> SortOrder.NAME_ASC
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = dynSearchBg),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = when (sortOrder) {
                        SortOrder.NAME_ASC -> "مرتب‌سازی: الفبا"
                        SortOrder.PRICE_ASC -> "قیمت: صعودی"
                        SortOrder.PRICE_DESC -> "قیمت: نزولی"
                    },
                    fontSize = 12.sp,
                    color = GeoText
                )
            }
        }

        // Advanced filter sliders/menus
        if (showAdvancedFilters) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = dynSearchBg),
                border = BorderStroke(1.dp, GeoBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Category Selector
                    Text("انتخاب دسته‌بندی کالا:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GeoText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoriesList.forEach { category ->
                            val isSelected = selectedCategory == category
                            Surface(
                                onClick = { selectedCategory = category },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) dynActivePillBg else dynSearchBg,
                                border = BorderStroke(1.dp, if (isSelected) GeoPrimary else GeoBorder)
                            ) {
                                Text(
                                    text = category,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isSelected) dynActivePillText else dynMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // Brand Selector
                    Text("فیلتر بر اساس برند:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GeoText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        brandsList.forEach { brand ->
                            val isSelected = selectedBrand == brand
                            Surface(
                                onClick = { selectedBrand = brand },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) dynActivePillBg else dynSearchBg,
                                border = BorderStroke(1.dp, if (isSelected) GeoPrimary else GeoBorder)
                            ) {
                                Text(
                                    text = brand,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isSelected) dynActivePillText else dynMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // Price Range input
                    Text("محدوده قیمت (ریال):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GeoText)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = minPriceInput,
                            onValueChange = { minPriceInput = it.filter { char -> char.isDigit() } },
                            label = { Text("از (ریال)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GeoPrimary,
                                unfocusedBorderColor = GeoBorder
                            )
                        )

                        OutlinedTextField(
                            value = maxPriceInput,
                            onValueChange = { maxPriceInput = it.filter { char -> char.isDigit() } },
                            label = { Text("تا (ریال)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GeoPrimary,
                                unfocusedBorderColor = GeoBorder
                            )
                        )
                    }

                    // Reset Filter Button
                    TextButton(
                        onClick = {
                            searchQuery = ""
                            selectedCategory = "همه دسته‌ها"
                            selectedBrand = "همه برندها"
                            minPriceInput = ""
                            maxPriceInput = ""
                            sortOrder = SortOrder.NAME_ASC
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "ریست", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("پاک کردن تمام فیلترها", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // تعداد نتایج
        if (normalizedQuery.isNotBlank() || selectedCategory != "همه دسته‌ها" || selectedBrand != "همه برندها") {
            Text(
                text = "${filteredProducts.size} مورد یافت شد",
                fontSize = 12.sp,
                color = GeoMutedText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // --- 5. RESULTS LIST OR EMPTY STATE ---
        if (filteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("empty_state"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بدون نتیجه",
                        tint = GeoMutedText,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "کالایی با این مشخصات یافت نشد!",
                        fontWeight = FontWeight.Bold,
                        color = GeoMutedText
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("product_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredProducts, key = { it.row }) { product ->
                    ProductRowCard(product = product, category = determineCategory(product.name), onClick = { selectedProduct = product })
                }
            }
        }
    }
    if (showSettings) {
        val csvFiles by viewModel.csvFiles.collectAsState()
        var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
        var showClearAllConfirm by remember { mutableStateOf(false) }
        val settingsContext2 = androidx.compose.ui.platform.LocalContext.current

        AlertDialog(
            onDismissRequest = { onDismissSettings() },
            containerColor = if (isDarkTheme) GeoInactivePillBgDark else GeoInactivePillBg,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "تنظیمات", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = dynText)
                    IconButton(onClick = { onDismissSettings() }) { Icon(Icons.Default.Close, contentDescription = "بستن", tint = dynText) }
                }
            },
            text = {
                val settingsContext = androidx.compose.ui.platform.LocalContext.current
                val totalProducts = csvFiles.sumOf { it.productCount }
                var generalMarkupInput by remember { mutableStateOf(getGeneralMarkupPercent(settingsContext).toString()) }
                var brandMarkupMap by remember { mutableStateOf(getBrandMarkupMap(settingsContext)) }
                var selectedBrandsForMarkup by remember { mutableStateOf(brandMarkupMap.keys) }
                val allBrandsForMarkup by viewModel.availableBrands.collectAsState()

                var expandedData by remember { mutableStateOf(true) }
                var expandedTheme by remember { mutableStateOf(false) }
                var expandedMarkup by remember { mutableStateOf(false) }
                var expandedFiles by remember { mutableStateOf(false) }

                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // ---- بخش ۱: مدیریت داده ----
                    Surface(
                        onClick = { expandedData = !expandedData },
                        shape = RoundedCornerShape(10.dp),
                        color = dynSearchBg,
                        border = BorderStroke(1.dp, dynBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = dynPrimary, modifier = Modifier.size(18.dp))
                                Text("مدیریت داده", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dynText)
                            }
                            Icon(if (expandedData) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = dynMuted)
                        }
                    }
                    if (expandedData) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp, end = 4.dp)) {
                            Button(
                                onClick = { csvLauncher.launch("text/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = dynPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = "import", modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ورود CSV جدید", fontSize = 14.sp, color = Color.White)
                            }
                            OutlinedButton(
                                onClick = { showClearAllConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                border = BorderStroke(1.dp, Color.Red),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "clear all", modifier = Modifier.size(16.dp), tint = Color.Red)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("پاک کردن کامل دیتابیس", fontSize = 14.sp, color = Color.Red)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("تعداد کل کالاهای وارد شده:", fontSize = 13.sp, color = dynMuted)
                                Text("$totalProducts کالا", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dynPrimary)
                            }
                            if (csvFiles.isNotEmpty()) {
                                HorizontalDivider(color = dynBorder)
                                Text("فایل‌های وارد شده:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dynText)
                                csvFiles.forEach { csv ->
                                    val displayName = csv.fileName
                                        .removePrefix("document:")
                                        .replace("_", " ")
                                        .replace("-", " ")
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = dynSearchBg),
                                        border = BorderStroke(1.dp, dynBorder),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dynText)
                                                Text(text = "${csv.productCount} محصول", fontSize = 11.sp, color = dynMuted)
                                            }
                                            IconButton(onClick = { showDeleteConfirm = csv.id }) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---- بخش ۲: ظاهر ----
                    Surface(
                        onClick = { expandedTheme = !expandedTheme },
                        shape = RoundedCornerShape(10.dp),
                        color = dynSearchBg,
                        border = BorderStroke(1.dp, dynBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, contentDescription = null, tint = dynPrimary, modifier = Modifier.size(18.dp))
                                Text("ظاهر برنامه", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dynText)
                            }
                            Icon(if (expandedTheme) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = dynMuted)
                        }
                    }
                    if (expandedTheme) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isDarkTheme) "تم تاریک" else "تم روشن", fontSize = 13.sp, color = dynText)
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { onThemeToggle(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = dynPrimary, checkedTrackColor = dynActivePillBg)
                            )
                        }
                    }

                    // ---- بخش ۳: درصد مارک‌آپ ----
                    Surface(
                        onClick = { expandedMarkup = !expandedMarkup },
                        shape = RoundedCornerShape(10.dp),
                        color = dynSearchBg,
                        border = BorderStroke(1.dp, dynBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Percent, contentDescription = null, tint = dynPrimary, modifier = Modifier.size(18.dp))
                                Text("درصد مارک‌آپ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dynText)
                            }
                            Icon(if (expandedMarkup) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = dynMuted)
                        }
                    }
                    if (expandedMarkup) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp, end = 4.dp)) {
                            OutlinedTextField(
                                value = generalMarkupInput,
                                onValueChange = {
                                    generalMarkupInput = it.filter { c -> c.isDigit() }
                                    setGeneralMarkupPercent(settingsContext, generalMarkupInput.toIntOrNull() ?: 0)
                                },
                                label = { Text("درصد عمومی (همه برندها)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = dynPrimary, unfocusedBorderColor = dynBorder)
                            )
                            Text("یا برای برند خاص درصد متفاوت بذار:", fontSize = 12.sp, color = dynMuted)
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                allBrandsForMarkup.filter { it != "همه برندها" }.forEach { brandName ->
                                    val isSelected = selectedBrandsForMarkup.contains(brandName)
                                    Surface(
                                        onClick = {
                                            selectedBrandsForMarkup = if (isSelected) selectedBrandsForMarkup - brandName else selectedBrandsForMarkup + brandName
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) dynActivePillBg else dynSearchBg,
                                        border = BorderStroke(1.dp, if (isSelected) dynPrimary else dynBorder)
                                    ) {
                                        Text(text = brandName, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.bodySmall.copy(color = if (isSelected) dynActivePillText else dynMuted, fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                            selectedBrandsForMarkup.forEach { brandName ->
                                var brandPercentInput by remember(brandName) { mutableStateOf((brandMarkupMap[brandName] ?: 0).toString()) }
                                OutlinedTextField(
                                    value = brandPercentInput,
                                    onValueChange = { newVal ->
                                        brandPercentInput = newVal.filter { c -> c.isDigit() }
                                        val updatedMap = brandMarkupMap.toMutableMap()
                                        updatedMap[brandName] = brandPercentInput.toIntOrNull() ?: 0
                                        brandMarkupMap = updatedMap
                                        setBrandMarkupMap(settingsContext, updatedMap)
                                    },
                                    label = { Text("درصد برای: $brandName", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = dynPrimary, unfocusedBorderColor = dynBorder)
                                )
                            }
                        }
                    }

                    if (csvFiles.isNotEmpty()) {
                        HorizontalDivider(color = GeoBorder)
                        Text("فایل‌های وارد شده:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dynText)
                        csvFiles.forEach { csv ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = dynSearchBg),
                                border = BorderStroke(1.dp, GeoBorder),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = csv.fileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dynText)
                                        Text(text = "${csv.productCount} محصول", fontSize = 11.sp, color = dynMuted)
                                    }
                                    IconButton(onClick = { showDeleteConfirm = csv.id }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    } else {
                        Text("هنوز فایل CSV وارد نشده", fontSize = 12.sp, color = GeoMutedText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {}
        )

        if (showClearAllConfirm) {
            AlertDialog(
                onDismissRequest = { showClearAllConfirm = false },
                containerColor = if (isDarkTheme) GeoInactivePillBgDark else GeoInactivePillBg,
                shape = RoundedCornerShape(16.dp),
                title = { Text("پاک کردن کامل دیتابیس", fontWeight = FontWeight.Bold, color = GeoText) },
                text = { Text("تمام محصولات و فایل‌های CSV ثبت‌شده حذف می‌شن. این کار قابل بازگشت نیست. مطمئنی؟", color = GeoText) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.clearAllData(); showClearAllConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("بله، پاک کن", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllConfirm = false }) { Text("انصراف", color = GeoPrimary) }
                }
            )
        }

        showDeleteConfirm?.let { csvId ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                containerColor = if (isDarkTheme) GeoInactivePillBgDark else GeoInactivePillBg,
                shape = RoundedCornerShape(16.dp),
                title = { Text("حذف فایل CSV", fontWeight = FontWeight.Bold, color = GeoText) },
                text = { Text("این فایل و تمام محصولاتش حذف می‌شن. مطمئنی؟", color = GeoText) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.deleteCsv(csvId); showDeleteConfirm = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("حذف", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) { Text("انصراف", color = GeoPrimary) }
                }
            )
        }
    }
    // Product Detail Dialog
    selectedProduct?.let { product ->
        val contextForDialog = androidx.compose.ui.platform.LocalContext.current
        val df2 = DecimalFormat("#,###")
        val generalPercent2 = remember { getGeneralMarkupPercent(contextForDialog) }
        val brandMarkupMap2 = remember { getBrandMarkupMap(contextForDialog) }
        val roundedPrice2 = calculateDisplayPrice(product.numericPrice, product.brand, generalPercent2, brandMarkupMap2)
        AlertDialog(
            onDismissRequest = { selectedProduct = null },
            containerColor = if (isDarkTheme) GeoInactivePillBgDark else GeoInactivePillBg,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "جزئیات محصول", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GeoText)
                    IconButton(onClick = { selectedProduct = null }) {
                        Icon(Icons.Default.Close, contentDescription = "بستن", tint = dynText)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = product.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeoText,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = product.brand,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = GeoBorder)
                    Text(text = "${df2.format(roundedPrice2)} ریال", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GeoPrimary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = product.row.toString(), fontSize = 12.sp, color = GeoText)
                        Text(text = "کد محصول:", fontSize = 12.sp, color = GeoMutedText)
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun ProductRowCard(product: Product, category: String, onClick: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val df = DecimalFormat("#,###")
    val generalPercent = remember { getGeneralMarkupPercent(context) }
    val brandMarkupMap = remember { getBrandMarkupMap(context) }
    val roundedPrice = calculateDisplayPrice(product.numericPrice, product.brand, generalPercent, brandMarkupMap)
    val displayPriceToman = df.format(roundedPrice)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.row}")
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GeoInactivePillBg),
        border = BorderStroke(1.dp, GeoBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Icon Block
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GeoAccentBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (category) {
                        "بلبرینگ و رولبرینگ" -> Icons.Filled.Build
                        "کلاچ و انتقال قدرت" -> Icons.Filled.Settings
                        "پلوس و سرپلوس" -> Icons.Filled.Refresh
                        "فرمان و هدایت" -> Icons.Filled.Navigation
                        "سیستم ترمز" -> Icons.Filled.Warning
                        "برقی و الکترونیک" -> Icons.Filled.Bolt
                        "واشر و آب‌بندی" -> Icons.Filled.Lock
                        "سیستم خنک‌کننده" -> Icons.Filled.AcUnit
                        "جلوبندی و تعلیق" -> Icons.Filled.HomeRepairService
                        else -> Icons.Filled.Build
                    },
                    contentDescription = category,
                    tint = GeoAccentText,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Product Details Block
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = GeoText
                    ),
                    textAlign = TextAlign.Right,
                    maxLines = 2
                )
                Text(
                    text = product.brand,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Price Block
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$displayPriceToman ریال",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = GeoPrimary
                    )
                )
                Text(
                    text = "",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = GeoMutedText,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
fun BottomNavBar(onSettingsClick: () -> Unit = {}, onCategoriesClick: () -> Unit = {}) {
    NavigationBar(
        containerColor = GeoBottomNavBg,
        tonalElevation = 8.dp,
        windowInsets = NavigationBarDefaults.windowInsets,
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(GeoActivePillBg)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "خانه",
                        tint = GeoActivePillText
                    )
                }
            },
            label = { Text("خانه", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        )

        NavigationBarItem(
            selected = false,
            onClick = { onCategoriesClick() },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Category,
                    contentDescription = "دسته‌ها",
                    tint = GeoMutedText
                )
            },
            label = { Text("دسته‌ها", fontSize = 11.sp, color = GeoMutedText) }
        )

        NavigationBarItem(
            selected = false,
            onClick = {},
            icon = {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "تاریخچه",
                    tint = GeoMutedText
                )
            },
            label = { Text("تاریخچه", fontSize = 11.sp, color = GeoMutedText) }
        )

        NavigationBarItem(
            selected = false,
            onClick = { onSettingsClick() },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "تنظیمات",
                    tint = GeoMutedText
                )
            },
            label = { Text("تنظیمات", fontSize = 11.sp, color = GeoMutedText) }
        )
    }
}

@Composable
fun HeaderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .background(GeoSearchBarBg, RoundedCornerShape(28.dp))
                .border(BorderStroke(1.dp, GeoBorder), RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "جستجو",
                tint = GeoMutedText,
                modifier = Modifier.size(24.dp)
            )
            
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("جستجوی ۳۶۰۰ کالا...", color = GeoMutedText, fontSize = 15.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = GeoText,
                    unfocusedTextColor = GeoText
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_field"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* search */ })
            )

            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "میکروفون",
                tint = GeoMutedText,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Quick arrangement helper for layout
object BoxArrangement {
    val SpaceBetween = Arrangement.SpaceBetween
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DebugNetworkScreen(onBack: () -> Unit) {
    val loggedUrls = remember { mutableStateListOf<String>() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Button(onClick = onBack) { Text("برگشت") }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(6f),
            factory = { context ->
                android.webkit.WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: android.webkit.WebView,
                            request: android.webkit.WebResourceRequest
                        ): android.webkit.WebResourceResponse? {
                            val url = request.url.toString()
                            if (!url.contains(".js") && !url.contains(".css") &&
                                !url.contains(".png") && !url.contains(".jpg") &&
                                !url.contains(".woff") && !url.contains(".ico") &&
                                !url.contains("googletagmanager") && !url.contains("chat.css")
                            ) {
                                loggedUrls.add(0, url)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    loadUrl("https://isaco.ir/%D9%82%D8%B7%D8%B9%D8%A7%D8%AA")
                }
            }
        )
        LazyColumn(Modifier.weight(1f)) {
            items(loggedUrls) { url ->
                Text(url, modifier = Modifier.padding(6.dp), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit, onIranKhodroClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Button(onClick = onBack) { Text("برگشت") }
        Spacer(modifier = Modifier.height(24.dp))
        Text("انتخاب برند", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onIranKhodroClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ایران خودرو")
        }
    }
}
