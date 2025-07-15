package com.dahe.v2.modules.export.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dahe.v2.common.ErrorCode;
import com.dahe.v2.common.Result;
import com.dahe.v2.modules.dynamic.model.DynamicFormConfig;
import com.dahe.v2.modules.dynamic.service.DynamicFormConfigService;
import com.dahe.v2.modules.export.service.ExportCsvCommand;
import com.dahe.v2.modules.export.service.ExportCsvFacadeService;
import com.dahe.v2.modules.farm.model.FarmRecord;
import com.dahe.v2.modules.farm.process.model.FarmProcessStep;
import com.dahe.v2.modules.farm.process.service.FarmProcessStepService;
import com.dahe.v2.modules.farm.service.FarmRecordService;
import com.dahe.v2.modules.field.model.Field;
import com.dahe.v2.modules.field.service.FieldService;
import com.dahe.v2.modules.seed.model.SeedBatch;
import com.dahe.v2.modules.seed.model.SeedQualityTest;
import com.dahe.v2.modules.seed.service.SeedBatchService;
import com.dahe.v2.modules.seed.service.SeedQualityTestService;
import com.dahe.v2.modules.export.model.ExportFieldDict;
import com.dahe.v2.modules.export.model.ExportTemplate;
import com.dahe.v2.modules.export.service.ExportFieldDictService;
import com.dahe.v2.modules.export.service.ExportServiceException;
import com.dahe.v2.modules.export.service.ExportTemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

    /**
 * 导出控制器。
 * 提供农事记录与种子检测的 CSV 导出能力，并支持：
 * 1. 基于导出模板控制字段顺序；
 * 2. 基于字段词典输出列头中文名；
 * 3. 自动合并动态字段（extra_json + dynamic_form_config）。
 */
@Service
public class ExportCsvFacadeServiceImpl implements ExportCsvFacadeService {

    private static final Logger log = LoggerFactory.getLogger(ExportCsvFacadeServiceImpl.class);

    /** 日期时间输出格式。 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 日期输出格式。 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 农事记录默认导出模板编码。 */
    private static final String DEFAULT_FARM_TEMPLATE = "farm_records_standard";
    /** 种子检测默认导出模板编码。 */
    private static final String DEFAULT_SEED_TEMPLATE = "seed_tests_standard";
    private static final int MAX_EXPORT_ROWS = 20000;

    private final FarmRecordService farmRecordService;
    private final FieldService fieldService;
    private final FarmProcessStepService farmProcessStepService;
    private final SeedQualityTestService seedQualityTestService;
    private final SeedBatchService seedBatchService;
    private final ExportTemplateService exportTemplateService;
    private final ExportFieldDictService exportFieldDictService;
    private final DynamicFormConfigService dynamicFormConfigService;
    private final ObjectMapper objectMapper;

    public ExportCsvFacadeServiceImpl(
            FarmRecordService farmRecordService,
            FieldService fieldService,
            FarmProcessStepService farmProcessStepService,
            SeedQualityTestService seedQualityTestService,
            SeedBatchService seedBatchService,
            ExportTemplateService exportTemplateService,
            ExportFieldDictService exportFieldDictService,
            DynamicFormConfigService dynamicFormConfigService,
            ObjectMapper objectMapper
    ) {
        this.farmRecordService = farmRecordService;
        this.fieldService = fieldService;
        this.farmProcessStepService = farmProcessStepService;
        this.seedQualityTestService = seedQualityTestService;
        this.seedBatchService = seedBatchService;
        this.exportTemplateService = exportTemplateService;
        this.exportFieldDictService = exportFieldDictService;
        this.dynamicFormConfigService = dynamicFormConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    /**
     * 导出农事记录 CSV。
     * 支持按田块、计划、年份、乡镇关键词和时间范围过滤。
     */
    public Object exportFarmRecordsCsv(ExportCsvCommand.FarmQuery query, HttpServletResponse response) {
        try {
        ExportCsvCommand.FarmQuery safeQuery = query == null ? new ExportCsvCommand.FarmQuery() : query;
        LocalDateTime rangeStart = safeQuery.getStartDate();
        LocalDateTime rangeEnd = safeQuery.getEndDate();
        if (safeQuery.getYear() != null && safeQuery.getYear() >= 2000 && safeQuery.getYear() <= 2100) {
            if (rangeStart == null) {
                rangeStart = LocalDateTime.of(safeQuery.getYear(), 1, 1, 0, 0, 0);
            }
            if (rangeEnd == null) {
                rangeEnd = LocalDateTime.of(safeQuery.getYear(), 12, 31, 23, 59, 59);
            }
        }
        LambdaQueryWrapper<FarmRecord> qw = new LambdaQueryWrapper<>();
        if (safeQuery.getFieldId() != null) {
            qw.eq(FarmRecord::getFieldId, safeQuery.getFieldId());
        }
        if (safeQuery.getCycleId() != null) {
            qw.eq(FarmRecord::getCycleId, safeQuery.getCycleId());
        }
        if (StringUtils.hasText(safeQuery.getTownship())) {
            Set<Long> fieldIds = resolveFieldIdsByRegionKeyword(safeQuery.getTownship());
            if (fieldIds.isEmpty()) {
                return writeEmptyFarmCsv(response);
            }
            qw.in(FarmRecord::getFieldId, fieldIds);
        }
        if (rangeStart != null) {
            qw.ge(FarmRecord::getWorkDate, rangeStart);
        }
        if (rangeEnd != null) {
            qw.le(FarmRecord::getWorkDate, rangeEnd);
        }
        qw.orderByDesc(FarmRecord::getWorkDate).orderByDesc(FarmRecord::getId);
        List<FarmRecord> records = farmRecordService.list(qw);
        checkExportRowLimit(records.size());

        Set<Long> fieldIds = records.stream().map(FarmRecord::getFieldId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> stepIds = records.stream().map(FarmRecord::getStepId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Field> fieldMap = fieldIds.isEmpty()
                ? Collections.emptyMap()
                : fieldService.listByIds(fieldIds).stream().collect(Collectors.toMap(Field::getId, x -> x, (a, b) -> a));
        Map<Long, FarmProcessStep> stepMap = stepIds.isEmpty()
                ? Collections.emptyMap()
                : farmProcessStepService.listByIds(stepIds).stream().collect(Collectors.toMap(FarmProcessStep::getId, x -> x, (a, b) -> a));

        List<String> fallbackCodes = Arrays.asList(
                "recordId", "fieldId", "fieldName", "cycleId", "stepId", "stepName",
                "workDate", "operatorName", "weather", "temperature", "weatherLocation",
                "humidity", "windDirection", "windPower", "weatherReportTime",
                "notes", "extraJson"
        );
        LinkedHashSet<String> dynamicKeys = new LinkedHashSet<>();
        Map<Long, Map<String, String>> extraMapByRecord = new HashMap<>();
        for (FarmRecord row : records) {
            Map<String, String> extra = parseExtraJson(row.getExtraJson());
            if (!extra.isEmpty()) {
                extraMapByRecord.put(row.getId(), extra);
                dynamicKeys.addAll(extra.keySet());
            }
        }

        Map<String, String> farmDynamicHeaders = resolveFarmDynamicHeaders(records, stepMap);
        List<Column> columns = resolveColumns("farm", StringUtils.hasText(safeQuery.getTemplateCode()) ? safeQuery.getTemplateCode() : DEFAULT_FARM_TEMPLATE, fallbackCodes);
        columns = appendDynamicColumns(columns, dynamicKeys, farmDynamicHeaders);
        List<String[]> rows = new ArrayList<>();
        for (FarmRecord row : records) {
            Field field = row.getFieldId() == null ? null : fieldMap.get(row.getFieldId());
            FarmProcessStep step = row.getStepId() == null ? null : stepMap.get(row.getStepId());
            Map<String, String> values = new HashMap<>();
            values.put("recordId", toText(row.getId()));
            values.put("fieldId", toText(row.getFieldId()));
            values.put("fieldName", field == null ? "" : toText(field.getName()));
            values.put("cycleId", toText(row.getCycleId()));
            values.put("stepId", toText(row.getStepId()));
            values.put("stepName", step == null ? "" : toText(step.getStepName()));
            values.put("workDate", formatDateTime(row.getWorkDate()));
            values.put("operatorName", toText(row.getOperatorName()));
            values.put("weather", toText(row.getWeather()));
            values.put("temperature", toText(row.getTemperature()));
            values.put("weatherLocation", toText(row.getWeatherLocation()));
            values.put("humidity", toText(row.getHumidity()));
            values.put("windDirection", toText(row.getWindDirection()));
            values.put("windPower", toText(row.getWindPower()));
            values.put("weatherReportTime", toText(row.getWeatherReportTime()));
            values.put("notes", toText(row.getNotes()));
            values.put("extraJson", toText(row.getExtraJson()));
            Map<String, String> extra = row.getId() == null ? Collections.emptyMap() : extraMapByRecord.getOrDefault(row.getId(), Collections.emptyMap());
            values.putAll(extra);
            rows.add(buildRow(columns, values));
        }

        writeCsv(
                response,
                "farm-records.csv",
                columns.stream().map(Column::getHeader).toArray(String[]::new),
                rows
        );
        return null;
        } catch (ExportServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("Export farm records failed: {}", ex.getMessage());
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "农事记录导出失败，请稍后重试");
        }
    }

    @Override
    /**
     * 导出种子检测 CSV。
     * 支持按批次、年份、检测日期范围过滤。
     */
    public Object exportSeedTestsCsv(ExportCsvCommand.SeedQuery query, HttpServletResponse response) {
        try {
        ExportCsvCommand.SeedQuery safeQuery = query == null ? new ExportCsvCommand.SeedQuery() : query;
        LocalDate rangeStart = safeQuery.getStartDate();
        LocalDate rangeEnd = safeQuery.getEndDate();
        if (safeQuery.getYear() != null && safeQuery.getYear() >= 2000 && safeQuery.getYear() <= 2100) {
            if (rangeStart == null) {
                rangeStart = LocalDate.of(safeQuery.getYear(), 1, 1);
            }
            if (rangeEnd == null) {
                rangeEnd = LocalDate.of(safeQuery.getYear(), 12, 31);
            }
        }
        LambdaQueryWrapper<SeedQualityTest> qw = new LambdaQueryWrapper<>();
        if (safeQuery.getBatchId() != null) {
            qw.eq(SeedQualityTest::getBatchId, safeQuery.getBatchId());
        }
        if (rangeStart != null) {
            qw.ge(SeedQualityTest::getTestDate, rangeStart);
        }
        if (rangeEnd != null) {
            qw.le(SeedQualityTest::getTestDate, rangeEnd);
        }
        qw.orderByDesc(SeedQualityTest::getTestDate).orderByDesc(SeedQualityTest::getId);
        List<SeedQualityTest> records = seedQualityTestService.list(qw);
        checkExportRowLimit(records.size());

        Set<Long> batchIds = records.stream().map(SeedQualityTest::getBatchId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SeedBatch> batchMap = batchIds.isEmpty()
                ? Collections.emptyMap()
                : seedBatchService.listByIds(batchIds).stream().collect(Collectors.toMap(SeedBatch::getId, x -> x, (a, b) -> a));

        List<String> fallbackCodes = Arrays.asList(
                "testId", "batchId", "batchCode", "cropType", "varietyName", "testDate", "sampleCount", "germinationCount",
                "germinationRate", "moisture", "purity", "cleanliness", "testerName", "remark", "createdAt"
        );
        LinkedHashSet<String> dynamicKeys = new LinkedHashSet<>();
        Map<Long, Map<String, String>> testExtraMap = new HashMap<>();
        Map<Long, Map<String, String>> batchExtraMap = new HashMap<>();
        for (SeedQualityTest row : records) {
            Map<String, String> testExtra = parseExtraJson(row.getExtraJson());
            if (!testExtra.isEmpty()) {
                Map<String, String> prefixed = prefixKeys("test_", testExtra);
                testExtraMap.put(row.getId(), prefixed);
                dynamicKeys.addAll(prefixed.keySet());
            }
            SeedBatch batch = row.getBatchId() == null ? null : batchMap.get(row.getBatchId());
            if (batch == null || batch.getId() == null || batchExtraMap.containsKey(batch.getId())) {
                continue;
            }
            Map<String, String> batchExtra = parseExtraJson(batch.getExtraJson());
            if (!batchExtra.isEmpty()) {
                Map<String, String> prefixed = prefixKeys("batch_", batchExtra);
                batchExtraMap.put(batch.getId(), prefixed);
                dynamicKeys.addAll(prefixed.keySet());
            }
        }

        Map<String, String> seedDynamicHeaders = resolveSeedDynamicHeaders(records, batchMap);
        List<Column> columns = resolveColumns("seed", StringUtils.hasText(safeQuery.getTemplateCode()) ? safeQuery.getTemplateCode() : DEFAULT_SEED_TEMPLATE, fallbackCodes);
        columns = appendDynamicColumns(columns, dynamicKeys, seedDynamicHeaders);
        List<String[]> rows = new ArrayList<>();
        for (SeedQualityTest row : records) {
            SeedBatch batch = row.getBatchId() == null ? null : batchMap.get(row.getBatchId());
            Map<String, String> values = new HashMap<>();
            values.put("testId", toText(row.getId()));
            values.put("batchId", toText(row.getBatchId()));
            values.put("batchCode", batch == null ? "" : toText(batch.getBatchCode()));
            values.put("cropType", batch == null ? "" : toText(batch.getCropType()));
            values.put("varietyName", batch == null ? "" : toText(batch.getVarietyName()));
            values.put("testDate", formatDate(row.getTestDate()));
            values.put("sampleCount", toText(row.getSampleCount()));
            values.put("germinationCount", toText(row.getGerminationCount()));
            values.put("germinationRate", toNumberText(row.getGerminationRate()));
            values.put("moisture", toNumberText(row.getMoisture()));
            values.put("purity", toNumberText(row.getPurity()));
            values.put("cleanliness", toNumberText(row.getCleanliness()));
            values.put("testerName", toText(row.getTesterName()));
            values.put("remark", toText(row.getRemark()));
            values.put("createdAt", formatDateTime(row.getCreatedAt()));
            if (row.getId() != null) {
                values.putAll(testExtraMap.getOrDefault(row.getId(), Collections.emptyMap()));
            }
            if (batch != null && batch.getId() != null) {
                values.putAll(batchExtraMap.getOrDefault(batch.getId(), Collections.emptyMap()));
            }
            rows.add(buildRow(columns, values));
        }

        writeCsv(
                response,
                "seed-tests.csv",
                columns.stream().map(Column::getHeader).toArray(String[]::new),
                rows
        );
        return null;
        } catch (ExportServiceException ex) {
            return Result.failure(ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("Export seed tests failed: {}", ex.getMessage());
            return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), "种子检测导出失败，请稍后重试");
        }
    }

    /** 解析最终导出列：优先模板，其次默认字段。 */
    private List<Column> resolveColumns(String moduleKey, String templateCode, List<String> fallbackCodes) {
        if (!StringUtils.hasText(moduleKey) || !StringUtils.hasText(templateCode) || fallbackCodes == null || fallbackCodes.isEmpty()) {
            return toFallbackColumns(moduleKey, fallbackCodes);
        }
        ExportTemplate template = null;
        try {
            template = exportTemplateService.findEnabledLatest(moduleKey, templateCode);
        } catch (Exception ex) {
            log.warn("Load export template failed, fallback to defaults. module={}, templateCode={}, cause={}",
                    moduleKey, templateCode, ex.getMessage());
        }
        if (template == null) {
            return toFallbackColumns(moduleKey, fallbackCodes);
        }

        List<String> fieldCodes = exportTemplateService.parseFieldCodes(template.getFieldsJson());
        if (fieldCodes.isEmpty()) {
            return toFallbackColumns(moduleKey, fallbackCodes);
        }
        Map<String, ExportFieldDict> dictMap = exportFieldDictService.mapByModuleAndCodes(moduleKey, fieldCodes);

        List<Column> columns = new ArrayList<>();
        for (String code : fieldCodes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            ExportFieldDict dict = dictMap.get(code);
            String header = dict == null || !StringUtils.hasText(dict.getFieldName())
                    ? defaultHeader(moduleKey, code)
                    : dict.getFieldName();
            columns.add(new Column(code, header));
        }
        return columns.isEmpty() ? toFallbackColumns(moduleKey, fallbackCodes) : columns;
    }

    /** 将默认字段编码列表转换为导出列定义。 */
    private List<Column> toFallbackColumns(String moduleKey, List<String> fallbackCodes) {
        List<Column> columns = new ArrayList<>();
        if (fallbackCodes == null) {
            return columns;
        }
        for (String code : fallbackCodes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            columns.add(new Column(code, defaultHeader(moduleKey, code)));
        }
        return columns;
    }

    /** 兜底列头映射（当词典未命中时使用）。 */
    private String defaultHeader(String moduleKey, String code) {
        String c = StringUtils.hasText(code) ? code.trim() : "";
        if (!StringUtils.hasText(c)) {
            return "";
        }
        String m = StringUtils.hasText(moduleKey) ? moduleKey.trim().toLowerCase(Locale.ROOT) : "";
        if ("farm".equals(m)) {
            switch (c) {
                case "recordId":
                    return "农事记录ID";
                case "fieldId":
                    return "田块ID";
                case "fieldName":
                    return "田块名称";
                case "cycleId":
                    return "种植计划ID";
                case "stepId":
                    return "步骤ID";
                case "stepName":
                    return "步骤名称";
                case "workDate":
                    return "作业时间";
                case "operatorName":
                    return "操作员";
                case "weather":
                    return "天气";
                case "temperature":
                    return "温度";
                case "weatherLocation":
                    return "天气位置";
                case "humidity":
                    return "湿度(%)";
                case "windDirection":
                    return "风向";
                case "windPower":
                    return "风力";
                case "weatherReportTime":
                    return "天气发布时间";
                case "notes":
                    return "备注";
                case "extraJson":
                    return "扩展参数";
                default:
                    return c;
            }
        }
        if ("seed".equals(m)) {
            switch (c) {
                case "testId":
                    return "检测ID";
                case "batchId":
                    return "批次ID";
                case "batchCode":
                    return "批次号";
                case "cropType":
                    return "作物";
                case "varietyName":
                    return "品种";
                case "testDate":
                    return "检测日期";
                case "sampleCount":
                    return "芽率样本数";
                case "germinationCount":
                    return "发芽数";
                case "germinationRate":
                    return "芽率(%)";
                case "moisture":
                    return "水分(%)";
                case "purity":
                    return "纯度(%)";
                case "cleanliness":
                    return "净度(%)";
                case "testerName":
                    return "检测员";
                case "remark":
                    return "备注";
                case "createdAt":
                    return "创建时间";
                default:
                    return c;
            }
        }
        return c;
    }
    private List<Column> appendDynamicColumns(List<Column> columns, Set<String> dynamicKeys, Map<String, String> dynamicHeaders) {
        if (dynamicKeys == null || dynamicKeys.isEmpty()) {
            return columns;
        }
        List<Column> out = new ArrayList<>(columns);
        Set<String> existing = out.stream().map(x -> x.code).collect(Collectors.toSet());
        for (String key : dynamicKeys) {
            if (!StringUtils.hasText(key) || existing.contains(key)) {
                continue;
            }
            String header = dynamicHeaders == null ? null : dynamicHeaders.get(key);
            out.add(new Column(key, StringUtils.hasText(header) ? header : key));
            existing.add(key);
        }
        return out;
    }

    /** 汇总农事记录动态字段列头。 */
    private Map<String, String> resolveFarmDynamicHeaders(List<FarmRecord> records, Map<Long, FarmProcessStep> stepMap) {
        if (records == null || records.isEmpty() || stepMap == null || stepMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> configIds = new LinkedHashSet<>();
        for (FarmProcessStep step : stepMap.values()) {
            if (step != null && step.getFormConfigId() != null) {
                configIds.add(step.getFormConfigId());
            }
        }
        Map<Long, String> schemaByConfigId = loadSchemaMapByConfigIds(configIds);
        Map<String, String> headers = new LinkedHashMap<>();
        for (FarmProcessStep step : stepMap.values()) {
            if (step == null) {
                continue;
            }
            String schema = resolveStepSchema(step, schemaByConfigId);
            mergeSchemaLabels(headers, schema, null, null);
        }
        return headers;
    }

    /** 汇总种子检测与种子批次动态字段列头。 */
    private Map<String, String> resolveSeedDynamicHeaders(List<SeedQualityTest> records, Map<Long, SeedBatch> batchMap) {
        if ((records == null || records.isEmpty()) && (batchMap == null || batchMap.isEmpty())) {
            return Collections.emptyMap();
        }
        Set<Long> configIds = new LinkedHashSet<>();
        if (records != null) {
            for (SeedQualityTest row : records) {
                if (row != null && row.getFormConfigId() != null) {
                    configIds.add(row.getFormConfigId());
                }
            }
        }
        if (batchMap != null) {
            for (SeedBatch batch : batchMap.values()) {
                if (batch != null && batch.getFormConfigId() != null) {
                    configIds.add(batch.getFormConfigId());
                }
            }
        }
        Map<Long, String> schemaByConfigId = loadSchemaMapByConfigIds(configIds);
        Map<String, String> headers = new LinkedHashMap<>();
        Set<Long> handledTestCfgIds = new LinkedHashSet<>();
        if (records != null) {
            for (SeedQualityTest row : records) {
                if (row == null || row.getFormConfigId() == null || !handledTestCfgIds.add(row.getFormConfigId())) {
                    continue;
                }
                mergeSchemaLabels(headers, schemaByConfigId.get(row.getFormConfigId()), "test_", "检测-");
            }
        }
        Set<Long> handledBatchCfgIds = new LinkedHashSet<>();
        if (batchMap != null) {
            for (SeedBatch batch : batchMap.values()) {
                if (batch == null || batch.getFormConfigId() == null || !handledBatchCfgIds.add(batch.getFormConfigId())) {
                    continue;
                }
                mergeSchemaLabels(headers, schemaByConfigId.get(batch.getFormConfigId()), "batch_", "批次-");
            }
        }
        return headers;
    }

    /** 按配置 ID 批量读取 schema_json。 */
    private Map<Long, String> loadSchemaMapByConfigIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> out = new HashMap<>();
        List<DynamicFormConfig> rows = dynamicFormConfigService.listByIds(ids);
        for (DynamicFormConfig row : rows) {
            if (row == null || row.getId() == null || !StringUtils.hasText(row.getSchemaJson())) {
                continue;
            }
            out.put(row.getId(), row.getSchemaJson());
        }
        return out;
    }

    /** 农事步骤 schema 解析：优先步骤内置 schema，其次配置表。 */
    private String resolveStepSchema(FarmProcessStep step, Map<Long, String> schemaByConfigId) {
        if (step == null) {
            return null;
        }
        if (StringUtils.hasText(step.getFormSchema())) {
            return step.getFormSchema();
        }
        if (step.getFormConfigId() == null || schemaByConfigId == null) {
            return null;
        }
        return schemaByConfigId.get(step.getFormConfigId());
    }

    /** 从 schema JSON 中提取 key/label，组装动态字段列头。 */
    private void mergeSchemaLabels(Map<String, String> out, String schemaJson, String keyPrefix, String labelPrefix) {
        if (out == null || !StringUtils.hasText(schemaJson)) {
            return;
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(schemaJson, new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                String key = toText(row.get("key")).trim();
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                String keyOut = StringUtils.hasText(keyPrefix) ? (keyPrefix + key) : key;
                if (out.containsKey(keyOut)) {
                    continue;
                }
                String label = toText(row.get("label")).trim();
                if (!StringUtils.hasText(label)) {
                    label = key;
                }
                if (StringUtils.hasText(labelPrefix)) {
                    label = labelPrefix + label;
                }
                out.put(keyOut, label);
            }
        } catch (Exception ex) {
            log.warn("Parse dynamic schema failed: {}", ex.getMessage());
        }
    }

    /** 根据列定义构造单行导出数据。 */
    private String[] buildRow(List<Column> columns, Map<String, String> values) {
        if (columns == null || columns.isEmpty()) {
            return new String[0];
        }
        String[] row = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            row[i] = values == null ? "" : toText(values.get(column.code));
        }
        return row;
    }
    /** 统一写出 UTF-8 BOM CSV。 */
    private void writeCsv(HttpServletResponse response, String fileName, String[] header, List<String[]> rows) {
        try {
            String encoded = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            OutputStream os = response.getOutputStream();
            os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            os.write(toCsvLine(header).getBytes(StandardCharsets.UTF_8));
            for (String[] row : rows) {
                os.write(toCsvLine(row).getBytes(StandardCharsets.UTF_8));
            }
            os.flush();
        } catch (Exception ex) {
            throw new ExportServiceException(ErrorCode.INTERNAL_ERROR.getCode(), "导出文件写入失败");
        }
    }

    /** 将一行列值序列化为 CSV 行。 */
    private String toCsvLine(String[] cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(escapeCsv(cols[i]));
        }
        sb.append("\n");
        return sb.toString();
    }

    /** CSV 转义，处理逗号、引号和换行。 */
    private String escapeCsv(String text) {
        String value = text == null ? "" : text;
        boolean needQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        value = value.replace("\"", "\"\"");
        return needQuote ? ("\"" + value + "\"") : value;
    }

    /** 安全转字符串。 */
    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 数字转文本，去掉无意义尾随 0。 */
    private String toNumberText(Double value) {
        if (value == null) {
            return "";
        }
        BigDecimal n = BigDecimal.valueOf(value).stripTrailingZeros();
        return n.toPlainString();
    }

    /** 解析动态参数 JSON 对象为键值对。 */
    private Map<String, String> parseExtraJson(String extraJson) {
        if (!StringUtils.hasText(extraJson)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(extraJson, new TypeReference<Map<String, Object>>() {});
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                String key = entry.getKey();
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                out.put(key.trim(), toText(entry.getValue()));
            }
            return out;
        } catch (Exception ex) {
            log.warn("Parse extra_json failed: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 给动态参数 key 加前缀，避免跨来源冲突。 */
    private Map<String, String> prefixKeys(String prefix, Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        String p = StringUtils.hasText(prefix) ? prefix : "";
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (!StringUtils.hasText(entry.getKey())) {
                continue;
            }
            out.put(p + entry.getKey(), entry.getValue());
        }
        return out;
    }

    /** 无数据时输出空的农事 CSV（仅含最小列）。 */
    private void checkExportRowLimit(int rows) {
        if (rows <= MAX_EXPORT_ROWS) {
            return;
        }
        throw new ExportServiceException(
                ErrorCode.VALIDATION_ERROR.getCode(),
                "导出数据量过大，请缩小筛选范围后重试（当前上限 " + MAX_EXPORT_ROWS + " 行）"
        );
    }

    private Object writeEmptyFarmCsv(HttpServletResponse response) {
        writeCsv(response, "farm-records.csv", new String[]{"recordId"}, Collections.emptyList());
        return null;
    }

    /** 格式化日期时间。 */
    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    /** 格式化日期。 */
    private String formatDate(LocalDate value) {
        return value == null ? "" : value.format(DATE_FORMATTER);
    }

    /** 根据区域关键词检索匹配田块 ID 集合。 */
    private Set<Long> resolveFieldIdsByRegionKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptySet();
        }
        String text = keyword.trim();
        LambdaQueryWrapper<Field> fieldQw = new LambdaQueryWrapper<>();
        fieldQw.and(w -> w.like(Field::getProvince, text)
                .or()
                .like(Field::getCity, text)
                .or()
                .like(Field::getDistrict, text)
                .or()
                .like(Field::getTownship, text)
                .or()
                .like(Field::getFormattedAddress, text)
                .or()
                .like(Field::getLocationDesc, text));
        List<Field> rows = fieldService.list(fieldQw);
        return rows.stream().map(Field::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** 导出列定义。 */
    private static class Column {
        private final String code;
        private final String header;

        private Column(String code, String header) {
            this.code = code;
            this.header = header;
        }

        private String getHeader() {
            return header;
        }
    }
}

