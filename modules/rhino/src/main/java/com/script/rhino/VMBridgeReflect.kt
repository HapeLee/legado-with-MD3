package com.script.rhino

import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory

object VMBridgeReflect {

    val contextLocal: ThreadLocal<Any> by lazy {
        @Suppress("UNCHECKED_CAST")
        try {
            // Rhino 1.9.1+ uses threadContextLocal directly in Context class
            val field = Context::class.java.getDeclaredField("threadContextLocal")
            field.isAccessible = true
            field.get(null) as ThreadLocal<Any>
        } catch (e: Exception) {
            try {
                // Fallback for some versions or custom builds
                val factory = ContextFactory.getGlobal()
                val field = ContextFactory::class.java.getDeclaredField("localContext")
                field.isAccessible = true
                field.get(factory) as ThreadLocal<Any>
            } catch (e2: Exception) {
                // Older versions used threadContextHelper (Object) which held a ThreadLocal
                val field = Context::class.java.getDeclaredField("threadContextHelper")
                field.isAccessible = true
                val helper = field.get(null)
                    ?: throw NullPointerException("threadContextHelper is null")
                val contextLocalField = helper.javaClass.getDeclaredField("contextLocal")
                contextLocalField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                contextLocalField.get(helper) as ThreadLocal<Any>
            }
        }
    }

}
