// src/main/java/com/example/backend/service/SentinelHubService.java
package com.example.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SentinelHubService {

    @Value("${sentinelhub.process.api-url}")
    private String processApiUrl;

    private final SentinelHubAuthService authService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public SentinelHubService(SentinelHubAuthService authService) {
        this.authService = authService;
    }

    /**
     * Запрашивает обработанное изображение (например, NDVI) для заданного полигона.
     *
     * @param polygonGeoJson GeoJSON строка геометрии полигона (только геометрия, не Feature).
     * @param analysisType Тип анализа (например, "NDVI", "TRUE_COLOR").
     * @param dateFrom Начальная дата для выборки данных (YYYY-MM-DD).
     * @param dateTo Конечная дата для выборки данных (YYYY-MM-DD).
     * @param width Ширина выходного изображения в пикселях.
     * @param height Высота выходного изображения в пикселях.
     * @return Массив байтов изображения (PNG).
     */
    public byte[] getProcessedImage(String polygonGeoJson, String analysisType, String dateFrom, String dateTo, int width, int height) {
        String accessToken = authService.getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        // Явно указываем Accept header для получения PNG изображения
        headers.set(HttpHeaders.ACCEPT, MediaType.IMAGE_PNG_VALUE); 

        try {
            // Создаем JSON-тело запроса для Process API
            ObjectNode requestBody = objectMapper.createObjectNode();

            // Input section
            ObjectNode inputNode = requestBody.putObject("input");
            ObjectNode boundsNode = inputNode.putObject("bounds");
            boundsNode.set("geometry", objectMapper.readTree(polygonGeoJson)); // Вставляем GeoJSON геометрию полигона

            ObjectNode dataArrayNode = inputNode.putArray("data").addObject();
            dataArrayNode.put("type", "sentinel-2-l2a"); // Используем данные Sentinel-2 L2A

            ObjectNode dataFilterNode = dataArrayNode.putObject("dataFilter");
            ObjectNode timeRangeNode = dataFilterNode.putObject("timeRange");
            timeRangeNode.put("from", dateFrom + "T00:00:00Z");
            timeRangeNode.put("to", dateTo + "T23:59:59Z");
            dataFilterNode.put("mosaickingOrder", "leastCC"); // Выбираем наименее облачный снимок

            // Output section
            ObjectNode outputNode = requestBody.putObject("output");
            outputNode.put("width", width);
            outputNode.put("height", height);

            // responses должен быть массивом объектов, а format - объектом
            ArrayNode responsesArray = objectMapper.createArrayNode();
            ObjectNode formatObject = objectMapper.createObjectNode();
            formatObject.put("type", "image/png"); 
            
            responsesArray.addObject()
                    .put("identifier", "default")
                    .set("format", formatObject); 
            outputNode.set("responses", responsesArray); 

            // Evalscript section based on analysisType
            String evalscript = getEvalscriptForAnalysisType(analysisType);
            requestBody.put("evalscript", evalscript);

            log.debug("Sentinel Hub Process API Request Body: {}", requestBody.toPrettyString());

            HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);

            // Отправляем POST-запрос и ожидаем массив байтов изображения
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    processApiUrl,
                    HttpMethod.POST,
                    request,
                    byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully fetched processed image for analysis type: {}", analysisType);
                return response.getBody();
            } else {
                log.error("Failed to fetch processed image. Status: {}", response.getStatusCode());
                // Попытка прочитать тело ответа для более детальной ошибки от Sentinel Hub
                String errorBody = response.getBody() != null ? new String(response.getBody()) : "[no body]";
                throw new RuntimeException("Failed to fetch processed image from Sentinel Hub. Response: " + errorBody);
            }

        } catch (HttpClientErrorException e) {
            log.error("HTTP error fetching Sentinel Hub image: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to get Sentinel Hub image: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
                log.error("Error fetching Sentinel Hub image: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to get Sentinel Hub image", e);
        }
    }

    /**
     * Возвращает evalscript для различных типов анализа с улучшенными цветовыми палитрами.
     * @param analysisType Тип анализа (например, "NDVI", "TRUE_COLOR").
     * @return Строка evalscript.
     */
    private String getEvalscriptForAnalysisType(String analysisType) {
        switch (analysisType.toUpperCase()) {
            case "NDVI":
            case "3_NDVI":
            case "3_NDVI-L1C":
                // Evalscript для NDVI с улучшенной цветовой палитрой и явной прозрачностью
                return "//VERSION=3\n" +
                   "function setup() {\n" +
                   "  return {\n" +
                   "    input: [\n" +
                   "      { bands: [\"B04\", \"B08\", \"dataMask\"], sampleType: \"FLOAT32\" } // Используем FLOAT32 для точности расчетов NDVI\n" +
                   "    ],\n" +
                   "    output: [\n" +
                   "      { id: \"default\", bands: 4, sampleType: \"UINT8\" } // RGBA (Красный, Зеленый, Синий, Альфа), 8-битное целое без знака\n" +
                   "    ]\n" +
                   "  };\n" +
                   "}\n" +
                   "\n" +
                   "// Улучшенный плавный градиент NDVI: от красного (низкое NDVI) до темно-зеленого (высокое NDVI) с желтым в середине.\n" +
                   "const ramp = [\n" +
                   "  [-1.0, [0.0, 0.0, 0.0, 0]],   // Полностью прозрачный для значений < -0.2 (вода, облака, снег, нет данных)\n" +
                   "  [-0.2, [0.0, 0.0, 0.0, 0]],   // Продолжаем прозрачность для очень низких/NoData значений\n" +
                   "  [ 0.0, [0.9, 0.1, 0.1, 1]],   // Красный для голой почвы/очень низкой растительности\n" +
                   "  [ 0.1, [0.9, 0.4, 0.1, 1]],   // Оранжево-красный\n" +
                   "  [ 0.2, [0.9, 0.7, 0.1, 1]],   // Желто-оранжевый\n" +
                   "  [ 0.3, [0.8, 0.8, 0.2, 1]],   // Желтый (середина, стрессовое состояние)\n" +
                   "  [ 0.4, [0.6, 0.7, 0.2, 1]],   // Светло-зеленый с желтоватым оттенком\n" +
                   "  [ 0.5, [0.4, 0.6, 0.15, 1]],  // Средне-зеленый\n" +
                   "  [ 0.6, [0.2, 0.5, 0.1, 1]],   // Темно-зеленый\n" +
                   "  [ 1.0, [0.0, 0.3, 0.0, 1]]    // Максимально темно-зеленый (очень плотная/здоровая растительность)\n" +
                   "];\n" +
                   "const visualizer = new ColorRampVisualizer(ramp);\n" +
                   "\n" +
                   "function evaluatePixel(samples) {\n" +
                   "  // Применяем маску данных в начале, чтобы не обрабатывать пиксели без данных\n" +
                   "  if (samples.dataMask === 0) {\n" +
                   "    return [0, 0, 0, 0]; // Полностью прозрачный, если нет данных\n" +
                   "  }\n" +
                   "\n" +
                   "  let ndvi = index(samples.B08, samples.B04);\n" +
                   "  let rgb_with_alpha = visualizer.process(ndvi); // Получаем RGBA от visualizer\n" +
                   "  \n" +
                   "  // Масштабируем RGB на 255 и используем альфа из visualizer\n" +
                   "  return [\n" +
                   "    rgb_with_alpha[0] * 255,\n" +
                   "    rgb_with_alpha[1] * 255,\n" +
                   "    rgb_with_alpha[2] * 255,\n" +
                   "    rgb_with_alpha[3] * 255 // Альфа-канал уже учтен в visualizer, умножаем на 255\n" +
                   "  ];\n" +
                   "}\n";
            case "TRUE_COLOR":
            case "1_TRUE_COLOR":
            case "1_TRUE-COLOR-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B02\", \"B03\", \"B04\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) {\n" +
                       "    return [0, 0, 0, 0]; // Полностью прозрачный, если нет данных\n" +
                       "  }\n" +
                       "  // Simple scaling and gamma correction for natural colors\n" +
                       "  let gain = 3.5; // Increased gain for better brightness\n" +
                       "  let red = samples.B04 * gain;\n" +
                       "  let green = samples.B03 * gain;\n" +
                       "  let blue = samples.B02 * gain;\n" +
                       "\n" +
                       "  // Apply a gamma correction (e.g., power of 0.8 to brighten mid-tones)\n" +
                       "  red = Math.pow(red, 0.8);\n" +
                       "  green = Math.pow(green, 0.8);\n" +
                       "  blue = Math.pow(blue, 0.8);\n" +
                       "\n" +
                       "  // Clip values to 1.0 (max) and convert to 0-255\n" +
                       "  return [Math.min(red, 1.0) * 255, Math.min(green, 1.0) * 255, Math.min(blue, 1.0) * 255, samples.dataMask * 255];\n" +
                       "}";
            case "FALSE_COLOR":
            case "2_FALSE_COLOR":
            case "2_FALSE-COLOR-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B08\", \"B04\", \"B03\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) {\n" +
                       "    return [0, 0, 0, 0];\n" +
                       "  }\n" +
                       "  // False Color (NIR, Red, Green) - Vegetation appears bright red.\n" +
                       "  // B08 (NIR) -> Red channel\n" +
                       "  // B04 (Red) -> Green channel\n" +
                       "  // B03 (Green) -> Blue channel\n" +
                       "  let gain = 3.5; // Increased gain for better visibility\n" +
                       "  let red = samples.B08 * gain;\n" +
                       "  let green = samples.B04 * gain;\n" +
                       "  let blue = samples.B03 * gain;\n" +
                       "\n" +
                       "  // Simple tone mapping (e.g., logistic function) or clipping\n" +
                       "  red = Math.min(red, 1.0);\n" +
                       "  green = Math.min(green, 1.0);\n" +
                       "  blue = Math.min(blue, 1.0);\n" +
                       "\n" +
                       "  return [red * 255, green * 255, blue * 255, samples.dataMask * 255];\n" +
                       "}";
            case "FALSE_COLOR_URBAN":
            case "4-FALSE-COLOR-URBAN":
            case "4-FALSE-COLOR-URBAN-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    // Based on your image: B12, B11, B04. For L2A, these bands are available.\n" +
                       "    input: [{ bands: [\"B12\", \"B11\", \"B04\", \"dataMask\"] }], \n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) {\n" +
                       "    return [0, 0, 0, 0];\n" +
                       "  }\n" +
                       "  // False Color Urban (SWIR2, SWIR1, Red) - Highlights urban areas.\n" +
                       "  // B12 (SWIR2) -> Red channel\n" +
                       "  // B11 (SWIR1) -> Green channel\n" +
                       "  // B04 (Red)   -> Blue channel\n" +
                       "  let gain = 3.0; // Adjust gain for better visualization\n" +
                       "  let red = samples.B12 * gain;\n" +
                       "  let green = samples.B11 * gain;\n" +
                       "  let blue = samples.B04 * gain;\n" +
                       "\n" +
                       "  red = Math.min(red, 1.0);\n" +
                       "  green = Math.min(green, 1.0);\n" +
                       "  blue = Math.min(blue, 1.0);\n" +
                       "\n" +
                       "  return [red * 255, green * 255, blue * 255, samples.dataMask * 255];\n" +
                       "}";
            case "MOISTURE_INDEX":
            case "5-MOISTURE-INDEX1":
            case "5-MOISTURE-INDEX1-L1C":
                // NDMI (Normalized Difference Moisture Index) or similar for plant moisture
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    // Using B08 (NIR) and B11 (SWIR1) for NDMI as per your image\n" +
                       "    input: [{ bands: [\"B08\", \"B11\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "\n" +
                       "// Modified color palette for moisture index (NDMI-like): yellow/brown for dry, dark blue/turquoise for high moisture\n" +
                       "const moistureRamp = [\n" +
                       "  [-1.0, [0.0, 0.0, 0.0, 0]],    // Transparent for NoData\n" +
                       "  [-0.2, [0.9, 0.8, 0.6, 1]],    // Dry or bare areas (pale yellow/light brown)\n" +
                       "  [ 0.0, [0.7, 0.6, 0.4, 1]],    // Low moisture (brownish)\n" +
                       "  [ 0.2, [0.4, 0.3, 0.1, 1]],    // Medium moisture (darker brown)\n" +
                       "  [ 0.4, [0.1, 0.1, 0.7, 1]],    // High moisture (dark blue)\n" +
                       "  [ 0.6, [0.05, 0.05, 0.5, 1]],  // Very high moisture (deeper blue)\n" +
                       "  [ 1.0, [0.0, 0.0, 0.3, 1]]     // Maximum moisture (very dark blue)\n" +
                       "];\n" +
                       "const moistureVisualizer = new ColorRampVisualizer(moistureRamp);\n" +
                       "\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) return [0, 0, 0, 0];\n" +
                       "  // NDMI formula (NIR - SWIR) / (NIR + SWIR)\n" +
                       "  let ndmi = (samples.B08 - samples.B11) / (samples.B08 + samples.B11);\n" +
                       "  let color = moistureVisualizer.process(ndmi);\n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * 255];\n" +
                       "}";
            case "NDSI":
            case "8-NDSI":
            case "8-NDSI-L1C":
                // NDSI (Normalized Difference Snow Index)
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    // Using B03 (Green) and B11 (SWIR1) for NDSI as per your image\n" +
                       "    input: [{ bands: [\"B03\", \"B11\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "\n" +
                       "const ndsiRamp = [\n" +
                       "  [-1.0, [0.0, 0.0, 0.0, 0]],   // Transparent for NoData\n" +
                       "  [ 0.0, [0.9, 0.1, 0.1, 1]],   // Red for non-snow/ice (-1 to 0)\n" +
                       "  [ 0.1, [0.9, 0.5, 0.1, 1]],   // Orange for very little snow/ice\n" +
                       "  [ 0.3, [0.9, 0.9, 0.1, 1]],   // Yellow for moderate snow/ice (0 to 0.5)\n" +
                       "  [ 0.5, [0.5, 0.7, 0.9, 1]],   // Light blue for significant snow/ice (0.5 to 1)\n" +
                       "  [ 0.7, [0.2, 0.4, 0.8, 1]],   // Blue for clear snow/ice\n" +
                       "  [ 1.0, [0.0, 0.0, 0.6, 1]]    // Dark blue for dense snow/ice\n" +
                       "];\n" +
                       "const ndsiVisualizer = new ColorRampVisualizer(ndsiRamp);\n" +
                       "\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) return [0, 0, 0, 0];\n" +
                       "  // NDSI formula (Green - SWIR) / (Green + SWIR)\n" +
                       "  let ndsi = (samples.B03 - samples.B11) / (samples.B03 + samples.B11);\n" +
                       "\n" +
                       "  let color = ndsiVisualizer.process(ndsi);\n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * 255];\n" +
                       "}";
            case "NDWI":
            case "7-NDWI":
            case "7-NDWI-L1C":
                // NDWI (Normalized Difference Water Index)
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    // Using B03 (Green) and B08 (NIR) for NDWI as per your image\n" +
                       "    input: [{ bands: [\"B03\", \"B08\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "\n" +
                       "const ndwiRamp = [\n" +
                       "  [-1.0, [0.0, 0.0, 0.0, 0]],    // Transparent for NoData\n" +
                       "  [-0.3, [0.9, 0.9, 0.7, 1]],    // Pale yellow to brown for drought/dry surfaces\n" +
                       "  [ 0.0, [0.7, 0.9, 0.7, 1]],    // Yellow-green for dry soil, vegetation\n" +
                       "  [ 0.2, [0.4, 0.8, 0.9, 1]],    // Cyan-turquoise for flooded areas or wet soil\n" +
                       "  [ 0.5, [0.1, 0.4, 0.8, 1]],    // Saturated blue for open water\n" +
                       "  [ 1.0, [0.0, 0.1, 0.5, 1]]     // Deep blue for maximum water presence\n" +
                       "];\n" +
                       "const ndwiVisualizer = new ColorRampVisualizer(ndwiRamp);\n" +
                       "\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) return [0, 0, 0, 0];\n" +
                       "  // NDWI formula (Green - NIR) / (Green + NIR)\n" +
                       "  let ndwi = (samples.B03 - samples.B08) / (samples.B03 + samples.B08);\n" +
                       "\n" +
                       "  let color = ndwiVisualizer.process(ndwi);\n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * 255];\n" +
                       "}";
            case "SWIR":
            case "6-SWIR":
            case "6-SWIR-L1C":
                return "//VERSION=3\n" +
                       "let minVal = 0.0; // Adjust as needed for better visualization\n" +
                       "let maxVal = 0.4; // Adjust as needed for better visualization\n" +
                       "let viz = new HighlightCompressVisualizer(minVal, maxVal);\n" +
                       "\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    // Corrected bands based on your image and L2A compatibility: B12, B08, B04.\n" +
                       "    // Using B08 instead of B08A to avoid 'no band B08A' error for S2L2A\n" +
                       "    input: [{ bands: [\"B12\", \"B08\", \"B04\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) {\n" +
                       "    return [0, 0, 0, 0];\n" +
                       "  }\n" +
                       "  // SWIR composite (SWIR2, NIR, Red) -> (B12, B08, B04) to (Red, Green, Blue)\n" +
                       "  // Vegetation - bright green (fluorescent)\n" +
                       "  // Water - black or blue\n" +
                       "  // Burned areas/fires - red/orange SWIR reflection areas\n" +
                       "  // Urban - white-turquoise, brownish-pink shades\n" +
                       "\n" +
                       "  // Use HighlightCompressVisualizer for better contrast\n" +
                       "  let val = [samples.B12, samples.B08, samples.B04, samples.dataMask];\n" +
                       "  let rgb_with_alpha = viz.processList(val);\n" +
                       "\n" +
                       "  return [\n" +
                       "    rgb_with_alpha[0] * 255,\n" +
                       "    rgb_with_alpha[1] * 255,\n" +
                       "    rgb_with_alpha[2] * 255,\n" +
                       "    rgb_with_alpha[3] * 255\n" +
                       "  ];\n" +
                       "}";
            case "SCENE_CLASSIFICATION":
            case "SCENE-CLASSIFICATION":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"SCL\", \"dataMask\"], sampleType: \"UINT8\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) return [0, 0, 0, 0];\n" +
                       "  let scl = samples.SCL;\n" +
                       "  let color = [0, 0, 0, 0]; \n" +
                       "\n" +
                       "  if (scl === 1) color = [0.65, 0.65, 0.65, 1]; // SC_SATURATED_DEFECTIVE (Серый)\n" +
                       "  else if (scl === 2) color = [0.8, 0.8, 0.8, 1]; // SC_DARK_FEATURE_SHADOW (Светло-серый)\n" +
                       "  else if (scl === 3) color = [0.9, 0.9, 0.9, 1]; // SC_CLOUD_SHADOW (Белый/очень светло-серый)\n" +
                       "  else if (scl === 4) color = [0.1, 0.5, 0.1, 1]; // SC_VEGETATION (Зеленый)\n" +
                       "  else if (scl === 5) color = [0.8, 0.6, 0.2, 1]; // SC_NOT_VEGETATED (Коричневый)\n" +
                       "  else if (scl === 6) color = [0.1, 0.1, 0.8, 1]; // SC_WATER (Синий)\n" +
                       "  else if (scl === 7) color = [0.9, 0.9, 0.1, 1]; // SC_CLOUD_LOW_PROBA (Светло-желтый)\n" +
                       "  else if (scl === 8) color = [0.7, 0.7, 0.7, 1]; // SC_CLOUD_MEDIUM_PROBA (Средне-серый)\n" +
                       "  else if (scl === 9) color = [0.9, 0.9, 0.9, 1]; // SC_CLOUD_HIGH_PROBA (Белый)\n" +
                       "  else if (scl === 10) color = [0.9, 0.9, 0.9, 1]; // SC_THIN_CIRRUS (Белый)\n" +
                       "  else if (scl === 11) color = [0.9, 0.9, 0.9, 1]; // SC_SNOW_ICE (Белый)\n" +
                       "  \n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * 255];\n" +
                       "}";
            case "HIGHLIGHT_OPTIMIZED_NATURAL_COLOR":
            case "2_TONEMAPPED_NATURAL_COLOR":
            case "2_TONEMAPPED-NATURAL-COLOR-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B02\", \"B03\", \"B04\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) {\n" +
                       "    return [0, 0, 0, 0];\n" +
                       "  }\n" +
                       "  \n" +
                       "  // Применение легкой тональной компрессии (tone mapping) для улучшения яркости\n" +
                       "  // и предотвращения перенасыщения в ярких областях\n" +
                       "  let factor = 2.5; // Коэффициент усиления\n" +
                       "  let R = samples.B04 * factor;\n" +
                       "  let G = samples.B03 * factor;\n" +
                       "  let B = samples.B02 * factor;\n" +
                       "\n" +
                       "  // Простой алгоритм тональной компрессии (например, на основе логистической функции)\n" +
                       "  R = R / (R + 1);\n" +
                       "  G = G / (G + 1);\n" +
                       "  B = B / (B + 1);\n" +
                       "\n" +
                       "  return [R * 255, G * 255, B * 255, samples.dataMask * 255];\n" +
                       "}";

            default:
                throw new IllegalArgumentException("Unsupported analysis type: " + analysisType);
        }
    }
}