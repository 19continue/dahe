param(
    [string]$SourceDb = "dahe_v2",
    [string]$DrillDb = "dahe_v2_drill_20260222",
    [string]$MySqlHost = "localhost",
    [int]$MySqlPort = 3306,
    [string]$MySqlUser = "root",
    [string]$MySqlPassword = "123456",
    [string]$DumpFile = "",
    [string]$ReportFile = "",
    [switch]$KeepDump
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-CheckedCmd {
    param([Parameter(Mandatory = $true)][string]$CommandText)
    Write-Host ">> $CommandText"
    cmd /c $CommandText
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $CommandText"
    }
}

function Invoke-MySqlQuery {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$TargetDb = ""
    )
    $args = @(
        "-h$MySqlHost",
        "-P$MySqlPort",
        "-u$MySqlUser",
        "-p$MySqlPassword",
        "--default-character-set=utf8mb4",
        "-N",
        "-B"
    )
    if ($TargetDb) {
        $args += $TargetDb
    }
    $args += @("-e", $Sql)
    $out = & mysql @args
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed: $Sql"
    }
    return (($out -join "`n").Trim())
}

function Convert-LineToMap {
    param(
        [Parameter(Mandatory = $true)][string[]]$Keys,
        [Parameter(Mandatory = $true)][string]$Line
    )
    $parts = @()
    if (-not [string]::IsNullOrWhiteSpace($Line)) {
        $parts = $Line -split "`t", -1
    }
    $map = [ordered]@{}
    for ($i = 0; $i -lt $Keys.Count; $i++) {
        $value = ""
        if ($i -lt $parts.Count) {
            $value = $parts[$i]
        }
        $map[$Keys[$i]] = $value
    }
    return $map
}

function New-MarkdownTable {
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary]$Map)
    $lines = @(
        "| Item | Value |",
        "| --- | --- |"
    )
    foreach ($entry in $Map.GetEnumerator()) {
        $lines += "| $($entry.Key) | $($entry.Value) |"
    }
    return ($lines -join "`r`n")
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$schemaFiles = @(
    "src/main/resources/db/schema-auth.sql",
    "src/main/resources/db/schema-field.sql",
    "src/main/resources/db/schema-crop-hierarchy.sql",
    "src/main/resources/db/schema-farm.sql",
    "src/main/resources/db/schema-seed.sql",
    "src/main/resources/db/schema-dynamic.sql",
    "src/main/resources/db/schema-assets.sql",
    "src/main/resources/db/schema-company.sql",
    "src/main/resources/db/schema-export.sql",
    "src/main/resources/db/schema-amap.sql",
    "src/main/resources/db/schema-oplog.sql"
)
$copyTables = @(
    "dynamic_form_config",
    "field",
    "farm_process_template",
    "seed_batch",
    "user",
    "user_notice",
    "media_asset",
    "operation_log"
)

if (-not $DumpFile) {
    $DumpFile = Join-Path $env:TEMP "$($SourceDb)_drill_backup_$(Get-Date -Format 'yyyyMMdd_HHmmss').sql"
}
if (-not $ReportFile) {
    $ReportFile = Join-Path $repoRoot "db_migration_drill_report_$(Get-Date -Format 'yyyyMMdd_HHmmss').md"
}

$tmpSqlDir = Join-Path $env:TEMP "dahe_drill_sql_$(Get-Date -Format 'yyyyMMdd_HHmmss_fff')"
New-Item -ItemType Directory -Path $tmpSqlDir -Force | Out-Null

try {
    $sourceExists = Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$SourceDb';"
    if ($sourceExists -ne "1") {
        throw "Source database '$SourceDb' not found."
    }

    $sourceSizeMb = Invoke-MySqlQuery @"
SELECT ROUND((SUM(data_length)+SUM(index_length))/1024/1024,2)
FROM information_schema.tables
WHERE table_schema='$SourceDb';
"@

    Invoke-MySqlQuery "DROP DATABASE IF EXISTS $DrillDb; CREATE DATABASE $DrillDb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

    # Use schema-only dump to avoid client parsing issues on very long escaped text rows.
    Invoke-CheckedCmd "mysqldump -h$MySqlHost -P$MySqlPort -u$MySqlUser -p$MySqlPassword --default-character-set=utf8mb4 --no-data --set-gtid-purged=OFF $SourceDb > `"$DumpFile`""
    Invoke-CheckedCmd "mysql --default-character-set=utf8mb4 -h$MySqlHost -P$MySqlPort -u$MySqlUser -p$MySqlPassword $DrillDb < `"$DumpFile`""

    $bt = [char]96
    $quotedSourceDb = "$bt$SourceDb$bt"
    $quotedDrillDb = "$bt$DrillDb$bt"
    Invoke-MySqlQuery "SET FOREIGN_KEY_CHECKS=0;"
    foreach ($table in $copyTables) {
        $exists = Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$SourceDb' AND table_name='$table';"
        if ($exists -ne "1") {
            continue
        }
        $quotedTable = "$bt$table$bt"
        $copySql = "DELETE FROM $quotedDrillDb.$quotedTable; INSERT INTO $quotedDrillDb.$quotedTable SELECT * FROM $quotedSourceDb.$quotedTable;"
        Invoke-MySqlQuery $copySql
    }
    Invoke-MySqlQuery "SET FOREIGN_KEY_CHECKS=1;"

    foreach ($relativePath in $schemaFiles) {
        $sourcePath = Join-Path $repoRoot $relativePath
        if (-not (Test-Path $sourcePath)) {
            throw "Schema file missing: $sourcePath"
        }
        $sql = Get-Content -Path $sourcePath -Raw -Encoding UTF8
        $patchedSql = [regex]::Replace($sql, "(?im)^USE\s+`?dahe_v2`?;\s*", "USE $DrillDb;`r`n")
        $patchedPath = Join-Path $tmpSqlDir ([IO.Path]::GetFileName($sourcePath))
        Set-Content -Path $patchedPath -Value $patchedSql -Encoding UTF8
        Invoke-CheckedCmd "mysql --default-character-set=utf8mb4 -h$MySqlHost -P$MySqlPort -u$MySqlUser -p$MySqlPassword $DrillDb < `"$patchedPath`""
    }

    $columnKeys = @(
        "field_enabled",
        "seed_batch_enabled",
        "farm_process_template_enabled",
        "media_asset_folder_path",
        "media_asset_source_type",
        "media_asset_recycle_flag",
        "media_asset_recycled_at",
        "media_asset_recycled_by_user_id",
        "user_user_type",
        "user_enabled",
        "user_avatar_url",
        "user_wx_avatar_url",
        "user_avatar_source",
        "user_notice_table",
        "operation_log_target_module",
        "operation_log_undo_payload_json",
        "operation_log_undo_status"
    )
    $columnLine = Invoke-MySqlQuery @"
SELECT
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='field' AND column_name='enabled'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='seed_batch' AND column_name='enabled'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='farm_process_template' AND column_name='enabled'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='media_asset' AND column_name='folder_path'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='media_asset' AND column_name='source_type'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='media_asset' AND column_name='recycle_flag'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='media_asset' AND column_name='recycled_at'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='media_asset' AND column_name='recycled_by_user_id'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='user' AND column_name='user_type'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='user' AND column_name='enabled'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='user' AND column_name='avatar_url'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='user' AND column_name='wx_avatar_url'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='user' AND column_name='avatar_source'),
  (SELECT COUNT(*) FROM information_schema.tables  WHERE table_schema='$DrillDb' AND table_name='user_notice'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='operation_log' AND column_name='target_module'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='operation_log' AND column_name='undo_payload_json'),
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DrillDb' AND table_name='operation_log' AND column_name='undo_status');
"@
    $columnMap = Convert-LineToMap -Keys $columnKeys -Line $columnLine

    $indexKeys = @(
        "idx_field_enabled",
        "idx_seed_batch_enabled",
        "idx_template_enabled",
        "idx_media_folder",
        "idx_media_source",
        "idx_media_recycle",
        "idx_op_log_target",
        "idx_op_log_undo"
    )
    $indexLine = Invoke-MySqlQuery @"
SELECT
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$DrillDb' AND table_name='field' AND index_name='idx_field_enabled'),
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$DrillDb' AND table_name='seed_batch' AND index_name='idx_seed_batch_enabled'),
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$DrillDb' AND table_name='farm_process_template' AND index_name='idx_template_enabled'),
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$DrillDb' AND table_name='media_asset' AND index_name='idx_media_folder'),
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$DrillDb' AND table_name='media_asset' AND index_name='idx_media_source'),
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$DrillDb' AND table_name='media_asset' AND index_name='idx_media_recycle'),
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$DrillDb' AND table_name='operation_log' AND index_name='idx_op_log_target'),
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$DrillDb' AND table_name='operation_log' AND index_name='idx_op_log_undo');
"@
    $indexMap = Convert-LineToMap -Keys $indexKeys -Line $indexLine

    $rowCompareKeys = @(
        "field_src",
        "field_drill",
        "seed_batch_src",
        "seed_batch_drill",
        "farm_process_template_src",
        "farm_process_template_drill",
        "user_src",
        "user_drill",
        "media_asset_src",
        "media_asset_drill",
        "operation_log_src",
        "operation_log_drill"
    )
    $rowCompareLine = Invoke-MySqlQuery @"
SELECT
  (SELECT COUNT(*) FROM $SourceDb.field),
  (SELECT COUNT(*) FROM $DrillDb.field),
  (SELECT COUNT(*) FROM $SourceDb.seed_batch),
  (SELECT COUNT(*) FROM $DrillDb.seed_batch),
  (SELECT COUNT(*) FROM $SourceDb.farm_process_template),
  (SELECT COUNT(*) FROM $DrillDb.farm_process_template),
  (SELECT COUNT(*) FROM $SourceDb.user),
  (SELECT COUNT(*) FROM $DrillDb.user),
  (SELECT COUNT(*) FROM $SourceDb.media_asset),
  (SELECT COUNT(*) FROM $DrillDb.media_asset),
  (SELECT COUNT(*) FROM $SourceDb.operation_log),
  (SELECT COUNT(*) FROM $DrillDb.operation_log);
"@
    $rowCompareMap = Convert-LineToMap -Keys $rowCompareKeys -Line $rowCompareLine

    # Rollback rehearsal: drop drill db and restore from baseline dump.
    Invoke-MySqlQuery "DROP DATABASE IF EXISTS $DrillDb; CREATE DATABASE $DrillDb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    Invoke-CheckedCmd "mysql --default-character-set=utf8mb4 -h$MySqlHost -P$MySqlPort -u$MySqlUser -p$MySqlPassword $DrillDb < `"$DumpFile`""
    Invoke-MySqlQuery "SET FOREIGN_KEY_CHECKS=0;"
    foreach ($table in $copyTables) {
        $exists = Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$SourceDb' AND table_name='$table';"
        if ($exists -ne "1") {
            continue
        }
        $quotedTable = "$bt$table$bt"
        $copySql = "DELETE FROM $quotedDrillDb.$quotedTable; INSERT INTO $quotedDrillDb.$quotedTable SELECT * FROM $quotedSourceDb.$quotedTable;"
        Invoke-MySqlQuery $copySql
    }
    Invoke-MySqlQuery "SET FOREIGN_KEY_CHECKS=1;"

    $tableCountSource = Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$SourceDb' AND table_type='BASE TABLE';"
    $tableCountDrill = Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DrillDb' AND table_type='BASE TABLE';"

    $rowRollbackLine = Invoke-MySqlQuery @"
SELECT
  (SELECT COUNT(*) FROM $SourceDb.field),
  (SELECT COUNT(*) FROM $DrillDb.field),
  (SELECT COUNT(*) FROM $SourceDb.seed_batch),
  (SELECT COUNT(*) FROM $DrillDb.seed_batch),
  (SELECT COUNT(*) FROM $SourceDb.farm_process_template),
  (SELECT COUNT(*) FROM $DrillDb.farm_process_template),
  (SELECT COUNT(*) FROM $SourceDb.user),
  (SELECT COUNT(*) FROM $DrillDb.user),
  (SELECT COUNT(*) FROM $SourceDb.media_asset),
  (SELECT COUNT(*) FROM $DrillDb.media_asset),
  (SELECT COUNT(*) FROM $SourceDb.operation_log),
  (SELECT COUNT(*) FROM $DrillDb.operation_log);
"@
    $rowRollbackMap = Convert-LineToMap -Keys $rowCompareKeys -Line $rowRollbackLine

    $summaryMap = [ordered]@{
        run_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        source_db = $SourceDb
        drill_db = $DrillDb
        source_size_mb = $sourceSizeMb
        source_table_count = $tableCountSource
        drill_table_count_after_rollback = $tableCountDrill
        dump_file = $DumpFile
    }

    $report = @()
    $report += "# DB Migration Drill Report"
    $report += ""
    $report += "## Summary"
    $report += (New-MarkdownTable -Map $summaryMap)
    $report += ""
    $report += "## Column Checks (After Migration On Drill DB)"
    $report += (New-MarkdownTable -Map $columnMap)
    $report += ""
    $report += "## Index Checks (After Migration On Drill DB)"
    $report += (New-MarkdownTable -Map $indexMap)
    $report += ""
    $report += "## Row Count Compare (After Migration On Drill DB)"
    $report += (New-MarkdownTable -Map $rowCompareMap)
    $report += ""
    $report += "## Row Count Compare (After Rollback Restore)"
    $report += (New-MarkdownTable -Map $rowRollbackMap)
    $report += ""
    $report += "## Applied Schema Files"
    foreach ($f in $schemaFiles) {
        $report += "- $f"
    }
    $report += ""
    $report += "## Notes"
    $report += "- This drill rewrites `USE dahe_v2;` to `USE $DrillDb;` in temp files to avoid touching source DB."
    $report += "- This drill uses schema-only dump plus key-business-table copy for stable replay."
    $report += "- Rollback rehearsal is performed by dropping drill DB and restoring baseline schema+data copy."

    Set-Content -Path $ReportFile -Value ($report -join "`r`n") -Encoding UTF8
    Write-Host "Drill report generated: $ReportFile"
}
finally {
    if (Test-Path $tmpSqlDir) {
        Remove-Item -Path $tmpSqlDir -Recurse -Force
    }
    if ((-not $KeepDump) -and (Test-Path $DumpFile)) {
        Remove-Item -Path $DumpFile -Force
    }
}
