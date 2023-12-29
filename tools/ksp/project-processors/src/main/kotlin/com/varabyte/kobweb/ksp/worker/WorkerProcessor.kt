package com.varabyte.kobweb.ksp.worker

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
import com.varabyte.kobweb.ksp.util.suppresses

/**
 * A KSP processor that generates code that instantiates / wraps a Worker class related to a given `WorkerStrategy`
 * implementation.
 *
 * For example, if the user defines a class called `CalculatePiWorkerStrategy`, then this processor will generate a
 * `main.kt` file that instantiates it plus a `CalculatePiWorker` class that wraps it and acts as the main way a
 * Kobweb application would interact with this module.
 */
class WorkerProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val workerFqnOverride: String? = null,
) : SymbolProcessor {
    class WorkerStrategyInfo(
        val classDeclaration: KSClassDeclaration,
        val inputType: KSType,
        val outputType: KSType,
    )
    private var workerStrategyInfo: WorkerStrategyInfo? = null

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val allFiles = resolver.getAllFiles()

        val visitor = WorkerStrategyVisitor()

        allFiles.forEach { file ->
            file.accept(visitor, Unit)
        }

        val workerStrategy = visitor.workerStrategies.singleOrNull() ?: run {
            error(buildString {
                append("A Kobweb worker module must have exactly one class that implements `WorkerStrategy`. ")
                if (visitor.workerStrategies.isEmpty()) {
                    append("However, none were found.")
                } else {
                    append("However, the following were found: [")
                    append(visitor.workerStrategies.joinToString {
                        it.classDeclaration.qualifiedName?.asString() ?: "?"
                    })
                    append("].")
                }
            })
        }

        if (workerStrategy.classDeclaration.getConstructors()
                .none { (it.isPublic() || it.isInternal()) && (it.parameters.isEmpty() || it.parameters.all { param -> param.hasDefault }) }
        ) {
            error("A Kobweb `WorkerStrategy` implementation must have a public empty constructor. Please add one to `${workerStrategy.classDeclaration.qualifiedName?.asString()}`.")
        }

        if (!workerStrategy.classDeclaration.modifiers.contains(Modifier.INTERNAL)) {
            if (workerStrategy.classDeclaration.modifiers.contains(Modifier.PRIVATE)) {
                error("A Kobweb `WorkerStrategy` implementation cannot be private, as this prevents us from generating code that wraps it. Please make `${workerStrategy.classDeclaration.qualifiedName?.asString()}` internal.")
            }

            val publicSuppression = "PUBLIC_WORKER_STRATEGY"
            if (!workerStrategy.classDeclaration.suppresses(publicSuppression)) {
                logger.warn("It is recommended that you make your `WorkerStrategy` implementation internal to prevent Kobweb applications from using it unintentionally. Please add `internal` to `${workerStrategy.classDeclaration.qualifiedName?.asString()}`. You can annotate your class with `@Suppress(\"$publicSuppression\")` to suppress this warning.")
            }
        }

        if (!workerStrategy.inputType.declaration.isPublic()) {
            error("A Kobweb `WorkerStrategy` implementation's input type must be public so the Kobweb application can use it. Please make `${workerStrategy.inputType.declaration.qualifiedName?.asString()}` public.")
        }

        if (!workerStrategy.outputType.declaration.isPublic()) {
            error("A Kobweb `WorkerStrategy` implementation's output type must be public so the Kobweb application can use it. Please make `${workerStrategy.outputType.declaration.qualifiedName?.asString()}` public.")
        }

        workerStrategyInfo = workerStrategy

        return emptyList()
    }

    override fun finish() {
        val workerStrategyInfo = workerStrategyInfo ?: return

        val deps = Dependencies(
            aggregating = true,
            *listOfNotNull(
                workerStrategyInfo.classDeclaration.containingFile,
                workerStrategyInfo.inputType.declaration.containingFile,
                workerStrategyInfo.inputType.declaration.containingFile
            ).toTypedArray()
        )

        val workerPackage = workerStrategyInfo.classDeclaration.packageName.asString()
        val workerClassName = workerStrategyInfo.classDeclaration.simpleName.asString().removeSuffix("Strategy")

        codeGenerator.createNewFile(
            deps,
            workerPackage,
            workerClassName
        ).writer().use { writer ->
            writer.write(
                """
                    ${workerPackage.takeIf { it.isNotEmpty() }?.let { "package $it" } ?: ""}

                    import org.w3c.dom.Worker

                    class $workerClassName(val onMessage: (${workerStrategyInfo.outputType.declaration.qualifiedName!!.asString()}) -> Unit) {
                        val messageConverter = ${workerStrategyInfo.classDeclaration.qualifiedName!!.asString()}().messageConverter

                        private val worker = Worker("worker.js").apply {
                            onmessage = { e ->
                                onMessage(messageConverter.deserializeOutput(e.data as String))
                            }
                        }

                        fun postMessage(message: ${workerStrategyInfo.inputType.declaration.qualifiedName!!.asString()}) {
                            worker.postMessage(messageConverter.serializeInput(message))
                        }
                    }
                """.trimIndent()
            )
        }

        codeGenerator.createNewFile(
            deps,
            packageName = "",
            "main"
        ).writer().use { writer ->
            writer.write(
                """
                    import ${workerStrategyInfo.classDeclaration.qualifiedName!!.asString()}

                    fun main() {
                        ${workerStrategyInfo.classDeclaration.qualifiedName!!.asString()}() // As a side effect, registers onMessage handler
                    }
                """.trimIndent()
            )
        }
    }

    /**
     * Search the codebase for classes that implement `WorkerStrategy`.
     *
     * After this processor runs, the [workerStrategies] property will be populated with all the classes that implement
     * this base class, along with their input and output types.
     *
     * Ideally, a Kobweb worker module has exactly one implementation. If there are none or multiple, an error should be
     * reported to the user, but this is handled at a higher level.
     */
    private inner class WorkerStrategyVisitor : KSVisitorVoid() {
        private val _workerStrategies = mutableListOf<WorkerStrategyInfo>()
        val workerStrategies: List<WorkerStrategyInfo> = _workerStrategies

        override fun visitFile(file: KSFile, data: Unit) {
            file.declarations.forEach { it.accept(this, Unit) }
        }

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val workerStrategyBaseClass = classDeclaration
                .getAllSuperTypes()
                .filter { it.declaration.qualifiedName?.asString() == "com.varabyte.kobweb.worker.WorkerStrategy" }
                .firstOrNull()

            if (workerStrategyBaseClass != null) {
                val resolvedTypes = workerStrategyBaseClass.arguments.mapNotNull { it.type?.resolve() }

                // WorkerStrategy<I, O>
                check(resolvedTypes.size == 2) {
                    "Unexpected error parsing WorkerStrategy subclass. Expected 2 type arguments, got ${resolvedTypes.size}: [${
                        resolvedTypes.joinToString {
                            it.declaration.qualifiedName?.asString().orEmpty()
                        }
                    }]"
                }
                _workerStrategies.add(
                    WorkerStrategyInfo(
                        classDeclaration,
                        inputType = resolvedTypes[0],
                        outputType = resolvedTypes[1],
                    )
                )
            }
        }
    }
}
//    private lateinit var initMethods: List<InitApiEntry>
//    private lateinit var apiMethods: List<ApiEntry>
//    private val apiStreams = mutableListOf<ApiStreamEntry>()
//
//    // fqPkg to subdir, e.g. "api.id._as._int" to "int"
//    private lateinit var packageMappings: Map<String, String>
//
//    // We track all files we depend on so that ksp can perform smart recompilation
//    // Even though our output is aggregating so generally requires full reprocessing, this at minimum means processing
//    // will be skipped if the only change is deleted file(s) that we do not depend on.
//    private val fileDependencies = mutableListOf<KSFile>()
//
//    override fun process(resolver: Resolver): List<KSAnnotated> {
//        initMethods = resolver.getSymbolsWithAnnotation(INIT_API_FQN).map { annotatedFun ->
//            fileDependencies.add(annotatedFun.containingFile!!)
//            val name = (annotatedFun as KSFunctionDeclaration).qualifiedName!!.asString()
//            InitApiEntry(name)
//        }.toList()
//
//        val allFiles = resolver.getAllFiles()
//
//        // package mapping must be processed before api methods & streams
//        packageMappings = allFiles.flatMap { file ->
//            getPackageMappings(file, qualifiedApiPackage, PACKAGE_MAPPING_API_FQN, logger).toList()
//                .also { if (it.isNotEmpty()) fileDependencies.add(file) }
//        }.toMap()
//
//        apiMethods = resolver.getSymbolsWithAnnotation(API_FQN)
//            .filterIsInstance<KSFunctionDeclaration>() // @Api for stream properties is handled separately
//            .mapNotNull { annotatedFun ->
//                processApiFun(annotatedFun, qualifiedApiPackage, packageMappings, logger)
//                    ?.also { fileDependencies.add(annotatedFun.containingFile!!) }
//            }.toList()
//
//        val visitor = ApiVisitor()
//        allFiles.forEach { file ->
//            file.accept(visitor, Unit)
//        }
//
//        return emptyList()
//    }
//
//    private inner class ApiVisitor : KSVisitorVoid() {
//        @OptIn(KspExperimental::class)
//        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
//            val type = property.type.toString()
//            if (type != API_STREAM_SIMPLE_NAME) return
//
//            val expensiveFullType = property.type.resolve().declaration.qualifiedName?.asString()
//            if (expensiveFullType != API_STREAM_FQN) return
//
//            val propertyName = property.simpleName.asString()
//            val topLevelSuppression = "TOP_LEVEL_API_STREAM"
//            val privateSuppression = "PRIVATE_API_STREAM"
//            if (property.parent !is KSFile) {
//                if (property.getAnnotationsByType(Suppress::class).none { topLevelSuppression in it.names }) {
//                    logger.warn(
//                        "Not registering ApiStream `val $propertyName`, as only top-level component styles are supported at this time. Although fixing this is recommended, you can manually register your API Stream inside an @InitSilk block instead (`ctx.apis.register($propertyName)`). Suppress this message by adding a `@Suppress(\"$topLevelSuppression\")` annotation.",
//                        property
//                    )
//                }
//                return
//            }
//            if (!property.isPublic()) {
//                if (property.getAnnotationsByType(Suppress::class).none { privateSuppression in it.names }) {
//                    logger.warn(
//                        "Not registering ApiStream `val $propertyName`, as it is not public. Although fixing this is recommended, you can manually register your API Stream inside an @InitSilk block instead (`ctx.apis.register($propertyName)`). Suppress this message by adding a `@Suppress(\"$privateSuppression\")` annotation.",
//                        property
//                    )
//                }
//                return
//            }
//            fileDependencies.add(property.containingFile!!)
//
//            val routeOverride = property.getAnnotationsByName(API_FQN)
//                .firstNotNullOfOrNull { it.arguments.firstOrNull()?.value?.toString() }
//
//            val resolvedRoute = processRoute(
//                pkg = property.packageName.asString(),
//                slugFromFile = property.containingFile!!.nameWithoutExtension.lowercase(),
//                routeOverride = routeOverride,
//                qualifiedPackage = qualifiedApiPackage,
//                packageMappings = packageMappings,
//                supportDynamicRoute = false,
//            )
//
//            apiStreams.add(ApiStreamEntry(property.qualifiedName!!.asString(), resolvedRoute))
//        }
//
//        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
//            classDeclaration.declarations.forEach { it.accept(this, Unit) }
//        }
//
//        override fun visitFile(file: KSFile, data: Unit) {
//            file.declarations.forEach { it.accept(this, Unit) }
//        }
//    }
//
//    override fun finish() {
//        val backendData = BackendData(initMethods, apiMethods, apiStreams).also {
//            it.assertValid(throwError = { msg -> logger.error(msg) })
//        }
//
//        val (path, extension) = genFile.split('.')
//        codeGenerator.createNewFileByPath(
//            Dependencies(aggregating = true, *fileDependencies.toTypedArray()),
//            path = path,
//            extensionName = extension,
//        ).writer().use { writer ->
//            writer.write(Json.encodeToString(backendData))
//        }
//    }
//}
//
//private fun processApiFun(
//    annotatedFun: KSFunctionDeclaration,
//    qualifiedApiPackage: String,
//    packageMappings: Map<String, String>,
//    logger: KSPLogger,
//): ApiEntry? {
//    val apiAnnotation = annotatedFun.getAnnotationsByName(API_FQN).first()
//    val currPackage = annotatedFun.packageName.asString()
//    val file = annotatedFun.containingFile ?: error("Symbol does not come from a source file")
//    val routeOverride = apiAnnotation.arguments.first().value?.toString()?.takeIf { it.isNotBlank() }
//
//    return if (routeOverride?.startsWith("/") == true || currPackage.startsWith(qualifiedApiPackage)) {
//        val resolvedRoute = processRoute(
//            pkg = annotatedFun.packageName.asString(),
//            slugFromFile = file.nameWithoutExtension.lowercase(),
//            routeOverride = routeOverride,
//            qualifiedPackage = qualifiedApiPackage,
//            packageMappings = packageMappings,
//            supportDynamicRoute = false,
//        )
//        ApiEntry(annotatedFun.qualifiedName!!.asString(), resolvedRoute)
//    } else {
//        val funName = annotatedFun.simpleName.asString()
//        val annotationName = apiAnnotation.shortName.asString()
//        logger.warn(
//            "Skipped over `@$annotationName fun ${funName}`. It is defined under package `$currPackage` but must exist under `$qualifiedApiPackage`.",
//            annotatedFun
//        )
//        null
//    }
