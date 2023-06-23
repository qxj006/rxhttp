package com.rxhttp.compiler.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Modifier
import com.rxhttp.compiler.RxHttp
import com.rxhttp.compiler.rxHttpPackage
import com.rxhttp.compiler.rxhttpClass
import com.rxhttp.compiler.rxhttpKClass
import com.squareup.javapoet.TypeName
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmOverloads
import com.squareup.kotlinpoet.jvm.jvmStatic
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.IOException

class RxHttpGenerator(
    private val logger: KSPLogger,
    private val ksFiles: Collection<KSFile>
) {

    var paramsVisitor: ParamsVisitor? = null
    var domainVisitor: DomainVisitor? = null
    var converterVisitor: ConverterVisitor? = null
    var okClientVisitor: OkClientVisitor? = null
    var defaultDomainVisitor: DefaultDomainVisitor? = null

    //生成RxHttp类
    @KspExperimental
    @Throws(IOException::class)
    fun generateCode(codeGenerator: CodeGenerator) {

        val paramClassName = ClassName("rxhttp.wrapper.param", "Param")
        val typeVariableP = TypeVariableName("P", paramClassName.parameterizedBy("P"))      //泛型P
        val typeVariableR = TypeVariableName("R", rxhttpKClass.parameterizedBy("P", "R")) //泛型R

        val okHttpClient = ClassName("okhttp3", "OkHttpClient")
        val requestName = okHttpClient.peerClass("Request")
        val headerName = okHttpClient.peerClass("Headers")
        val headerBuilderName = okHttpClient.peerClass("Headers.Builder")
        val cacheControlName = okHttpClient.peerClass("CacheControl")
        val callName = okHttpClient.peerClass("Call")

        val timeUnitName = ClassName("java.util.concurrent", "TimeUnit")

        val rxHttpPluginsName = ClassName("rxhttp", "RxHttpPlugins")
        val converterName = ClassName("rxhttp.wrapper.callback", "IConverter")
        val logUtilName = ClassName("rxhttp.wrapper.utils", "LogUtil")
        val logInterceptor = ClassName("rxhttp.wrapper.intercept", "LogInterceptor")
        val cacheInterceptorName = logInterceptor.peerClass("CacheInterceptor")
        val rangeInterceptor = logInterceptor.peerClass("RangeInterceptor")
        val cacheModeName = ClassName("rxhttp.wrapper.cache", "CacheMode")
        val cacheStrategyName = cacheModeName.peerClass("CacheStrategy")
        val downloadOffSizeName = ClassName("rxhttp.wrapper.entity", "DownloadOffSize")
        val outputStreamFactory = converterName.peerClass("OutputStreamFactory")

        val t = TypeVariableName("T")
        val className = Class::class.asClassName()
        val superT = WildcardTypeName.consumerOf(t)
        val classSuperTName = className.parameterizedBy(superT)

        val wildcard = TypeVariableName("*")
        val listName = LIST.parameterizedBy("*")
        val mapName = MAP.parameterizedBy(STRING, wildcard)
        val mapStringName = MAP.parameterizedBy(STRING, STRING)

        val methodList = ArrayList<FunSpec>() //方法集合

        //添加构造方法
        val constructorFun = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PROTECTED)
            .addParameter("param", typeVariableP)
            .build()

        val propertySpecs = mutableListOf<PropertySpec>()

        PropertySpec.builder("connectTimeoutMillis", LONG, KModifier.PRIVATE)
            .initializer("0L")
            .mutable(true)
            .build()
            .let { propertySpecs.add(it) }

        PropertySpec.builder("readTimeoutMillis", LONG, KModifier.PRIVATE)
            .initializer("0L")
            .mutable(true)
            .build()
            .let { propertySpecs.add(it) }

        PropertySpec.builder("writeTimeoutMillis", LONG, KModifier.PRIVATE)
            .initializer("0L")
            .mutable(true)
            .build()
            .let { propertySpecs.add(it) }

        PropertySpec.builder("converter", converterName, KModifier.PRIVATE)
            .mutable(true)
            .initializer("%T.getConverter()", rxHttpPluginsName)
            .build()
            .let { propertySpecs.add(it) }

        PropertySpec.builder("okClient", okHttpClient, KModifier.PRIVATE)
            .mutable(true)
            .initializer("%T.getOkHttpClient()", rxHttpPluginsName)
            .build()
            .let { propertySpecs.add(it) }

        PropertySpec.builder("param", typeVariableP)
            .initializer("param")
            .build()
            .let { propertySpecs.add(it) }

        PropertySpec.builder("request", requestName.copy(true))
            .mutable(true)
            .initializer("null")
            .build()
            .let { propertySpecs.add(it) }

        val getUrlFun = FunSpec.getterBuilder()
            .addStatement("addDefaultDomainIfAbsent()")
            .addStatement("return param.getUrl()")
            .build()

        PropertySpec.builder("url", STRING)
            .addAnnotation(getJvmName("getUrl"))
            .getter(getUrlFun)
            .build()
            .let { propertySpecs.add(it) }

        val simpleUrlFun = FunSpec.getterBuilder()
            .addStatement("return param.getSimpleUrl()")
            .build()

        PropertySpec.builder("simpleUrl", STRING)
            .addAnnotation(getJvmName("getSimpleUrl"))
            .getter(simpleUrlFun)
            .build()
            .let { propertySpecs.add(it) }

        val headersFun = FunSpec.getterBuilder()
            .addStatement("return param.getHeaders()")
            .build()

        PropertySpec.builder("headers", headerName)
            .addAnnotation(getJvmName("getHeaders"))
            .getter(headersFun)
            .build()
            .let { propertySpecs.add(it) }

        val headersBuilderFun = FunSpec.getterBuilder()
            .addStatement("return param.getHeadersBuilder()")
            .build()

        PropertySpec.builder("headersBuilder", headerBuilderName)
            .addAnnotation(getJvmName("getHeadersBuilder"))
            .getter(headersBuilderFun)
            .build()
            .let { propertySpecs.add(it) }

        val cacheStrategyFun = FunSpec.getterBuilder()
            .addStatement("return param.getCacheStrategy()")
            .build()

        PropertySpec.builder("cacheStrategy", cacheStrategyName)
            .addAnnotation(getJvmName("getCacheStrategy"))
            .getter(cacheStrategyFun)
            .build()
            .let { propertySpecs.add(it) }

        val okClientFun = FunSpec.getterBuilder()
            .addCode(
                """
                if (_okHttpClient != null) return _okHttpClient!!
                val okClient = this.okClient
                var builder: OkHttpClient.Builder? = null
                
                if (%T.isDebug()) {
                    val b = builder ?: okClient.newBuilder().also { builder = it }
                    b.addInterceptor(%T(okClient))
                }
                
                if (connectTimeoutMillis != 0L) {
                    val b = builder ?: okClient.newBuilder().also { builder = it }
                    b.connectTimeout(connectTimeoutMillis, %T.MILLISECONDS)
                }
                
                if (readTimeoutMillis != 0L) {
                    val b = builder ?: okClient.newBuilder().also { builder = it }
                    b.readTimeout(readTimeoutMillis, %T.MILLISECONDS)
                }

                if (writeTimeoutMillis != 0L) {
                    val b = builder ?: okClient.newBuilder().also { builder = it }
                    b.writeTimeout(writeTimeoutMillis, %T.MILLISECONDS)
                }
                
                if (param.getCacheMode() != CacheMode.ONLY_NETWORK) {
                    val b = builder ?: okClient.newBuilder().also { builder = it }
                    b.addInterceptor(%T(cacheStrategy))
                }

                _okHttpClient = builder?.build() ?: okClient
                return _okHttpClient!!
                """.trimIndent(),
                logUtilName,
                logInterceptor,
                timeUnitName,
                timeUnitName,
                timeUnitName,
                cacheInterceptorName
            )
            .build()


        PropertySpec.builder("_okHttpClient", okHttpClient.copy(true), KModifier.PRIVATE)
            .mutable(true)
            .initializer("null")
            .build()
            .let { propertySpecs.add(it) }

        PropertySpec.builder("okHttpClient", okHttpClient)
            .addAnnotation(getJvmName("getOkHttpClient"))
            .getter(okClientFun)
            .build()
            .let { propertySpecs.add(it) }

        FunSpec.builder("connectTimeout")
            .addParameter("connectTimeout", LONG)
            .addStatement("connectTimeoutMillis = connectTimeout")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("readTimeout")
            .addParameter("readTimeout", LONG)
            .addStatement("readTimeoutMillis = readTimeout")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("writeTimeout")
            .addParameter("writeTimeout", LONG)
            .addStatement("writeTimeoutMillis = writeTimeout")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        val methodMap = LinkedHashMap<String, String>()
        methodMap["get"] = "RxHttpNoBodyParam"
        methodMap["head"] = "RxHttpNoBodyParam"
        methodMap["postBody"] = "RxHttpBodyParam"
        methodMap["putBody"] = "RxHttpBodyParam"
        methodMap["patchBody"] = "RxHttpBodyParam"
        methodMap["deleteBody"] = "RxHttpBodyParam"
        methodMap["postForm"] = "RxHttpFormParam"
        methodMap["putForm"] = "RxHttpFormParam"
        methodMap["patchForm"] = "RxHttpFormParam"
        methodMap["deleteForm"] = "RxHttpFormParam"
        methodMap["postJson"] = "RxHttpJsonParam"
        methodMap["putJson"] = "RxHttpJsonParam"
        methodMap["patchJson"] = "RxHttpJsonParam"
        methodMap["deleteJson"] = "RxHttpJsonParam"
        methodMap["postJsonArray"] = "RxHttpJsonArrayParam"
        methodMap["putJsonArray"] = "RxHttpJsonArrayParam"
        methodMap["patchJsonArray"] = "RxHttpJsonArrayParam"
        methodMap["deleteJsonArray"] = "RxHttpJsonArrayParam"

        val codeBlock =
            """
                For example:

                ```
                RxHttp.get("/service/%L/...", 1)
                    .addQuery("size", 20)
                    ...
                ```
                 url = /service/1/...?size=20
            """.trimIndent()

        val companionBuilder = TypeSpec.companionObjectBuilder()

        methodMap.forEach { (key, value) ->
            val methodBuilder = FunSpec.builder(key)
            if (key == "get") {
                methodBuilder.addKdoc(codeBlock, "%d")
            }
            methodBuilder.jvmStatic()
                .addParameter("url", STRING)
                .addParameter("formatArgs", ANY, true, KModifier.VARARG)
                .addStatement(
                    "return $value(%T.${key}(format(url, *formatArgs)))", paramClassName,
                )
                .returns(rxhttpKClass.peerClass(value))
                .build()
                .let { companionBuilder.addFunction(it) }
        }

        paramsVisitor?.apply {
            companionBuilder.addFunctions(getFunList(codeGenerator))
        }

        FunSpec.builder("format")
            .addKdoc("Returns a formatted string using the specified format string and arguments.")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("url", STRING)
            .addParameter("formatArgs", ANY, true, KModifier.VARARG)
            .addStatement("return if(formatArgs.isNullOrEmpty()) url else String.format(url, *formatArgs)")
            .returns(STRING)
            .build()
            .let { companionBuilder.addFunction(it) }

        FunSpec.builder("setUrl")
            .addParameter("url", STRING)
            .addStatement("param.setUrl(url)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addPath")
            .addKdoc(
                """
                For example:

                ```
                RxHttp.get("/service/{page}/...")
                    .addPath("page", 1)
                    ...
                ```
                url = /service/1/...
                """.trimIndent()
            )
            .addParameter("name", STRING)
            .addParameter("value", ANY)
            .addStatement("param.addPath(name, value)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addEncodedPath")
            .addParameter("name", STRING)
            .addParameter("value", ANY)
            .addStatement("param.addEncodedPath(name, value)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addQuery")
            .addParameter("key", STRING)
            .addStatement("param.addQuery(key, null)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addEncodedQuery")
            .addParameter("key", STRING)
            .addStatement("param.addEncodedQuery(key, null)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addQuery")
            .addParameter("key", STRING)
            .addParameter("value", ANY, true)
            .addStatement("param.addQuery(key, value)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addEncodedQuery")
            .addParameter("key", STRING)
            .addParameter("value", ANY, true)
            .addStatement("param.addEncodedQuery(key, value)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addAllQuery")
            .addParameter("key", STRING)
            .addParameter("list", listName)
            .addStatement("param.addAllQuery(key, list)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addAllEncodedQuery")
            .addParameter("key", STRING)
            .addParameter("list", listName)
            .addStatement("param.addAllEncodedQuery(key, list)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addAllQuery")
            .addParameter("map", mapName)
            .addStatement("param.addAllQuery(map)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addAllEncodedQuery")
            .addParameter("map", mapName)
            .addStatement("param.addAllEncodedQuery(map)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        val isAddParam = ParameterSpec.builder("isAdd", BOOLEAN)
            .defaultValue("true")
            .build()
        FunSpec.builder("addHeader")
            .jvmOverloads()
            .addParameter("line", STRING)
            .addParameter(isAddParam)
            .addCode(
                """
                if (isAdd) param.addHeader(line)
                return self()
                """.trimIndent()
            )
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addNonAsciiHeader")
            .addKdoc("Add a header with the specified name and value. Does validation of header names, allowing non-ASCII values.")
            .addParameter("key", STRING)
            .addParameter("value", STRING)
            .addStatement("param.addNonAsciiHeader(key, value)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setNonAsciiHeader")
            .addKdoc("Set a header with the specified name and value. Does validation of header names, allowing non-ASCII values.")
            .addParameter("key", STRING)
            .addParameter("value", STRING)
            .addStatement("param.setNonAsciiHeader(key, value)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addHeader")
            .jvmOverloads()
            .addParameter("key", STRING)
            .addParameter("value", STRING)
            .addParameter(isAddParam)
            .addCode(
                """
                if (isAdd) param.addHeader(key, value)
                return self()
                """.trimIndent()
            )
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addAllHeader")
            .addParameter("headers", mapStringName)
            .addStatement("param.addAllHeader(headers)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("addAllHeader")
            .addParameter("headers", headerName)
            .addStatement("param.addAllHeader(headers)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setHeader")
            .addParameter("key", STRING)
            .addParameter("value", STRING)
            .addStatement("param.setHeader(key, value)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setAllHeader")
            .addParameter("headers", mapStringName)
            .addStatement("param.setAllHeader(headers)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        val endIndex = ParameterSpec.builder("endIndex", LONG)
            .defaultValue("-1L")
            .build()

        FunSpec.builder("setRangeHeader")
            .jvmOverloads()
            .addParameter("startIndex", LONG)
            .addParameter(endIndex)
            .addStatement("return setRangeHeader(startIndex, endIndex, false)")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setRangeHeader")
            .addParameter("startIndex", LONG)
            .addParameter("connectLastProgress", BOOLEAN)
            .addStatement("return setRangeHeader(startIndex, -1, connectLastProgress)")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setRangeHeader")
            .addKdoc(
                """
                设置断点下载开始/结束位置
                @param startIndex 断点下载开始位置
                @param endIndex 断点下载结束位置，默认为-1，即默认结束位置为文件末尾
                @param connectLastProgress 是否衔接上次的下载进度，该参数仅在带进度断点下载时生效
                """.trimIndent()
            )
            .addParameter("startIndex", LONG)
            .addParameter("endIndex", LONG)
            .addParameter("connectLastProgress", BOOLEAN)
            .addCode(
                """
                param.setRangeHeader(startIndex, endIndex)
                if (connectLastProgress && startIndex >= 0)
                    param.tag(DownloadOffSize::class.java, %T(startIndex))
                return self()
                """.trimIndent(), downloadOffSizeName
            )
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("removeAllHeader")
            .addParameter("key", STRING)
            .addStatement("param.removeAllHeader(key)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setHeadersBuilder")
            .addParameter("builder", headerBuilderName)
            .addStatement("param.setHeadersBuilder(builder)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setAssemblyEnabled")
            .addKdoc(
                """
                设置单个接口是否需要添加公共参数,
                即是否回调通过{@link RxHttpPlugins#setOnParamAssembly(Function)}方法设置的接口,默认为true
                """.trimIndent()
            )
            .addParameter("enabled", BOOLEAN)
            .addStatement("param.setAssemblyEnabled(enabled)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setDecoderEnabled")
            .addKdoc(
                """
                设置单个接口是否需要对Http返回的数据进行解码/解密,
                即是否回调通过{@link RxHttpPlugins#setResultDecoder(Function)}方法设置的接口,默认为true
                """.trimIndent()
            )
            .addParameter("enabled", BOOLEAN)
            .addStatement(
                "param.addHeader(%T.DATA_DECRYPT, enabled.toString())", paramClassName
            )
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("isAssemblyEnabled")
            .addStatement("return param.isAssemblyEnabled()")
            .returns(BOOLEAN)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("getHeader")
            .addParameter("key", STRING)
            .addStatement("return param.getHeader(key)")
            .returns(STRING)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("tag")
            .addParameter("tag", ANY)
            .addStatement("param.tag(tag)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("tag")
            .addModifiers(KModifier.OVERRIDE)
            .addTypeVariable(t)
            .addParameter("type", classSuperTName)
            .addParameter("tag", t)
            .addCode(
                """
            param.tag(type, tag)
            if (type === %T::class.java) {
                okClient = okClient.newBuilder()
                    .addInterceptor(%T())
                    .build()
            }
            return self()
            """.trimIndent(), outputStreamFactory, rangeInterceptor
            )
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("cacheControl")
            .addParameter("cacheControl", cacheControlName)
            .addStatement("param.cacheControl(cacheControl)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setCacheKey")
            .addParameter("cacheKey", STRING)
            .addStatement("param.setCacheKey(cacheKey)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setCacheValidTime")
            .addParameter("cacheValidTime", LONG)
            .addStatement("param.setCacheValidTime(cacheValidTime)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setCacheMode")
            .addParameter("cacheMode", cacheModeName)
            .addStatement("param.setCacheMode(cacheMode)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("newCall")
            .addModifiers(KModifier.OVERRIDE)
            .addCode(
                """
                val request = buildRequest()
                return okHttpClient.newCall(request)
                """.trimIndent()
            )
            .returns(callName)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("buildRequest")
            .addCode(
                """
                if (request == null) {
                    doOnStart()
                    request = param.buildRequest()
                }
                return request!!
                """.trimIndent()
            )
            .returns(requestName)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("doOnStart")
            .addModifiers(KModifier.PRIVATE)
            .addKdoc("请求开始前内部调用，用于添加默认域名等操作\n")
            .addStatement("setConverterToParam(converter)")
            .addStatement("addDefaultDomainIfAbsent()")
            .build()
            .let { methodList.add(it) }

        converterVisitor?.apply {
            methodList.addAll(getFunList())
        }

        FunSpec.builder("setConverter")
            .addParameter("converter", converterName)
            .addCode(
                """
                this.converter = converter
                return self()
                """.trimIndent()
            )
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setConverterToParam")
            .addKdoc("给Param设置转换器，此方法会在请求发起前，被RxHttp内部调用\n")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("converter", converterName)
            .addStatement("param.tag(IConverter::class.java, converter)")
            .addStatement("return self()")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        FunSpec.builder("setOkClient")
            .addParameter("okClient", okHttpClient)
            .addCode(
                """
                this.okClient = okClient
                return self()
                """.trimIndent()
            )
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        okClientVisitor?.apply {
            methodList.addAll(getFunList())
        }

        defaultDomainVisitor?.apply {
            methodList.add(getFun())
        }

        domainVisitor?.apply {
            methodList.addAll(getFunList())
        }

        FunSpec.builder("setDomainIfAbsent")
            .addParameter("domain", STRING)
            .addCode(
                """
                val newUrl = addDomainIfAbsent(param.getSimpleUrl(), domain)
                param.setUrl(newUrl)
                return self()
                """.trimIndent()
            )
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        //对url添加域名方法
        FunSpec.builder("addDomainIfAbsent")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("url", STRING)
            .addParameter("domain", STRING)
            .addCode(
                """
                return 
                    if (url.startsWith("http")) {
                        url
                    } else if (url.startsWith("/")) {
                        if (domain.endsWith("/"))
                            domain + url.substring(1)
                        else
                            domain + url
                    } else if (domain.endsWith("/")) {
                        domain + url
                    } else {
                        domain + "/" + url
                    }
                """.trimIndent()
            )
            .returns(STRING)
            .build()
            .let { methodList.add(it) }

        val suppressAnnotation = AnnotationSpec.builder(Suppress::class)
            .addMember("%S", "UNCHECKED_CAST")
            .build()

        FunSpec.builder("self")
            .addModifiers(KModifier.PRIVATE)
            .addAnnotation(suppressAnnotation)
            .addStatement("return this as R")
            .returns(typeVariableR)
            .build()
            .let { methodList.add(it) }

        val baseRxHttpName = rxhttpKClass.peerClass("BaseRxHttp")

        val rxHttpBuilder = TypeSpec.classBuilder(RxHttp)
            .primaryConstructor(constructorFun)
            .addType(companionBuilder.build())
            .addKdoc(
                """
                Github
                https://github.com/liujingxing/rxhttp
                https://github.com/liujingxing/rxlife
                https://github.com/liujingxing/rxhttp/wiki/FAQ
                https://github.com/liujingxing/rxhttp/wiki/更新日志
            """.trimIndent()
            )
            .addModifiers(KModifier.OPEN)
            .addTypeVariable(typeVariableP)
            .addTypeVariable(typeVariableR)
            .superclass(baseRxHttpName)
            .addProperties(propertySpecs)
            .addFunctions(methodList)
            .build()

        val dependencies = Dependencies(true, *ksFiles.toTypedArray())
        FileSpec.builder(rxHttpPackage, RxHttp)
            .addType(rxHttpBuilder)
            .indent("    ")
            .build()
            .writeTo(codeGenerator, dependencies)
    }
}