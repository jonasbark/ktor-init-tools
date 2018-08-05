package io.ktor.start.swagger

import io.ktor.start.util.*

/**
 * https://swagger.io/specification/
 * https://github.com/OAI/OpenAPI-Specification/blob/master/versions/1.2.md
 * https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md
 * https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md
 * https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.1.md
 * https://blog.readme.io/an-example-filled-guide-to-swagger-3-2/
 */
data class SwaggerModel(
    val filename: String,
    val info: SwaggerInfo,
    val servers: List<Server>,
    val produces: List<String>,
    val consumes: List<String>,
    val securityDefinitions: Map<String, SecurityDefinition>,
    val paths: Map<String, PathModel>,
    val definitions: Map<String, TypeDef>
) {
    data class Server(
        val url: String,
        val description: String,
        val variables: Map<String, ServerVariable>
    ) {
        //V2:
        //info:
        //  title: Swagger Sample App
        //host: example.com
        //basePath: /v1
        //schemes: ['http', 'https']
        //
        //V3:
        //servers:
        //- url: https://{subdomain}.site.com/{version}
        //  description: The main prod server
        //  variables:
        //    subdomain:
        //      default: production
        //    version:
        //      enum:
        //        - v1
        //        - v2
        //      default: v2
    }

    data class ServerVariable(
        val name: String,
        val default: String,
        val description: String,
        val enum: List<String>?
    )

    interface GenType
    interface BasePrimType : GenType
    data class PrimType(val type: String, val format: String?, val untyped: Any?) : BasePrimType

    interface BaseStringType : BasePrimType

    object PasswordType : BaseStringType {
        override fun toString(): String = "String"
    }

    object Base64Type : BaseStringType {
        override fun toString(): String = "Base64Type"
    }

    object BinaryStringType : BaseStringType {
        override fun toString(): String = "String"
    }

    object StringType : BaseStringType {
        override fun toString(): String = "String"
    }

    interface IntegerType : BasePrimType

    object Int32Type : IntegerType {
        override fun toString(): String = "Int"
    }

    object Int64Type : IntegerType {
        override fun toString(): String = "Long"
    }

    object BoolType : BasePrimType {
        override fun toString(): String = "Bool"
    }

    object FloatType : BasePrimType {
        override fun toString(): String = "Float"
    }

    object DoubleType : BasePrimType {
        override fun toString(): String = "Double"
    }

    object DateType : BasePrimType {
        override fun toString(): String = "Date"
    }

    object DateTimeType : BasePrimType {
        override fun toString(): String = "DateTime"
    }

    data class RefType(val type: String) : GenType {
        override fun toString(): String = type.substringAfterLast('/')
    }

    data class ArrayType(val items: GenType) : GenType {
        override fun toString(): String = "List<$items>"
    }

    data class OptionalType(val type: GenType) : GenType {
        override fun toString(): String = "$type?"
    }

    data class ObjType(val fields: Map<String, GenType>) : GenType {
        override fun toString(): String = "Any/*Unsupported {$fields}*/"
    }

    data class Prop(val name: String, val type: GenType, val required: Boolean) {
        val rtype = if (required) type else OptionalType(type)
    }

    data class TypeDef(
        val name: String,
        val props: Map<String, Prop>
    ) {
        val propsList = props.values
    }

    class SecurityDefinition(
        val kname: String,
        val description: String,
        val type: String,
        val name: String,
        val inside: String
    )

    data class Contact(val name: String, val url: String, val email: String)
    data class License(val name: String, val url: String)

    data class Parameter(
        val name: String,
        val inside: String,
        val required: Boolean,
        val description: String,
        val default: Any?,
        val schema: GenType
    )

    data class Security(
        val name: String,
        val info: List<String>
    )

    data class PathMethodModel(
        val path: String,
        val method: String,
        val summary: String,
        val description: String,
        val tags: List<String>,
        val security: List<Security>,
        val operationId: String,
        val parameters: List<Parameter>,
        val responses: List<Response>
    )

    data class PathModel(
        val path: String,
        val methods: Map<String, PathMethodModel>
    ) {
        val methodsList = methods.values
    }

    data class SwaggerInfo(
        val title: String,
        val description: String,
        val termsOfService: String,
        val version: String,
        val contact: Contact,
        val license: License
    )

    data class Response(
        val code: String,
        val description: String,
        val schema: GenType?
    ) {
        val intCode = when (code) {
            "default" -> 200
            else -> code.toIntOrNull() ?: -1
        }
    }

    companion object {
        object Versions {
            val V2 = SemVer("2.0")
            val V3 = SemVer("3.0.0")
            val V3_0_1 = SemVer("3.0.1")

            val MIN = V2
            val MAX = V3_0_1
        }

        // https://swagger.io/specification/#data-types
        fun parseDefinitionElement(def: Any?): GenType {
            return Dynamic {
                val ref = def["\$ref"]
                if (ref != null) {
                    RefType(ref.str)
                } else {
                    val type = def["type"]
                    val format = def["format"]
                    when (type) {
                        // Primitive
                        "integer" -> when (format.str) {
                            "int32", "null", "" -> Int32Type
                            "int64" -> Int64Type
                            else -> error("Invalid integer type $format")
                        }
                        "number" -> when (format.str) {
                            "float" -> FloatType
                            "double", "null", "" -> DoubleType
                            else -> error("Invalid number type $format")
                        }
                        "string" -> when (format.str) {
                            "string", "null", "" -> StringType
                            "byte" -> Base64Type // Base64
                            "binary" -> BinaryStringType // ISO-8859-1
                            "date" -> DateType
                            "date-time" -> DateTimeType
                            "password" -> PasswordType
                            else -> error("Invalid string type $format")
                        }
                        "boolean" -> BoolType
                        // Composed Types
                        "array" -> {
                            val items = def["items"]
                            ArrayType(parseDefinitionElement(items))
                        }
                        "object" -> {
                            val props = def["properties"]
                            val entries =
                                props.strEntries.map { it.first to parseDefinitionElement(it.second) }.toMap()
                            ObjType(entries)
                        }
                        "null" -> error("null? : $def")
                        else -> {
                            //error("Other prim $type, $def")
                            PrimType(type.str, format?.str, def)
                        }
                    }
                }
            }
        }

        fun parseDefinition(name: String, def: Any?): TypeDef {
            //println("Definition $name:")
            return Dynamic {
                //println(" - " + def["required"].list)
                val type = def["type"].str
                if (type != "object") error("Only supported 'object' definitions but found '$type'")
                val required = def["required"].strList.toSet()
                val props = def["properties"].let {
                    it.strEntries.map { (key, element) ->
                        val pdef = parseDefinitionElement(element)
                        key to Prop(key.str, pdef, key in required)
                    }.toMap()
                }

                TypeDef(name, props)
            }
        }

        fun parseParameter(def: Any?): Parameter {
            return Dynamic {
                Parameter(
                    name = def["name"].str,
                    inside = def["in"].str,
                    required = def["required"]?.bool ?: false,
                    description = def["description"].str,
                    default = def["default"],
                    schema = parseDefinitionElement(def["schema"] ?: def)
                )
            }
        }

        fun parseMethodPath(path: String, method: String, def: Any?): PathMethodModel {
            return Dynamic {
                PathMethodModel(
                    path = path,
                    method = method,
                    summary = def["summary"].str,
                    description = def["description"].str,
                    tags = def["tags"].strList,
                    security = def["security"].list.map {
                        val name = it.strKeys.first()
                        val info = it[name]
                        Security(name, info.strList)
                    },
                    operationId = def["tags"].str,
                    parameters = def["parameters"].list.map { parseParameter(it) },
                    responses = def["responses"].let {
                        it.strEntries.map { (code, rdef) ->
                            Response(code, rdef["description"].str, rdef["schema"]?.let { parseDefinitionElement(it) })
                        }
                    }
                )
            }
        }

        fun parsePath(path: String, def: Any?): PathModel {
            return Dynamic {
                PathModel(path, def.strEntries.map { (method, methodDef) ->
                    method to parseMethodPath(path, method, methodDef)
                }.toMap())
            }
        }

        fun parse(model: Any?, filename: String = "unknown.json"): SwaggerModel {
            return Dynamic {
                val version = model["swagger"] ?: model["openapi"]
                val semVer = SemVer(version.toString())

                if (semVer !in (Versions.MIN..Versions.MAX)) throw IllegalArgumentException("Not a swagger/openapi: '2.0' or '3.0.0' model")

                val info = model["info"].let {
                    SwaggerInfo(
                        title = it["title"].str,
                        description = it["description"].str,
                        termsOfService = it["termsOfService"].str,
                        contact = it["contact"].let { Contact(it["name"].str, it["url"].str, it["email"].str) },
                        license = it["license"].let { License(it["name"].str, it["url"].str) },
                        version = it["version"].str
                    )
                }
                val servers = arrayListOf<Server>()
                if (semVer < Versions.V3) {
                    val host = model["host"]?.str ?: "127.0.0.1"
                    val basePath = model["basePath"]?.str ?: "/"
                    val schemes = model["schemes"].strList
                    servers += Server(url = "{scheme}://$host$basePath", description = info.description, variables = mapOf(
                        "scheme" to ServerVariable("scheme", schemes.firstOrNull() ?: "https", "", schemes)
                    ))
                } else {
                    for (userver in model["servers"].list) {
                        servers += Server(url = userver["url"].str, description = userver["description"]?.str ?: "API", variables = userver["variables"].map.map { (uname, uvar) ->
                            val name = uname.str
                            name to ServerVariable(
                                name,
                                uvar["default"].str,
                                uvar["description"].str,
                                uvar["enum"]?.strList
                            )
                        }.toMap())
                    }
                }
                val produces = model["produces"].list.map { it.str }
                val consumes = model["consumes"].list.map { it.str }
                val securityDefinitions = model["securityDefinitions"].let {
                    it.strEntries.map { (kname, obj) ->
                        kname to SecurityDefinition(
                            kname = kname.str,
                            description = obj["description"].str,
                            type = obj["type"].str,
                            name = obj["name"].str,
                            inside = obj["in"].str
                        )
                    }.toMap()
                }
                val paths = model["paths"].let {
                    it.strEntries.map { (key, obj) ->
                        key to parsePath(key, obj)
                    }.toMap()
                }
                val definitions = model["definitions"].let {
                    it.strEntries.map { (key, obj) ->
                        key to parseDefinition(key, obj)
                    }.toMap()
                }
                SwaggerModel(
                    filename = filename,
                    info = info,
                    servers = servers,
                    produces = produces,
                    consumes = consumes,
                    securityDefinitions = securityDefinitions,
                    paths = paths,
                    definitions = definitions
                )
            }
        }
    }
}
