package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.AppDatabase
import com.example.data.local.entity.ProductEntity
import com.example.data.repository.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder {
    NAME_ASC,
    PRICE_ASC,
    PRICE_DESC
}

data class FilterState(
    val query: String = "",
    val selectedBrand: String = "همه برندها",
    val selectedCategory: String = "همه دسته‌ها",
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val priceMin: Long? = null,
    val priceMax: Long? = null
)

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProductRepository
    
    init {
        val db = AppDatabase.getDatabase(application)
        repository = ProductRepository(application, db.productDao())
        
        // Seed the database if empty on startup
        viewModelScope.launch {
            repository.checkAndSeedDatabase()
        }
    }

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // Trigger searches or grab all products
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawProductsFlow: Flow<List<ProductEntity>> = _filterState
        .map { it.query }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getAllProducts()
            } else {
                repository.searchProducts(query)
            }
        }

    // List of dynamic brands extracted from current search results or whole dataset
    val availableBrands: StateFlow<List<String>> = repository.getAllProducts()
        .map { products ->
            listOf("همه برندها") + products.map { it.brand }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("همه برندها"))

    // List of static categories supported
    val availableCategories = listOf(
        "همه دسته‌ها",
        "بلبرینگ و رولبرینگ",
        "کلاچ و انتقال قدرت",
        "پلوس و سرپلوس",
        "فرمان و هدایت",
        "سیستم ترمز",
        "برقی و الکترونیک",
        "واشر و آب‌بندی",
        "شیلنگ و سیستم خنک‌کننده",
        "جلوبندی و تعلیق",
        "بدنه و تزئینات",
        "سایر قطعات"
    )

    // Filtered and sorted products flow
    val uiState: StateFlow<List<ProductEntity>> = combine(
        rawProductsFlow,
        _filterState
    ) { products, filters ->
        products.filter { product ->
            val matchesBrand = filters.selectedBrand == "همه برندها" || product.brand == filters.selectedBrand
            val matchesCategory = filters.selectedCategory == "همه دسته‌ها" || product.determineCategory() == filters.selectedCategory
            val matchesPriceMin = filters.priceMin == null || product.priceNumeric >= filters.priceMin
            val matchesPriceMax = filters.priceMax == null || product.priceNumeric <= filters.priceMax
            
            matchesBrand && matchesCategory && matchesPriceMin && matchesPriceMax
        }.sortedWith { p1, p2 ->
            when (filters.sortOrder) {
                SortOrder.NAME_ASC -> p1.name.compareTo(p2.name)
                SortOrder.PRICE_ASC -> p1.priceNumeric.compareTo(p2.priceNumeric)
                SortOrder.PRICE_DESC -> p2.priceNumeric.compareTo(p1.priceNumeric)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Update query
    fun updateQuery(query: String) {
        _filterState.update { it.copy(query = query) }
    }

    // Update brand filter
    fun selectBrand(brand: String) {
        _filterState.update { it.copy(selectedBrand = brand) }
    }

    // Update category filter
    fun selectCategory(category: String) {
        _filterState.update { it.copy(selectedCategory = category) }
    }

    // Update sorting
    fun updateSortOrder(order: SortOrder) {
        _filterState.update { it.copy(sortOrder = order) }
    }

    // Update price limits
    fun updatePriceFilter(min: Long?, max: Long?) {
        _filterState.update { it.copy(priceMin = min, priceMax = max) }
    }

    // Reset all filters
    fun clearFilters() {
        _filterState.update { 
            FilterState(query = it.query) // Keep query but reset brand, category, sort
        }
    }
}
