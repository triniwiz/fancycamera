package io.github.triniwiz.fancycamera.textrecognition

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

class Result(instance: Text) {
    val blocks: Array<Block>

    val text = instance.text

    init {
        val _blocks = mutableListOf<Block>()
        for (block in instance.textBlocks) {
            val lines = mutableListOf<Line>()
            for (line in block.lines) {
                val elements = mutableListOf<Element>()
                for (element in line.elements) {
                    elements.add(Element(element.text, Bounds(element.boundingBox!!)))
                }
                val points = mutableListOf<Point>()
                for (point in line.cornerPoints ?: arrayOf()) {
                    points.add(Point(point))
                }
                lines.add(
                        Line(line.text, points.toTypedArray(), Bounds(line.boundingBox!!), elements.toTypedArray())
                )
            }

            val points = mutableListOf<Point>()
            for (point in block.cornerPoints ?: arrayOf()) {
                points.add(Point(point))
            }

            _blocks.add(
                    Block(block.text, points.toTypedArray(), Bounds(block.boundingBox!!), lines.toTypedArray())
            )
        }
        blocks = _blocks.toTypedArray()
    }

    class Element(val text: String, bounds: Bounds)

    class Line(val text: String, val cornerPoints: Array<Point>, bounds: Bounds, elements: Array<Element>)
    class Block(val text: String, val cornerPoints: Array<Point>, bounds: Bounds, lines: Array<Line>)
    class Bounds(rect: Rect) {
        class Origin(val x: Int, val y: Int)
        class Size(val width: Int,
                   val height: Int)

        val origin = Origin(rect.left, rect.right)
        val size = Size(rect.width(), rect.height())
    }

    class Point(point: android.graphics.Point) {
        val x: Int = point.x

        val y: Int = point.y
    }
}