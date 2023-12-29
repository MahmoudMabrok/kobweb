package com.varabyte.kobweb

import com.varabyte.kobweb.ksp.KOBWEB_APP_METADATA_BACKEND
import com.varabyte.kobweb.ksp.KOBWEB_APP_METADATA_FRONTEND
import com.varabyte.kobweb.ksp.KOBWEB_METADATA_BACKEND
import com.varabyte.kobweb.ksp.KOBWEB_METADATA_FRONTEND

enum class ProcessorMode {
    APP, LIBRARY, WORKER
}

val ProcessorMode.frontendFile
    get() = when (this) {
        ProcessorMode.APP -> KOBWEB_APP_METADATA_FRONTEND
        ProcessorMode.LIBRARY -> KOBWEB_METADATA_FRONTEND
        ProcessorMode.WORKER -> "" // DO NOT SUBMIT
    }

val ProcessorMode.backendFile
    get() = when (this) {
        ProcessorMode.APP -> KOBWEB_APP_METADATA_BACKEND
        ProcessorMode.LIBRARY -> KOBWEB_METADATA_BACKEND
        ProcessorMode.WORKER -> "" // DO NOT SUBMIT
    }
