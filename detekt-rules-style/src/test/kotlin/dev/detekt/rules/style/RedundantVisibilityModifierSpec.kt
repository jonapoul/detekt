package dev.detekt.rules.style

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.test.FakeLanguageVersionSettings
import dev.detekt.test.assertj.assertThat
import dev.detekt.test.junit.KotlinCoreEnvironmentTest
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class RedundantVisibilityModifierSpec(private val env: KotlinEnvironmentContainer) {
    val subject = RedundantVisibilityModifier(Config.empty)

    @Test
    fun `does not report overridden function of abstract class with public modifier`() {
        val code = """
            abstract class A {
                abstract protected fun f()
            }
            
            class Test : A() {
                override public fun f() {}
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `does not report overridden function of abstract class without public modifier`() {
        val code = """
            abstract class A {
                abstract protected fun f()
            }
            
            class Test : A() {
                override fun f() {}
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `does not report overridden function of interface`() {
        val code = """
            interface A {
                fun f()
            }
            
            class Test : A {
                override public fun f() {}
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `reports public function in class`() {
        val code = """
            class Test {
                public fun f() {}
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `does not report function in class without modifier`() {
        val code = """
            class Test {
                fun f() {}
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `reports public class`() {
        val code = """
            public class Test {
                fun f() {}
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `reports interface with public modifier`() {
        val code = """
            public interface Test {
                public fun f()
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).hasSize(2)
    }

    @Test
    fun `reports field with public modifier`() {
        val code = """
            class Test {
                public val str : String = "test"
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `does not report field without public modifier`() {
        val code = """
            class Test {
                val str : String = "test"
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `does not report overridden field without public modifier`() {
        val code = """
            abstract class A {
                abstract val test: String
            }
            
            class B : A() {
                override val test: String = "valid"
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `does not report overridden field with public modifier`() {
        val code = """
            abstract class A {
                abstract val test: String
            }
            
            class B : A() {
                override public val test: String = "valid"
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `reports internal modifier on nested class in private object`() {
        val code = """
            private object A {
                internal class InternalClass
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `reports internal modifier on property in private object`() {
        val code = """
            private object A {
                internal val x: String = "test"
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `reports internal modifier on member of local class`() {
        val code = """
            fun foo() {
                class Local {
                    internal fun bar() {}
                }
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `does not report internal modifier on member of public class`() {
        val code = """
            class A {
                internal fun bar() {}
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `reports public modifier on top-level function`() {
        val code = """
            public fun topLevel() {}
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `reports public modifier on top-level property`() {
        val code = """
            public val topLevel: String = "test"
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `reports internal modifier on function declaration in private object`() {
        val code = """
            private object A {
                internal fun internalFunction() {}
            }
        """.trimIndent()
        assertThat(subject.lintWithContext(env, code)).hasSize(1)
    }

    @Nested
    inner class `Explicit API mode` {
        val code = $$"""
            public val a = 123

            public fun b() = Unit 

            public class C {
                public var d: Int = 123
                    public get() = field
                    public set(value) { field = value }

                public fun e(): String = "abc"

                public companion object F {
                    public fun g(): Int = 123
                }
            }

            public object H

            public interface H { 
                public fun i()
                public var j: Int
                public val k: Float
                
               public companion object {
                   public const val L: Int = 123
               }
            }
            
        """.trimIndent()

        private val strict = FakeLanguageVersionSettings(ExplicitApiMode.STRICT)
        private val warning = FakeLanguageVersionSettings(ExplicitApiMode.WARNING)
        private val disabled = FakeLanguageVersionSettings(ExplicitApiMode.DISABLED)

        @Test
        fun `does not report public if strict explicit API mode`() {
            assertThat(subject.lintWithContext(env, code, languageVersionSettings = strict)).isEmpty()
        }

        @Test
        fun `does not report public if warning explicit API mode`() {
            assertThat(subject.lintWithContext(env, code, languageVersionSettings = warning)).isEmpty()
        }

        @Test
        fun `reports public if explicit API is disabled`() {
            assertThat(subject.lintWithContext(env, code, languageVersionSettings = disabled))
                .containsFindingOnPublicWithName("a")
                .containsFindingOnPublicWithName("B")
                .containsFindingOnPublicWithName("c")
                .containsFindingOnPublicWithName("d")
                .containsFindingOnPublicWithName("E")
                .containsFindingOnPublicWithName("f")
                .containsFindingOnPublicWithName("G")
                .containsFindingOnPublicWithName("Companion")
                .containsFindingOnPublicWithName("H")
                .containsFindingOnPublicWithName("i")
                .containsFindingOnPublicWithName("j")
                .containsFindingOnPublicWithName("k")
                .containsFindingOnPublicWithName("L")
                .containsFindingOnPublicWithName("m")
            subject.lintWithContext(env, code, languageVersionSettings = disabled)
                .assertReportsOn("a", "B", "c", "d", "E", "f", "G", "Companion", "H", "i", "j", "k", "L", "m")
        }
    }

    private fun List<Finding>.assertReportsOn(vararg names: String) {
        assertThat(this).hasSize(names.size)
        for (name in names) {
            val pattern = """\b${Regex.escape(name)}\b""".toRegex()
            assertThat(filter { pattern.containsMatchIn(it.message) })
                .describedAs("findings mentioning '$name'")
                .hasSize(1)
        }
    }
}
