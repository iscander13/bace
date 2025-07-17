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
     * Возвращает evalscript для различных типов анализа.
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
                   "// Улучшенный плавный градиент NDVI с явным альфа-каналом для более четких переходов от желтого к зеленому\n" +
                   "const ramp = [\n" +
                   "  [-1.0, [0.0, 0.0, 0.0, 0]],   // Полностью прозрачный для значений < -0.2 (вода, облака, снег, нет данных)\n" +
                   "  [-0.2, [0.0, 0.0, 0.0, 0]],   // Продолжаем прозрачность\n" +
                   "  [ 0.0, [0.95, 0.95, 0.95, 0.6]], // Почти белый/светло-серый для голой почвы/очень редкой растительности, полупрозрачный\n" +
                   "  [ 0.05, [0.98, 0.9, 0.5, 1]], // Светло-желтый (едва заметная растительность или сухая трава)\n" +
                   "  [ 0.1, [0.93, 0.85, 0.4, 1]], // Желтый (начало вегетации, стрессовое состояние)\n" +
                   "  [ 0.15, [0.85, 0.75, 0.3, 1]], // Более насыщенный желтый\n" +
                   "  [ 0.2, [0.75, 0.7, 0.25, 1]], // Желто-зеленый переход\n" +
                   "  [ 0.25, [0.6, 0.65, 0.2, 1]], // Салатовый (молодая или не очень здоровая растительность)\n" +
                   "  [ 0.3, [0.45, 0.6, 0.15, 1]], // Светло-зеленый\n" +
                   "  [ 0.35, [0.3, 0.55, 0.1, 1]], // Средне-зеленый\n" +
                   "  [ 0.4, [0.15, 0.5, 0.05, 1]], // Темно-зеленый (здоровая растительность)\n" +
                   "  [ 0.5, [0.05, 0.4, 0.0, 1]], // Очень темно-зеленый\n" +
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
                   "}\n" +
                   "// Функция для определения, является ли пиксель облаком на основе SCL (Scene Classification Layer)\n" +
                   "function isCloud (scl) {\n" +
                   "  if (scl === 3) { // SC_CLOUD_SHADOW (Тени от облаков) - не облако\n" +
                   "    return false;\n" +
                   "  } else if (scl === 9) { // SC_CLOUD_HIGH_PROBA (Высокая вероятность облаков) - облако\n" +
                   "    return true;\n" +
                   "  } else if (scl === 8) { // SC_CLOUD_MEDIUM_PROBA (Средняя вероятность облаков) - облако\n" +
                   "    return true;\n" +
                   "  } else if (scl === 7) { // SC_CLOUD_LOW_PROBA (Низкая вероятность облаков) - не облако\n" +
                   "    return false;\n" +
                   "  } else if (scl === 10) { // SC_THIN_CIRRUS (Тонкие перистые облака) - облако\n" +
                   "    return true;\n" +
                   "  } else if (scl === 11) { // SC_SNOW_ICE (Снег / Лед) - не облако\n" +
                   "    return false;\n" +
                   "  } else if (scl === 1) { // SC_SATURATED_DEFECTIVE (Насыщенные / Дефектные пиксели) - не облако\n" +
                   "    return false;\n" +
                   "  } else if (scl === 2) { // SC_DARK_FEATURE_SHADOW (Тени от темных объектов) - не облако\n" +
                   "    return false;\n" +
                   "  }\n" +
                   "  return false; // По умолчанию, если значение SCL не соответствует известным типам облаков\n" +
                   "}";
            case "TRUE_COLOR":
            case "1_TRUE_COLOR":
            case "1_TRUE-COLOR-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B02\", \"B03\", \"B04\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  return [samples.B04 * 255, samples.B03 * 255, samples.B02 * 255, samples.dataMask * 255];\n" +
                       "}";
            case "FALSE_COLOR":
            case "2_FALSE_COLOR":
            case "2_FALSE-COLOR-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B08\", \"B04\", \"B03\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  return [samples.B08 * 255, samples.B04 * 255, samples.B03 * 255, samples.dataMask * 255];\n" +
                       "}";
            case "FALSE_COLOR_URBAN":
            case "4-FALSE-COLOR-URBAN":
            case "4-FALSE-COLOR-URBAN-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B11\", \"B08\", \"B04\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  return [samples.B11 * 255, samples.B08 * 255, samples.B04 * 255, samples.dataMask * 255];\n" +
                       "}";
            case "MOISTURE_INDEX":
            case "5-MOISTURE-INDEX1":
            case "5-MOISTURE-INDEX1-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B08\", \"B11\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) return [0, 0, 0, 0];\n" +
                       "  let val = (samples.B08 - samples.B11) / (samples.B08 + samples.B11);\n" +
                       "  let color = colorBlend(val, [-1, -0.2, 0, 0.2, 0.4, 0.6, 0.8, 1], [\n" +
                       "    [0, 0, 0, 0], \n" +
                       "    [0.9, 0.9, 0.9, 1], \n" +
                       "    [0.9, 0.7, 0.7, 1], \n" +
                       "    [0.7, 0.5, 0.5, 1], \n" +
                       "    [0.5, 0.3, 0.3, 1], \n" +
                       "    [0.3, 0.1, 0.1, 1], \n" +
                       "    [0.1, 0.0, 0.0, 1], \n" +
                       "    [0.0, 0.0, 0.0, 1]  \n" +
                       "  ]);\n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * 255];\n" +
                       "}";
            case "NDSI":
            case "8-NDSI":
            case "8-NDSI-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B03\", \"B11\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) return [0, 0, 0, 0];\n" +
                       "  let val = (samples.B03 - samples.B11) / (samples.B03 + samples.B11);\n" +
                       "  let color = colorBlend(val, [-1, 0, 0.2, 0.4, 0.6, 1], [\n" +
                       "    [0, 0, 0, 0], \n" +
                       "    [0.7, 0.7, 0.7, 1], \n" +
                       "    [0.5, 0.8, 0.9, 1], \n" +
                       "    [0.8, 0.9, 1.0, 1], \n" +
                       "    [0.9, 0.9, 1.0, 1], \n" +
                       "    [1.0, 1.0, 1.0, 1]  \n" +
                       "  ]);\n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * 255];\n" +
                       "}";
            case "NDWI":
            case "7-NDWI":
            case "7-NDWI-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B03\", \"B08\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  if (samples.dataMask === 0) return [0, 0, 0, 0];\n" +
                       "  let val = (samples.B03 - samples.B08) / (samples.B03 + samples.B08);\n" +
                       "  let color = colorBlend(val, [-1, -0.2, 0, 0.2, 0.4, 0.6, 1], [\n" +
                       "    [0, 0, 0, 0], \n" +
                       "    [0.9, 0.9, 0.9, 1], \n" +
                       "    [0.7, 0.7, 0.9, 1], \n" +
                       "    [0.5, 0.5, 0.9, 1], \n" +
                       "    [0.3, 0.3, 0.7, 1], \n" +
                       "    [0.1, 0.1, 0.5, 1], \n" +
                       "    [0.0, 0.0, 0.3, 1]  \n" +
                       "  ]);\n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * 255];\n" +
                       "}";
            case "SWIR":
            case "6-SWIR":
            case "6-SWIR-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B12\", \"B11\", \"B08\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  return [samples.B12 * 255, samples.B11 * 255, samples.B08 * 255, samples.dataMask * 255];\n" +
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
                       "  if (scl === 1) color = [0.65, 0.65, 0.65, 1]; \n" +
                       "  else if (scl === 2) color = [0.8, 0.8, 0.8, 1]; \n" +
                       "  else if (scl === 3) color = [0.9, 0.9, 0.9, 1]; \n" +
                       "  else if (scl === 4) color = [0.1, 0.5, 0.1, 1]; \n" +
                       "  else if (scl === 5) color = [0.8, 0.6, 0.2, 1]; \n" +
                       "  else if (scl === 6) color = [0.1, 0.1, 0.8, 1]; \n" +
                       "  else if (scl === 7) color = [0.9, 0.9, 0.1, 1]; \n" +
                       "  else if (scl === 8) color = [0.7, 0.7, 0.7, 1]; \n" +
                       "  else if (scl === 9) color = [0.9, 0.9, 0.9, 1]; \n" +
                       "  else if (scl === 10) color = [0.9, 0.9, 0.9, 1]; \n" +
                       "  else if (scl === 11) color = [0.9, 0.9, 0.9, 1]; \n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * 255];\n" +
                       "}";
            case "HIGHLIGHT_OPTIMIZED_NATURAL_COLOR":
            case "2_TONEMAPPED_NATURAL_COLOR":
            case "2_TONEMAPPED-NATURAL-COLOR-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B02\", \"B03\", \"B04\", \"dataMask\"], sampleType: \"FLOAT32\" }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" +
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  return [samples.B04 * 255, samples.B03 * 255, samples.B02 * 255, samples.dataMask * 255];\n" +
                       "}";

            default:
                throw new IllegalArgumentException("Unsupported analysis type: " + analysisType);
        }
    }
}