package io.github.triniwiz.fancycamera.objectdetection

import android.graphics.Rect
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.defaults.PredefinedCategory


class Result(detectedObject: DetectedObject) {
    val trackingId = detectedObject.trackingId
    val bounds = Bounds(detectedObject.boundingBox)
    val labels: Array<Label>

    init {
        val labels = mutableListOf<Label>()
        for (label in detectedObject.labels) {
            detectedObject.labels
            labels.add(Label(label))
        }
        this.labels = labels.toTypedArray()
    }

    class Label(label: DetectedObject.Label) {
        val text: Category
        val confidence: Float = label.confidence
        val index: Index

        init {
            text = when (label.text) {
                PredefinedCategory.FASHION_GOOD -> Category.FashionGood
                PredefinedCategory.FOOD -> Category.Food
                PredefinedCategory.HOME_GOOD -> Category.HomeGood
                PredefinedCategory.PLACE -> Category.Place
                PredefinedCategory.PLANT -> Category.Plant
                else -> Category.Unknown
            }
            index = when (label.index) {
                PredefinedCategory.FASHION_GOOD_INDEX -> Index.FashionGoodIndex
                PredefinedCategory.FOOD_INDEX -> Index.FoodIndex
                PredefinedCategory.HOME_GOOD_INDEX -> Index.HomeGoodIndex
                PredefinedCategory.PLACE_INDEX -> Index.PlaceIndex
                PredefinedCategory.PLANT_INDEX -> Index.PlantIndex
                else -> Index.UnknownIndex
            }
        }

        enum class Category(val category: String) {
            Unknown("unknown"),
            HomeGood("homeGood"),
            FashionGood("fashionGood"),
            Food("food"),
            Place("place"),
            Plant("plant")
        }

        enum class Index(val index: String) {
            UnknownIndex("unknownIndex"),
            HomeGoodIndex("homeGoodIndex"),
            FashionGoodIndex("fashionGoodIndex"),
            FoodIndex("foodIndex"),
            PlaceIndex("placeIndex"),
            PlantIndex("plantIndex")
        }

    }

    class Bounds(rect: Rect) {
        class Origin(val x: Int, val y: Int)
        class Size(val width: Int,
                   val height: Int)

        val origin = Origin(rect.left, rect.top)
        val size = Size(rect.width(), rect.height())
    }
}
