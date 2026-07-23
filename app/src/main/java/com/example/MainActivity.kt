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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.isSystemInDarkTheme

import com.example.ui.theme.*
import com.example.ui.viewmodel.SortOrder
import com.example.ui.viewmodel.FilterState
import com.example.ui.viewmodel.ProductViewModel
import com.example.data.local.entity.ProductEntity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState

object MarkupState {
    var generalPercent by androidx.compose.runtime.mutableStateOf(0)
    var brandMap by androidx.compose.runtime.mutableStateOf<Map<String, Int>>(emptyMap())
    var version by androidx.compose.runtime.mutableStateOf(0)

    fun refresh() {
        version++
    }
}

object ScanState {
    var lastBarcode by androidx.compose.runtime.mutableStateOf<String?>(null)
}


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
                var showBarcodeScanner by remember { mutableStateOf(false) }
                var showBarcodeForProduct by remember { mutableStateOf(false) }
                var scanTargetProduct by remember { mutableStateOf<Product?>(null) }
                if (showBarcodeScanner) {
                    BarcodeScannerScreen(
                        onBarcodeDetected = { barcode ->
                            ScanState.lastBarcode = barcode
                            showBarcodeScanner = false
                        },
                        onClose = { showBarcodeScanner = false }
                    )
                } else if (showBarcodeForProduct) {
                    BarcodeScannerScreen(
                        onBarcodeDetected = { barcode ->
                            showBarcodeForProduct = false
                            scanTargetProduct = null
                        },
                        onClose = { showBarcodeForProduct = false; scanTargetProduct = null },
                        targetProduct = scanTargetProduct?.let {
                            com.example.data.local.entity.ProductEntity(
                                id = it.id,
                                name = it.name,
                                brand = it.brand,
                                price = it.price,
                                priceNumeric = it.numericPrice
                            )
                        }
                    )
                } else if (showDebugWebView) {
                    DebugNetworkScreen(onBack = { showDebugWebView = false })
                } else if (showCategoriesScreen) {
                    CategoriesScreen(
                        onBack = { showCategoriesScreen = false },
                        onIranKhodroClick = { showDebugWebView = true }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        SearchEngineContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            showSettings = showSettings,
                            onDismissSettings = { showSettings = false },
                            onShowSettings = { showSettings = true },
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = { newVal ->
                                isDarkTheme = newVal
                                prefs.edit().putBoolean("dark_theme", newVal).apply()
                            },
                            onCategoriesClick = { showCategoriesScreen = true },
                            onBarcodeScanClick = { showBarcodeScanner = true },
                            onRegisterBarcodeClick = { product ->
                                scanTargetProduct = product
                                showBarcodeForProduct = true
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

data class PriceResult(
    val buyPrice: Long,
    val sellPrice: Long,
    val profit: Long,
    val markupPercent: Int
)

fun calculatePriceResult(
    buyPrice: Long,
    brand: String,
    generalPercent: Int,
    brandMarkupMap: Map<String, Int>
): PriceResult {

    val percent = brandMarkupMap[brand] ?: generalPercent

    val sell = buyPrice + (buyPrice * percent / 100)

    val roundedSell = (sell / 1000 + if (sell % 1000 > 0) 1 else 0) * 1000

    return PriceResult(
        buyPrice = buyPrice,
        sellPrice = roundedSell,
        profit = roundedSell - buyPrice,
        markupPercent = percent
    )
}

fun calculateDisplayPrice(numericPrice: Long, brand: String, generalPercent: Int, brandMarkupMap: Map<String, Int>): Long {
    val rawPrice = if (numericPrice < 80000) (numericPrice * 1.4).toLong() else numericPrice
    val percent = brandMarkupMap[brand] ?: generalPercent

    android.util.Log.d(
        "MARKUP",
        "brand=$brand general=$generalPercent brandPercent=${brandMarkupMap[brand]} finalPercent=$percent price=$numericPrice"
    )
    val withMarkup = rawPrice + (rawPrice * percent / 100)
    return (withMarkup / 1000 + if (withMarkup % 1000 > 0) 1 else 0) * 1000
}

// Data model parsed from CSV
data class Product(
    val id: Int,
    val row: Int,
    val name: String,
    val brand: String,
    val price: String,
    val numericPrice: Long,
    val barcode: String? = null
)

@Composable
fun SearchEngineContent(
    modifier: Modifier = Modifier,
    viewModel: ProductViewModel = viewModel(),
    showSettings: Boolean = false,
    onDismissSettings: () -> Unit = {},
    onShowSettings: () -> Unit = {},
    isDarkTheme: Boolean = false,
    onThemeToggle: (Boolean) -> Unit = {},
    onCategoriesClick: () -> Unit = {},
    onBarcodeScanClick: () -> Unit = {},
    onRegisterBarcodeClick: (Product) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
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
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val fileName = cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                c.moveToFirst()
                if (nameIndex >= 0) c.getString(nameIndex) else null
            } ?: it.lastPathSegment?.substringAfterLast("/") ?: "فایل ناشناس"
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
                            items(preview.duplicateMatches) { match ->    val isDarkTheme = isSystemInDarkTheme()


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
    val rawProducts = remember(allProducts) {
        allProducts.map { e -> Product(id = e.id, row = e.id, name = e.name, brand = e.brand, price = e.price, numericPrice = e.priceNumeric, barcode = e.barcode) }
    }

    // Filters and search logic
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(300)
        debouncedSearchQuery = searchQuery
    }
    LaunchedEffect(Unit) {
        MarkupState.generalPercent = getGeneralMarkupPercent(context)
        MarkupState.brandMap = getBrandMarkupMap(context)
    }
    LaunchedEffect(ScanState.lastBarcode, rawProducts) {
        ScanState.lastBarcode?.let { code ->
            val found = rawProducts.find { it.barcode == code }
            if (found != null) {
                selectedProduct = found
            }
            ScanState.lastBarcode = null
        }
    }
    var selectedCategory by remember { mutableStateOf("همه دسته‌ها") }
    var selectedBrand by remember { mutableStateOf("همه برندها") }
    var minPriceInput by remember { mutableStateOf("") }
    var maxPriceInput by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.NAME_ASC) }
    var showAdvancedFilters by remember { mutableStateOf(false) }
    BackHandler(enabled = selectedProduct != null) { selectedProduct = null }
    BackHandler(enabled = showSettings) { onDismissSettings() }
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
        result = result.replace('ي', 'ی').replace('ك', 'ک')
            .replace('ة', 'ه').replace('أ', 'ا').replace('إ', 'ا').replace('ؤ', 'و')
        return result
    }

    val normalizedQuery = normalizeQuery(debouncedSearchQuery)

    fun compress(s: String): String = s.replace(Regex("[\\s\u200c]+"), "")

    val filteredProducts = remember(normalizedQuery, selectedCategory, selectedBrand, minPriceInput, maxPriceInput, sortOrder) {
        rawProducts.filter { product ->
            val matchesQuery = if (normalizedQuery.isBlank()) true else {
                val tokens = normalizedQuery.trim().split(Regex("\\s+"))
                val searchIn = normalizeQuery(product.name) + " " + normalizeQuery(product.brand)
                val tokenMatch = tokens.all { token -> searchIn.contains(token) }
                tokenMatch || compress(searchIn).contains(compress(normalizedQuery))
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

    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet(
                drawerContainerColor = if (isDarkTheme) GeoInactivePillBgDark else GeoInactivePillBg
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("منو", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dynText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                HorizontalDivider(color = dynBorder)
                androidx.compose.material3.NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null, tint = dynPrimary) },
                    label = { Text("خانه", color = dynText) },
                    selected = true,
                    onClick = { drawerScope.launch { drawerState.close() } }
                )
                androidx.compose.material3.NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Category, contentDescription = null, tint = dynPrimary) },
                    label = { Text("دسته‌ها", color = dynText) },
                    selected = false,
                    onClick = { drawerScope.launch { drawerState.close() }; onCategoriesClick() }
                )
                androidx.compose.material3.NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = dynPrimary) },
                    label = { Text("تنظیمات", color = dynText) },
                    selected = false,
                    onClick = { drawerScope.launch { drawerState.close() }; onShowSettings() }
                )
                androidx.compose.material3.NavigationDrawerItem(
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = dynPrimary) },
                    label = { Text("اسکن بارکد", color = dynText) },
                    selected = false,
                    onClick = { drawerScope.launch { drawerState.close() }; onBarcodeScanClick() }
                )
            }
        }
    ) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Import CSV Button

        // App Branding / Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                Icon(Icons.Default.Menu, contentDescription = "منو", tint = dynPrimary)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "یدک مارکت (زینلی)",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = dynPrimary
            )
            Text(
                text = "لیست قیمت ها",
                fontSize = 13.sp,
                color = dynMuted,
                textAlign = TextAlign.Center
            )
            Text(
                text = "یدک مارکت زینلی",
                fontSize = 11.sp,
                color = dynMuted,
                textAlign = TextAlign.Center
            )
        }

        // Search Bar Fill style
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input"),
            placeholder = { Text("نام کالا یا برند را جستجو کنید...", fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = dynPrimary) },
            trailingIcon = {
                IconButton(onClick = onBarcodeScanClick) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "بارکد خوان", tint = dynPrimary)
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color(0x66B39DDB),
                unfocusedContainerColor = androidx.compose.ui.graphics.Color(0x55B39DDB),
                focusedIndicatorColor = dynPrimary,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color(0x88B39DDB)
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
                    containerColor = if (showAdvancedFilters) dynPrimary else androidx.compose.ui.graphics.Color(0x33D0BCFF)
                ),
                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x66D0BCFF)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("toggle_filters_button")
            ) {
                Text(
                    text = if (showAdvancedFilters) "پنهان کردن فیلترها" else "فیلترهای پیشرفته",
                    fontSize = 12.sp,
                    color = dynText
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
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0x33D0BCFF)),
                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x66D0BCFF)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = when (sortOrder) {
                        SortOrder.NAME_ASC -> "مرتب‌سازی: الفبا"
                        SortOrder.PRICE_ASC -> "قیمت: صعودی"
                        SortOrder.PRICE_DESC -> "قیمت: نزولی"
                    },
                    fontSize = 12.sp,
                    color = dynText
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
        } else if (normalizedQuery.isBlank() && selectedCategory == "همه دسته‌ها" && selectedBrand == "همه برندها") {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("نام کالا یا برند را جستجو کنید", fontSize = 14.sp, color = dynMuted)
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
                    val visible = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                    androidx.compose.animation.AnimatedVisibility(
                        visibleState = visible,
                        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) +
                                androidx.compose.animation.slideInVertically(animationSpec = androidx.compose.animation.core.tween(300)) { it / 2 }
                    ) {
                        ProductRowCard(product = product, category = determineCategory(product.name), onClick = { selectedProduct = product })
                    }
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
                LaunchedEffect(Unit) {
                    MarkupState.generalPercent = getGeneralMarkupPercent(settingsContext)
                    MarkupState.brandMap = getBrandMarkupMap(settingsContext)
                }
                var brandMarkupMap by remember { mutableStateOf(getBrandMarkupMap(settingsContext)) }
                var selectedBrandsForMarkup by remember { mutableStateOf(brandMarkupMap.keys) }
                val allBrandsForMarkup by viewModel.availableBrands.collectAsState()

                var expandedData by remember { mutableStateOf(false) }
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
                            val syncStatus by viewModel.syncStatus.collectAsState()
                            Button(
                                onClick = { viewModel.syncFromServer() },
                                colors = ButtonDefaults.buttonColors(containerColor = dynPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "sync from server", modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("دریافت کاتالوگ از سرور", fontSize = 14.sp, color = Color.White)
                            }
                            OutlinedButton(
                                onClick = { viewModel.pushCatalogToServer() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = "push to server", modifier = Modifier.size(16.dp), tint = dynPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ارسال کاتالوگ به سرور", fontSize = 14.sp, color = dynText)
                            }
                            syncStatus?.let { status ->
                                Text(status, fontSize = 12.sp, color = dynMuted)
                            }
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = generalMarkupInput,
                                    onValueChange = { generalMarkupInput = it.filter { c -> c.isDigit() } },
                                    label = { Text("درصد عمومی (همه برندها)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = dynPrimary, unfocusedBorderColor = dynBorder)
                                )
                                Button(onClick = {
                                    val p = generalMarkupInput.toIntOrNull() ?: 0
                                    setGeneralMarkupPercent(settingsContext, p)
                                    MarkupState.generalPercent = p
                                    MarkupState.refresh()
                                    android.util.Log.d("MARKUP", "اعمال شد: $p")
                                }) { Text("تایید", fontSize = 12.sp) }
                            }
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
        val generalPercent2 = MarkupState.generalPercent
        val brandMarkupMap2 = MarkupState.brandMap
        val priceResult2 = calculatePriceResult(product.numericPrice, product.brand, generalPercent2, brandMarkupMap2)
        val roundedPrice2 = priceResult2.sellPrice
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
                    Text(text = "جزئیات محصول", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = dynText)
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
                        color = dynText,
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
                    HorizontalDivider(color = dynBorder)
                    Text(
                        text = "قیمت خرید: ${df2.format(priceResult2.buyPrice)} ریال",
                        fontSize = 14.sp,
                        color = dynText,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                    Text(
                        text = "مارکاپ: ${priceResult2.markupPercent}٪",
                        fontSize = 14.sp,
                        color = dynText,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                    Text(
                        text = "قیمت فروش: ${df2.format(priceResult2.sellPrice)} ریال",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = dynPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                    Text(
                        text = "سود: ${df2.format(priceResult2.profit)} ریال",
                        fontSize = 14.sp,
                        color = dynText,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = product.row.toString(), fontSize = 12.sp, color = dynText)
                        Text(text = "کد محصول:", fontSize = 12.sp, color = dynMuted)
                    }
                    HorizontalDivider(color = dynBorder)
                    Button(
                        onClick = {
                            selectedProduct = null
                            onRegisterBarcodeClick(product)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = dynPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ثبت بارکد", fontSize = 14.sp, color = Color.White)
                    }
                }
            },
            confirmButton = {}
        )
    }
    }
}

@Composable
fun ProductRowCard(product: Product, category: String, onClick: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val df = DecimalFormat("#,###")
    val markupVersion = MarkupState.version
    val generalPercent = MarkupState.generalPercent
    val brandMarkupMap = MarkupState.brandMap
    val priceResult = calculatePriceResult(
        product.numericPrice,
        product.brand,
        generalPercent,
        brandMarkupMap
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x66B39DDB)),
        modifier = Modifier.fillMaxWidth()
            .testTag("product_card_${product.row}")
            .clickable { onClick() }
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = if (isDarkTheme) listOf(
                        androidx.compose.ui.graphics.Color(0xFF2A2440),
                        androidx.compose.ui.graphics.Color(0xFF1E1B30)
                    ) else listOf(
                        androidx.compose.ui.graphics.Color(0xFFEDE7F6),
                        androidx.compose.ui.graphics.Color(0xFFF3EDF7)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
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
                        color = if (isDarkTheme) GeoTextDark else GeoText
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
                    text = "فروش: ${df.format(priceResult.sellPrice)} ریال",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = GeoPrimary
                    )
                )
                Text(
                    text = "خرید: ${df.format(priceResult.buyPrice)} | سود: ${df.format(priceResult.profit)} ریال",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isDarkTheme) GeoMutedTextDark else GeoMutedText,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = "",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isDarkTheme) GeoMutedTextDark else GeoMutedText,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
fun BottomNavBar(onSettingsClick: () -> Unit = {}, onCategoriesClick: () -> Unit = {}, isDarkTheme: Boolean = false) {
    val navBg = if (isDarkTheme) GeoBottomNavBgDark else GeoBottomNavBg
    val navActive = if (isDarkTheme) GeoActivePillBgDark else GeoActivePillBg
    val navActiveText = if (isDarkTheme) GeoActivePillTextDark else GeoActivePillText
    val navMuted = if (isDarkTheme) GeoMutedTextDark else GeoMutedText
    NavigationBar(
        containerColor = navBg,
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
                        tint = navActiveText
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
                    tint = navMuted
                )
            },
            label = { Text("دسته‌ها", fontSize = 11.sp, color = navMuted) }
        )

        NavigationBarItem(
            selected = false,
            onClick = {},
            icon = {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "تاریخچه",
                    tint = navMuted
                )
            },
            label = { Text("تاریخچه", fontSize = 11.sp, color = navMuted) }
        )

        NavigationBarItem(
            selected = false,
            onClick = { onSettingsClick() },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "تنظیمات",
                    tint = navMuted
                )
            },
            label = { Text("تنظیمات", fontSize = 11.sp, color = navMuted) }
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
