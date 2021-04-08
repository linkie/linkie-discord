/*
 * Copyright (c) 2019, 2020, 2021 shedaniel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.shedaniel.linkie.discord

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsEntryType
import me.shedaniel.linkie.utils.ClassResultList
import me.shedaniel.linkie.utils.FieldResultList
import me.shedaniel.linkie.utils.MappingsQuery
import me.shedaniel.linkie.utils.MatchAccuracy
import me.shedaniel.linkie.utils.MethodResultList
import me.shedaniel.linkie.utils.QueryContext
import me.shedaniel.linkie.utils.ResultHolder

object MappingsQueryUtils {
    suspend fun query(mappings: MappingsContainer, searchTerm: String, vararg types: MappingsEntryType): MutableList<ResultHolder<*>> {
        require(types.isNotEmpty())
        val context = QueryContext(
            provider = { mappings },
            searchKey = searchTerm,
        )
        val result: MutableList<ResultHolder<*>> = mutableListOf()
        var classes: ClassResultList? = null
        var methods: MethodResultList? = null
        var fields: FieldResultList? = null
        runBlocking {
            if (MappingsEntryType.CLASS in types) {
                launch {
                    try {
                        classes = MappingsQuery.queryClasses(context).value
                    } catch (ignore: NullPointerException) {
                    }
                }
            }
            if (MappingsEntryType.METHOD in types) {
                launch {
                    try {
                        methods = MappingsQuery.queryMethods(context).value
                    } catch (ignore: NullPointerException) {
                    }
                }
            }
            if (MappingsEntryType.FIELD in types) {
                launch {
                    try {
                        fields = MappingsQuery.queryFields(context).value
                    } catch (ignore: NullPointerException) {
                    }
                }
            }
        }
        classes?.also(result::addAll)
        methods?.also(result::addAll)
        fields?.also(result::addAll)
        result.sortByDescending { it.score }

        if (result.isEmpty()) {
            runBlocking {
                if (MappingsEntryType.CLASS in types) {
                    launch {
                        try {
                            classes = MappingsQuery.queryClasses(context.copy(accuracy = MatchAccuracy.Fuzzy)).value
                        } catch (e: NullPointerException) {

                        }
                    }
                }
                if (MappingsEntryType.METHOD in types) {
                    launch {
                        try {
                            methods = MappingsQuery.queryMethods(context.copy(accuracy = MatchAccuracy.Fuzzy)).value
                        } catch (e: NullPointerException) {

                        }
                    }
                }
                if (MappingsEntryType.FIELD in types) {
                    launch {
                        try {
                            fields = MappingsQuery.queryFields(context.copy(accuracy = MatchAccuracy.Fuzzy)).value
                        } catch (e: NullPointerException) {

                        }
                    }
                }
            }
            classes?.also(result::addAll)
            methods?.also(result::addAll)
            fields?.also(result::addAll)
            result.sortByDescending { it.score }

            if (result.isEmpty()) {
                if (types.size != 1) {
                    MappingsQuery.errorNoResultsFound(null, searchTerm)
                } else {
                    MappingsQuery.errorNoResultsFound(types.first(), searchTerm)
                }
            }
        }

        return result
    }
}
