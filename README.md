# Sistema Automático de Puntuación y Evaluación de Rendimiento en Tiro con Arco

Aplicación móvil nativa para Android desarrollada como Trabajo Final de Grado para la **Licenciatura en Ciencia de Datos** (Universidad del Gran Rosario, 2026).

**Autor:** Facundo Martín Ferrari  
**Video demo:** [Ver demostración en YouTube](https://www.youtube.com/watch?v=ntGpKsgANIg)  
**Entrenamiento del modelo (Colab):** [Ver notebook en Google Colab](https://colab.research.google.com/drive/1vXtyaxU8mluN5W9GhcHuog2aInxVUXkl)

---

## Descripción General

El sistema permite detectar automáticamente impactos de flechas sobre blancos reglamentarios de tiro con arco (World Archery de 10 y 6 anillos) y calcular su puntaje de forma instantánea y 100% local en el dispositivo móvil (**Edge AI**), sin requerir conexión a internet ni servidores externos.

### Flujo de Procesamiento

1. **Captura en vivo (`MainActivity.kt`):** Utiliza la API **CameraX** (`Preview`) para capturar fotogramas en tiempo real directamente como `Bitmap`.
2. **Preprocesamiento (`Detector.kt`):** Aplica **Letterbox** (640 × 640 px con relleno gris neutro `RGB(114, 114, 114)` para preservar la relación de aspecto circular de la diana) y convierte la imagen a un tensor `Float32` normalizado en formato `NCHW` (`[1, 3, 640, 640]`).
3. **Inferencia ONNX (`Detector.kt`):** Ejecuta un modelo **YOLOv8 Nano** entrenado mediante *Transfer Learning* y exportado a **ONNX** (`app/src/main/assets/model.onnx`) sobre **ONNX Runtime Android**, detectando tres clases (`labels.txt`):
   - `arrow` (punto de penetración de la flecha)
   - `bullseye` (centro de la diana)
   - `target_bounds` (marco exterior del blanco)
4. **Postprocesamiento (`Detector.kt`):** Decodifica el tensor de salida (`[1, 7, 8400]`), filtra por umbral de confianza (`CONFIDENCE_THRESHOLD = 0.35`, ajustable desde la interfaz) y aplica **Non-Maximum Suppression (NMS)** con `IOU_THRESHOLD = 0.70`, además de un filtro de unicidad para `bullseye` y `target_bounds`.
5. **Motor Geométrico de Puntuación (`ScoringEngine.kt`):** Calcula la distancia euclidiana normalizada de cada impacto respecto al centro, aplica la tolerancia reglamentaria de flecha cortadora de línea (*Line-Cutter*, estimando el radio del tubo en el 0,6% del ancho de la diana) y asigna los puntajes correspondientes.
6. **Persistencia y Analítica (`data/`, `HeatmapActivity.kt`, `OverlayView.kt`):** Almacena sesiones y tandas localmente con **Room**, dibuja las detecciones sobre el visor y genera mapas de calor y métricas de consistencia.

> Además, el proyecto incluye un módulo exploratorio de estimación de pose biomecánica (`PoseActivity.kt`, `PoseDetector.kt`, `yolov8s-pose.onnx`) documentado como línea de trabajo futuro.

---

## Stack Tecnológico

- **Lenguaje:** Kotlin (Android `minSdk = 26`, `targetSdk = 34`, JVM 17)
- **Motor de Inferencia:** ONNX Runtime Mobile (`com.microsoft.onnxruntime:onnxruntime-android:1.17.3`)
- **Cámara:** AndroidX CameraX (`1.4.0-alpha04`)
- **Base de Datos Local:** AndroidX Room (`2.6.1`)
- **Arquitectura de Visión:** Ultralytics YOLOv8n exportado a ONNX

---

## Cómo Compilar y Ejecutar

1. Clonar este repositorio:
   ```bash
   git clone https://github.com/Facu-Ferra/Trabajo-de-grado-Deteccion-de-impactos-en-arqueria.git
   ```
2. Abrir la carpeta raíz del proyecto en **Android Studio**.
3. Esperar a que finalice la sincronización de **Gradle**.
4. Conectar un dispositivo Android físico (Android 8.0 / API 26 o superior) y ejecutar la aplicación (`Run 'app'`).

---

## Créditos Base

La estructura inicial del detector móvil tomó como referencia la plantilla de código abierto [YOLOv8-TfLite-Object-Detector](https://github.com/surendramaran/YOLOv8-TfLite-Object-Detector), siendo adaptada e implementada sobre **ONNX Runtime**, preprocesamiento **Letterbox**, motor de puntuación geométrico, gestión de sesiones en Room y modo duelo.
