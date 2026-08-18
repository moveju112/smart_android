package com.wemade.smartnoti

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 백업 파일 읽고 쓰기.
 *
 * 매크로는 이미 JSON으로 저장돼 있어서, 백업은 그 모양을 그대로 파일에 옮긴 것이다.
 * 다른 형식으로 바꾸지 않으니 옮기다 빠지는 것도 없다.
 */
private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

fun exportBackup(macros: List<Macro>): String = backupJson.encodeToString(macros)

/**
 * 백업 파일을 읽는다.
 * 이 앱의 JSON이 기본이고, 예전에 쓰던 MacroDroid 백업(.mdr)도 알아보고 받아 준다.
 */
fun importBackup(text: String): ImportResult {
    // 이 앱 백업은 매크로 목록이라 대괄호로 시작한다. MacroDroid 것은 중괄호다
    return if (text.trimStart().startsWith("[")) {
        ImportResult(backupJson.decodeFromString<List<Macro>>(text), emptyList(), emptyList())
    } else {
        importMdr(text)
    }
}
