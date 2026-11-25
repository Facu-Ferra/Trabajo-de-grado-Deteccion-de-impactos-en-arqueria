package com.surendramaran.yolov8tflite

import kotlin.math.ceil
import kotlin.math.sqrt

data class ArrowScore(
    val score: Int, // 0 significa 'M' (Miss)
    val colorHex: String,
    val distance: Float,
    val nx: Float,
    val ny: Float
)

object ScoringEngine {

    /**
     * Calcula el puntaje geométrico de cada flecha.
     * @param bullseye Caja del centro exacto.
     * @param target Caja que envuelve TODO el blanco visible.
     * @param arrows Lista de cajas de las flechas.
     * @param targetType "6-Ring" o "10-Ring".
     * @param expectedArrows Cantidad de flechas que el arquero indicó haber tirado.
     */
    fun calculateScores(
        bullseye: BoundingBox?,
        target: BoundingBox?,
        arrows: List<BoundingBox>,
        targetType: String,
        expectedArrows: Int
    ): Result<List<ArrowScore>> {

        if (bullseye == null) {
            return Result.failure(Exception("Bullseye not detected. Please center the target and retake."))
        }
        
        if (target == null) {
            return Result.failure(Exception("Full target boundaries not detected. Please ensure the whole target is visible."))
        }

        val cx = bullseye.cx
        val cy = bullseye.cy
        
        // El ancho de la diana entera.
        val targetWidth = target.w
        
        // R es el grosor de UN solo anillo.
        // El radio total (desde el centro hasta el borde) es la mitad del ancho.
        val totalRadius = targetWidth / 2f
        
        val R = if (targetType == "10-Ring") {
            totalRadius / 10f // La diana completa tiene 10 anillos desde el centro al borde
        } else {
            totalRadius / 6f  // La diana recortada tiene 6 anillos (5,6,7,8,9,10) desde el centro al borde
        }

        // Calculamos un radio de flecha estimado para la tolerancia de "Line Cutter" (mordedora de línea).
        // En promedio, una flecha tiene ~0.7cm de diámetro (0.35cm de radio).
        // Si asumimos que la diana estándar en pantalla mide unos 60-80cm físicos, 
        // el radio de la flecha representará aproximadamente el 0.6% del ancho total de la diana.
        val arrowRadiusPx = targetWidth * 0.006f

        val scores = mutableListOf<ArrowScore>()
        
        for (arrow in arrows) {
            val ax = arrow.cx
            val ay = arrow.cy
            
            val dx = ax - cx
            val dy = ay - cy
            val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            
            // Restamos el radio de la flecha a la distancia al centro. 
            // Si el borde de la flecha toca la línea del anillo interior, le pongo el puntaje mayor
            val effectiveDistance = distance - arrowRadiusPx
            
            // Si la distancia efectiva es <= 0, está literalmente en el centro perfecto absoluto.
            var ringsTraversed = ceil(effectiveDistance / R).toInt()
            if (ringsTraversed < 1) ringsTraversed = 1
            
            var score = 11 - ringsTraversed
            
            if (score > 10) score = 10
            
            if (targetType == "6-Ring" && score < 5) score = 0 // Miss (afuera del azul)
            if (targetType == "10-Ring" && score < 1) score = 0 // Miss (afuera del blanco)
            
            val color = getRingColor(score)
            val nx = dx / totalRadius
            val ny = dy / totalRadius
            
            scores.add(ArrowScore(score, color, distance, nx, ny))
        }

        // Ordenar de mayor a menor puntaje (como es estándar en torneos de arquería)
        scores.sortByDescending { it.score }
        
        // Rellenar con 'M' (Miss) si el sistema detectó menos flechas de las que el usuario dijo tirar
        val missingCount = expectedArrows - scores.size
        for (i in 0 until missingCount) {
            scores.add(ArrowScore(0, "#757575", Float.MAX_VALUE, 0f, 0f))
        }

        return Result.success(scores)
    }

    private fun getRingColor(score: Int): String {
        return when (score) {
            10, 9 -> "#FFD700" // Amarillo/Oro
            8, 7 -> "#FF0000" // Rojo
            6, 5 -> "#00B4FF" // Azul
            4, 3 -> "#000000" // Negro
            2, 1 -> "#FFFFFF" // Blanco
            else -> "#757575" // Miss (Gris)
        }
    }
}
